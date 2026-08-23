package com.polybezev.currencybotmcp.service;

import com.polybezev.currencybotmcp.dto.PortfolioDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * MCP tool that reads a user's own trading portfolio from currency-bot's internal read-only
 * API. The only tool in this project that ties {@code mcp-server} to the bot's database —
 * deliberately via HTTP, not a shared Postgres connection, so the bot stays the single owner
 * of its own data (see {@code ai/CODE_REFERENCE.md}, "куда расти").
 * <p>
 * {@code userId} must be supplied by the agent, extracted from the chatId embedded in the
 * request prompt by {@code AiAnalysisService.generatePortfolioAnswer} on the bot side — this
 * tool has no way to determine the caller's identity on its own.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    private final RestTemplate restTemplate;

    @Value("${currencybot.internal.base-url}")
    private String botBaseUrl;

    /** Shared secret — must match {@code internal.mcp.token} in the bot's application.properties. */
    @Value("${currencybot.internal.token}")
    private String botToken;

    /**
     * Fetches the portfolio snapshot (exchange connection status, trading toggle, recent
     * trade history) for the given Telegram user from currency-bot's internal API.
     *
     * @param userId Telegram chatId of the user asking — must come from the request context,
     *               never guessed or defaulted by the agent
     * @return a Russian-language plain-text summary, or an error message if the bot's API
     *         is unreachable or the user has no data
     */
    @Tool(description = "Возвращает портфель конкретного пользователя: подключена ли биржа, " +
            "включена ли автоторговля, последние сделки. Обязательно передавай userId, " +
            "указанный в тексте запроса (Telegram chatId) — никогда не придумывай и не " +
            "используй значение по умолчанию.")
    public String getUserPortfolio(
            @ToolParam(description = "Telegram userId (chatId) пользователя, взятый из текста запроса")
            Long userId
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", botToken);

            ResponseEntity<PortfolioDto> response = restTemplate.exchange(
                    botBaseUrl + "/internal/portfolio/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    PortfolioDto.class
            );

            PortfolioDto dto = response.getBody();
            if (dto == null) {
                return "Данные портфеля недоступны.";
            }
            return format(dto);
        } catch (Exception e) {
            log.warn("Portfolio lookup failed for userId={}: {}", userId, e.getMessage());
            return "Не удалось получить портфель пользователя.";
        }
    }

    private String format(PortfolioDto dto) {
        StringBuilder sb = new StringBuilder();

        if (!dto.exchangeConnected()) {
            sb.append("Биржа не подключена.");
        } else {
            sb.append("Биржа: ").append(dto.exchangeName())
                    .append(", автоторговля: ").append(dto.tradingEnabled() ? "включена" : "на паузе")
                    .append(".");
        }

        if (dto.recentTrades() == null || dto.recentTrades().isEmpty()) {
            sb.append(" Сделок пока не было.");
            return sb.toString();
        }

        sb.append(" Последние сделки:\n");
        dto.recentTrades().forEach(t -> sb
                .append("• ").append(t.side()).append(" ").append(t.coin())
                .append(" · ").append(t.quantity() != null ? t.quantity() : "—")
                .append(" @ ").append(t.price() != null ? t.price() : "—")
                .append(" USDT · ").append(t.status())
                .append(" · ").append(t.createdAt())
                .append("\n"));

        return sb.toString();
    }
}
