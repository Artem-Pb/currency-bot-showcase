package com.polybezev.currencybotmcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * MCP tools for public, key-less market data — Crypto Fear &amp; Greed Index and live coin
 * prices. Both are stateless one-shot HTTP lookups with no user identity involved, unlike
 * {@code PortfolioService} which calls back into currency-bot's own database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataService {

    private final RestTemplate restTemplate;

    private static final String FNG_URL = "https://api.alternative.me/fng/?limit=1";
    private static final String COINGECKO_SEARCH_URL = "https://api.coingecko.com/api/v3/search";
    private static final String COINGECKO_PRICE_URL = "https://api.coingecko.com/api/v3/simple/price";

    /**
     * Fetches the current Crypto Fear &amp; Greed Index from alternative.me.
     *
     * @return a one-line Russian-language summary ("52/100 — нейтрально"), or an
     *         error message if the API is unavailable
     */
    @Tool(description = "Возвращает текущий индекс страха и жадности крипторынка (Crypto Fear " +
            "& Greed Index) — число от 0 до 100 и его интерпретацию. Используй для оценки " +
            "общего рыночного настроения при объяснении сигнала или в утренней сводке.")
    public String getFearGreedIndex() {
        try {
            JsonNode root = restTemplate.getForObject(FNG_URL, JsonNode.class);
            JsonNode entry = root.path("data").get(0);
            String value = entry.path("value").asText();
            String classification = translateClassification(entry.path("value_classification").asText());
            return value + "/100 — " + classification;
        } catch (Exception e) {
            log.warn("Fear & Greed Index unavailable: {}", e.getMessage());
            return "Индекс страха и жадности сейчас недоступен.";
        }
    }

    /**
     * Looks up the current USD price of an arbitrary coin by name or symbol via CoinGecko —
     * for coins outside the 12 hardcoded in {@code TaSignalService}.
     *
     * @param coin coin name or symbol, e.g. {@code "bitcoin"}, {@code "SHIB"}, {@code "pepe"}
     * @return a one-line "Name: $price" summary, or a "not found"/error message
     */
    @Tool(description = "Возвращает текущую цену в USD произвольной монеты по названию или " +
            "тикеру, включая монеты вне основного списка из 12 (BTC, ETH, SOL и т.д.). " +
            "Используй, когда пользователь или контекст запроса упоминает монету, для которой " +
            "нет встроенного сигнала.")
    public String getLivePrice(
            @ToolParam(description = "Название или тикер монеты, например 'bitcoin' или 'SHIB'")
            String coin
    ) {
        try {
            String searchUrl = UriComponentsBuilder.fromHttpUrl(COINGECKO_SEARCH_URL)
                    .queryParam("query", coin)
                    .toUriString();
            JsonNode searchResult = restTemplate.getForObject(searchUrl, JsonNode.class);
            JsonNode coins = searchResult.path("coins");
            if (!coins.isArray() || coins.isEmpty()) {
                return "Монета «" + coin + "» не найдена.";
            }

            JsonNode first = coins.get(0);
            String id = first.path("id").asText();
            String name = first.path("name").asText();

            String priceUrl = UriComponentsBuilder.fromHttpUrl(COINGECKO_PRICE_URL)
                    .queryParam("ids", id)
                    .queryParam("vs_currencies", "usd")
                    .toUriString();
            JsonNode priceResult = restTemplate.getForObject(priceUrl, JsonNode.class);
            JsonNode usd = priceResult.path(id).path("usd");
            if (usd.isMissingNode()) {
                return "Цена монеты «" + name + "» сейчас недоступна.";
            }

            return name + ": $" + usd.asText();
        } catch (Exception e) {
            log.warn("Live price lookup failed for '{}': {}", coin, e.getMessage());
            return "Не удалось получить цену монеты «" + coin + "».";
        }
    }

    private String translateClassification(String classification) {
        return switch (classification) {
            case "Extreme Fear"  -> "экстремальный страх";
            case "Fear"          -> "страх";
            case "Neutral"       -> "нейтрально";
            case "Greed"         -> "жадность";
            case "Extreme Greed" -> "экстремальная жадность";
            default              -> classification;
        };
    }
}
