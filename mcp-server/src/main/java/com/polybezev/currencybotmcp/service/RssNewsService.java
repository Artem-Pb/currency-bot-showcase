package com.polybezev.currencybotmcp.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MCP tool that aggregates crypto news from public RSS feeds (CoinDesk, CoinTelegraph, Decrypt)
 * — a free, key-less replacement for the paid CryptoPanic API. Called directly by the LLM
 * (the agent decides the {@code query}/{@code hours} arguments), not by currency-bot's own code.
 */
@Service
@Slf4j
public class RssNewsService {

    private static final List<String> FEED_URLS = List.of(
            "https://www.coindesk.com/arc/outboundfeeds/rss/",
            "https://cointelegraph.com/rss",
            "https://decrypt.co/feed"
    );

    private static final int DEFAULT_HOURS = 24;
    private static final int MAX_RESULTS = 8;

    /**
     * Searches recent entries across all configured RSS feeds, optionally filtered by a
     * case-insensitive substring match on the title.
     *
     * @param query keyword/coin name to filter titles by, or {@code null}/empty for no filter
     * @param hours how many hours back to look; {@code null} defaults to {@value #DEFAULT_HOURS}
     * @return up to {@value #MAX_RESULTS} bullet-point lines ({@code "• Title — Source"}), or a
     *         plain-language "nothing found" message if no entry matched
     */
    @Tool(description = "Ищет последние новости о криптовалюте из публичных RSS-лент " +
            "CoinDesk, CoinTelegraph и Decrypt. Используй перед объяснением торгового сигнала " +
            "(query = название монеты) или для утренней сводки (query пустой — общий крипто-фон).")
    public String searchNews(
            @ToolParam(description = "Название монеты или ключевое слово для фильтра по " +
                    "заголовку новости, например 'bitcoin'. Пустая строка или отсутствие " +
                    "значения — вернуть последние новости без фильтра по теме.",
                    required = false) String query,
            @ToolParam(description = "За сколько последних часов искать новости. По умолчанию 24.",
                    required = false) Integer hours
    ) {
        int window = hours != null ? hours : DEFAULT_HOURS;
        Instant cutoff = Instant.now().minus(Duration.ofHours(window));
        String needle = query == null ? "" : query.trim().toLowerCase();

        List<SyndEntry> matched = new ArrayList<>();
        for (String feedUrl : FEED_URLS) {
            matched.addAll(fetchMatching(feedUrl, cutoff, needle));
        }

        if (matched.isEmpty()) {
            return "Новостей по запросу за последние " + window + " ч. не найдено.";
        }

        matched.sort(Comparator.comparing(this::publishedInstant).reversed());

        StringBuilder sb = new StringBuilder();
        matched.stream().limit(MAX_RESULTS).forEach(entry -> sb
                .append("• ").append(entry.getTitle())
                .append(" — ").append(sourceName(entry))
                .append("\n"));

        return sb.toString();
    }

    private List<SyndEntry> fetchMatching(String feedUrl, Instant cutoff, String needle) {
        try {
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(URI.create(feedUrl).toURL()));
            List<SyndEntry> result = new ArrayList<>();
            for (SyndEntry entry : feed.getEntries()) {
                if (publishedInstant(entry).isBefore(cutoff)) continue;
                if (!needle.isEmpty() && !entry.getTitle().toLowerCase().contains(needle)) continue;
                result.add(entry);
            }
            return result;
        } catch (Exception e) {
            // Одна упавшая лента не должна валить весь ответ — агент получит то, что нашлось
            // в остальных, вместо ошибки инструмента.
            log.warn("RSS feed unavailable, skipping: {} ({})", feedUrl, e.getMessage());
            return List.of();
        }
    }

    private Instant publishedInstant(SyndEntry entry) {
        if (entry.getPublishedDate() != null) return entry.getPublishedDate().toInstant();
        if (entry.getUpdatedDate() != null) return entry.getUpdatedDate().toInstant();
        return Instant.EPOCH;
    }

    private String sourceName(SyndEntry entry) {
        String link = entry.getLink() != null ? entry.getLink() : "";
        if (link.contains("coindesk")) return "CoinDesk";
        if (link.contains("cointelegraph")) return "CoinTelegraph";
        if (link.contains("decrypt")) return "Decrypt";
        return link.isEmpty() ? "источник неизвестен" : link;
    }
}
