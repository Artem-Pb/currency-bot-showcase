package com.polybezev.currencybot.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.polybezev.currencybot.entity.NewsAnalysisCache;
import com.polybezev.currencybot.formatter.BotMessages;
import com.polybezev.currencybot.repository.NewsAnalysisCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Wraps a Timeweb Cloud AI Agent (OpenAI-compatible endpoint) for several use-cases:
 * <ol>
 *   <li>Morning digest — a 4-paragraph market summary generated from live price data and news.</li>
 *   <li>TA signal explanation — a 2–3 sentence plain-language commentary on an RSI/MACD reading.</li>
 *   <li>Portfolio Q&amp;A — free-text answers about a user's own trade history via MCP.</li>
 *   <li>News translation + analysis (phase 7) — see {@link #translateAndAnalyzeNews} and
 *       {@link #generateNewsImpact}.</li>
 * </ol>
 * All methods are fault-tolerant: an API failure returns a fallback value (digest text or
 * {@code null}) so the caller can still show partial results.
 * <p>
 * The underlying model is whatever is configured on the agent in the Timeweb control panel —
 * the {@code model} field sent in each request is ignored by their API. See
 * {@code ai/CODE_REFERENCE.md} for how to create and wire up the agent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisService {

    private final RestTemplate restTemplate;
    private final NewsAnalysisCacheRepository newsAnalysisCacheRepository;

    /**
     * Base URL of the agent as copied verbatim from the ПУ ("Скопировать OpenAI URL") —
     * already ends in {@code /v1}, e.g.
     * {@code https://agent.timeweb.cloud/api/v1/cloud-ai/agents/<access_id>/v1}.
     */
    @Value("${timeweb.agent.base-url}")
    private String agentBaseUrl;

    /** Bearer token from the agent's "Доступ по API" panel — required even for public agents. */
    @Value("${timeweb.agent.token}")
    private String agentToken;

    /**
     * Generates the daily morning market digest using live {@code marketData} and {@code news}.
     * <p>
     * On success the AI response is returned with the standard disclaimer appended.
     * On any API failure the method falls back to {@link BotMessages#DIGEST_FALLBACK},
     * which formats the raw data without AI commentary.
     *
     * @param marketData formatted price snapshot (BTC, USD, EUR, CNY)
     * @param news       latest crypto news headlines
     * @return formatted digest ready to send; never {@code null}
     */
    public String generateMorningDigest(String marketData, String news) {
        try {
            String prompt = buildDigestPrompt(marketData, news);
            return formatResponse(callAgent(prompt, 800));
        } catch (Exception e) {
            log.warn("AI unavailable, returning fallback digest: {}", e.getMessage());
            return formatFallback(marketData, news);
        }
    }

    /**
     * Generates a short 2–3 sentence plain-language explanation for a TA signal result.
     * <p>
     * Returns {@code null} on failure so callers can display the TA numbers without an explanation
     * rather than suppressing the whole signal message.
     *
     * @param r the signal result containing coin name, RSI, MACD values, and the signal verdict
     * @return italicised explanation text, or {@code null} if the API call failed
     */
    public String generateSignalExplanation(TaSignalService.SignalResult r) {
        try {
            String emaTrend = r.ema20() > r.ema50() ? "восходящий (EMA20 выше EMA50)" : "нисходящий (EMA20 ниже EMA50)";
            String prompt = String.format(
                    "Ты трейдинговый аналитик. Объясни простым языком (2-3 предложения) что значат эти показатели:\n" +
                    "Монета: %s\n" +
                    "Итог: %s (балл: %d/3)\n" +
                    "RSI(14): %.1f\n" +
                    "MACD: %.6f, сигнальная линия: %.6f\n" +
                    "EMA20: %.2f, EMA50: %.2f, тренд: %s\n\n" +
                    "Без заголовков, без списков. Только связный текст. " +
                    "Не давай конкретных советов купить/продать.",
                    r.coin(), r.signal(), r.score(), r.rsi(), r.macd(), r.macdSignal(),
                    r.ema20(), r.ema50(), emaTrend
            );

            return "_" + callAgent(prompt, 200).trim() + "_";
        } catch (Exception e) {
            log.warn("Signal explanation AI unavailable: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generates a free-text answer about a user's own portfolio/trade history.
     * <p>
     * Unlike {@link #generateMorningDigest} and {@link #generateSignalExplanation}, this call
     * is tied to a specific user: the {@code chatId} is embedded directly in the prompt text
     * so the agent can pass it as the {@code userId} argument when it calls the
     * {@code get_user_portfolio} MCP tool (see {@code ai/CODE_REFERENCE.md} for the system
     * prompt instruction that tells the agent to do this). There is no session/thread
     * mechanism between the bot and the agent — each call is still a stateless one-shot prompt.
     *
     * @param chatId   Telegram chat ID of the user asking — becomes the {@code userId} the
     *                 agent must forward to {@code get_user_portfolio}
     * @param question the user's free-text question
     * @return the assistant's reply text, or {@code null} if the API call failed
     */
    public String generatePortfolioAnswer(long chatId, String question) {
        try {
            String prompt = String.format(
                    "Твой Telegram userId (chatId): %d\n\n" +
                    "Вопрос пользователя о своём портфеле: %s\n\n" +
                    "Используй userId из этого сообщения как параметр userId при вызове " +
                    "get_user_portfolio. Отвечай по-русски, 2-4 предложения, без заголовков " +
                    "и списков. Не давай прямых инвестиционных советов.",
                    chatId, question
            );
            return callAgent(prompt, 400).trim();
        } catch (Exception e) {
            log.warn("Portfolio answer AI unavailable for chatId={}: {}", chatId, e.getMessage());
            return null;
        }
    }

    // ==================== ФАЗА 7: НОВОСТИ — ПЕРЕВОД + AI-РАЗБОР ====================

    /**
     * Result of translating and generally analysing a single news item — identical for every
     * user, since neither the translation nor the "what this means" explanation depends on who
     * is asking.
     *
     * @param translation Russian translation of the original title
     * @param summary     1–2 sentence Russian explanation of what the news means for the market
     */
    public record NewsAnalysis(String translation, String summary) {}

    /**
     * Translates {@code title} to Russian and generates a short general explanation of what it
     * means, or returns the result cached from a previous call for the same {@code guid}.
     * <p>
     * This is the shared half of phase 7's news pipeline (architecture "вариант 2" — see
     * {@code ai/CODE_REFERENCE.md}): computed once per news item regardless of how many
     * subscribers view it, persisted in {@link NewsAnalysisCache} so the result survives app
     * restarts. The personalised "impact on your positions" line is a separate, uncached call —
     * see {@link #generateNewsImpact}.
     * <p>
     * Not safe against a rare race (two users requesting the same brand-new item in the same
     * instant could both miss the cache and both attempt to save the same {@code guid}) — the
     * unique constraint would reject the second insert. Accepted as an MVP-scale limitation:
     * this bot has a handful of concurrent subscribers, not a scenario worth adding
     * insert-or-update retry logic for.
     *
     * @param guid   RSS guid (or link fallback) — cache key
     * @param title  original headline text (any source language, typically English)
     * @param source display name of the feed (e.g. {@code "CoinDesk"})
     * @return cached or freshly computed translation+summary, or {@code null} if the AI call
     *         failed and nothing was cached yet for this guid
     */
    public NewsAnalysis translateAndAnalyzeNews(String guid, String title, String source) {
        Optional<NewsAnalysisCache> cached = newsAnalysisCacheRepository.findByGuid(guid);
        if (cached.isPresent()) {
            return new NewsAnalysis(cached.get().getTranslation(), cached.get().getSummary());
        }
        try {
            String prompt = String.format(
                    "Новость (%s): \"%s\"\n\n" +
                    "Сделай ровно две вещи, каждую с новой строки, без ничего лишнего:\n" +
                    "ПЕРЕВОД: точный перевод заголовка на русский, одна строка\n" +
                    "РАЗБОР: 1-2 предложения простым языком, что эта новость значит для рынка крипты\n\n" +
                    "Без вступлений, без markdown, без конкретных советов купить/продать.",
                    source, title
            );
            NewsAnalysis analysis = parseNewsAnalysis(callAgent(prompt, 300), title);

            NewsAnalysisCache entry = new NewsAnalysisCache();
            entry.setGuid(guid);
            entry.setTranslation(analysis.translation());
            entry.setSummary(analysis.summary());
            entry.setCreatedAt(LocalDateTime.now());
            newsAnalysisCacheRepository.save(entry);

            return analysis;
        } catch (Exception e) {
            log.warn("News analysis AI unavailable for guid={}: {}", guid, e.getMessage());
            return null;
        }
    }

    /**
     * Generates a short, personalised line about whether/how a news item affects assets the
     * user actually cares about — the uncached half of phase 7's news pipeline. Reuses the
     * shared {@code generalSummary} from {@link #translateAndAnalyzeNews} as context so this
     * prompt stays small and cheap: it only has to reason about relevance, not re-explain the
     * news from scratch.
     *
     * @param generalSummary the shared summary already computed for this news item
     * @param assets         uppercase codes the user actually cares about right now — TIER 3
     *                       open exchange positions if connected, otherwise watchlist codes
     *                       (see {@code NewsService})
     * @return 1-sentence Russian relevance line, or {@code null} if the AI call failed or
     *         {@code assets} is empty (nothing to match against)
     */
    public String generateNewsImpact(String generalSummary, Set<String> assets) {
        if (assets.isEmpty()) return null;
        try {
            String prompt = String.format(
                    "Разбор новости: %s\n\n" +
                    "Активы пользователя: %s\n\n" +
                    "Одним предложением по-русски: затрагивает ли эта новость напрямую что-то " +
                    "из этого списка и как, или прямо скажи, что новость не по теме этих " +
                    "активов. Без вступлений, без конкретных советов купить/продать.",
                    generalSummary, String.join(", ", assets)
            );
            return callAgent(prompt, 150).trim();
        } catch (Exception e) {
            log.warn("News impact AI unavailable: {}", e.getMessage());
            return null;
        }
    }

    private NewsAnalysis parseNewsAnalysis(String response, String fallbackTitle) {
        String translation = extractField(response, "ПЕРЕВОД:");
        String summary = extractField(response, "РАЗБОР:");
        return new NewsAnalysis(
                translation != null ? translation : fallbackTitle,
                summary != null ? summary : response.trim()
        );
    }

    private String extractField(String response, String marker) {
        int start = response.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = response.indexOf('\n', start);
        String value = end < 0 ? response.substring(start) : response.substring(start, end);
        return value.trim();
    }

    /**
     * Calls the agent's OpenAI-compatible {@code /v1/chat/completions} endpoint with a single
     * user message and returns the assistant's reply text. Stateless by design (no
     * {@code parent_message_id}) — each call here is a one-shot prompt, not a multi-turn dialog.
     *
     * @param prompt    the full user-role message to send
     * @param maxTokens completion token cap for this call
     * @return the assistant's reply text
     */
    private String callAgent(String prompt, int maxTokens) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject body = new JsonObject();
        body.add("messages", messages);
        body.addProperty("max_completion_tokens", maxTokens);
        body.addProperty("stream", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(agentToken);

        String responseBody = restTemplate.postForObject(
                agentBaseUrl + "/chat/completions",
                new HttpEntity<>(body.toString(), headers),
                String.class
        );

        return JsonParser.parseString(responseBody).getAsJsonObject()
                .getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }

    private String buildDigestPrompt(String marketData, String news) {
        return """
        Ты — финансовый аналитик с 15-летним опытом. Каждое утро ты пишешь сводку \
        для частного инвестора: человека с портфелем, который следит за рынком, \
        умеет читать цифры, но не торгует профессионально. Он хочет понять, что \
        происходит — и что с этим делать.

        Данные на сегодня:

        РЫНОК:
        """ + marketData + """

        НОВОСТИ (последние 24 часа):
        """ + news + """

        Твоя задача — написать утреннюю сводку. Вот как это должно работать:

        СТРУКТУРА (строго):
        1. Главное за ночь — 2–3 предложения. Что изменилось, пока инвестор спал. \
           Только факты с цифрами.
        2. Почему так вышло — причины движений. Не пересказывай новости, а объясни \
           связь: что на что повлияло и почему рынок среагировал именно так.
        3. На что смотреть сегодня — 1–2 конкретных момента: событие, уровень, \
           публикация данных. Без гадания — только то, что реально важно.
        4. Одна мысль напоследок — короткий вывод. Не совет купить или продать, \
           а угол зрения: на что стоит обратить внимание при принятии решений.

        КАК ПИСАТЬ:
        — Говори как аналитик с коллегой, не как учебник со студентом.
        — Цифры и факты — основа. Без цифр нет смысла.
        — Причинно-следственные связи важнее перечисления событий.
        — Никаких клише: "рынки штормит", "инвесторы нервничают", \
          "волатильность высокая", "ситуация неоднозначная" — не пиши так.
        — Никакого канцелярита: "в рамках данного периода", "следует отметить", \
          "необходимо подчеркнуть" — выброси.
        — Если данных не хватает для вывода — скажи об этом честно, одним предложением.
        — Длина: 4 абзаца. Каждый абзац — 3–5 предложений. Не больше.

        ЗАПРЕЩЕНО:
        — Давать конкретные инвестиционные рекомендации ("купить X", "продать Y")
        — Добавлять дисклеймеры и оговорки в основной текст \
          (дисклеймер будет добавлен автоматически после)
        — Придумывать данные, которых нет во входных данных
        — Использовать заголовки и маркированные списки внутри сводки — \
          только связный текст
        """;
    }

    private String formatResponse(String aiResponse) {
        return aiResponse + "\n\n" + BotMessages.DIGEST_DISCLAIMER;
    }

    private String formatFallback(String marketData, String news) {
        return BotMessages.DIGEST_FALLBACK
                .replace("{marketData}", marketData)
                .replace("{news}", news);
    }
}
