package com.polybezev.currencybot.service;

import com.polybezev.currencybot.formatter.BotMessages;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Fetches the latest crypto news headlines from public RSS feeds (CoinDesk, CoinTelegraph,
 * Decrypt) — free, key-less, no rate limit.
 * <p>
 * Replaces the previous CryptoPanic integration (2026-08-02): CryptoPanic's free tier now
 * returns 403 even with the client correctly configured (auth_token requirement tightened),
 * and the paid Plus plan was judged too expensive for this project. Same three feeds and
 * fetch pattern as the MCP tool ({@code mcp-server/.../RssNewsService}) used by the AI agent
 * for per-coin news lookups during signal explanation — this class stays a separate,
 * duplicated implementation on purpose: it serves the bot's own deterministic paths (raw
 * TIER 1 headlines, market digest), which fetch news up front rather than leaving the choice
 * to the agent, for predictable behavior. See {@code ai/CODE_REFERENCE.md}.
 * <p>
 * Each headline is returned as {@code "• Title — Source"} so the user can see
 * both the story and where it comes from (e.g. CoinDesk, Decrypt, CoinTelegraph).
 * <p>
 * Results are <em>not</em> cached here — callers that need caching should apply
 * {@code @Cacheable} on their own side or accept fresh data each call.
 */
@Service
@Slf4j
public class NewsService {

    private static final List<String> FEED_URLS = List.of(
            "https://www.coindesk.com/arc/outboundfeeds/rss/",
            "https://cointelegraph.com/rss",
            "https://decrypt.co/feed"
    );

    private static final int MAX_HEADLINES = 5;

    /**
     * A single news headline in a form other services can use without depending on the ROME
     * {@code SyndEntry} type — used by phase 7's per-item translation/AI-analysis pipeline
     * ({@code CommandHandler.handleNews}, {@code AiAnalysisService.translateAndAnalyzeNews}).
     *
     * @param guid   RSS guid, or the article link if the feed omits {@code <guid>} — stable
     *               identity used as the AI-analysis cache key
     * @param title  original headline text (source language, typically English)
     * @param link   article URL
     * @param source display name of the feed (e.g. {@code "CoinDesk"})
     */
    public record NewsItem(String guid, String title, String link, String source) {}

    /**
     * Returns the top {@value #MAX_HEADLINES} most recent crypto news headlines across all
     * configured feeds, most recent first.
     * <p>
     * Format per line: {@code • Title — Source}<br>
     * Example: {@code • Bitcoin breaks $100k — CoinDesk}
     * <p>
     * A single unreachable feed does not fail the whole call — it is skipped and logged;
     * only if every feed fails or returns nothing does this return
     * {@link BotMessages#NEWS_UNAVAILABLE}.
     * <p>
     * Used by the AI digest and {@code MorningDigestScheduler}, which feed raw headlines into
     * a single AI prompt and don't need per-item structure. The "📰 Новости" button uses
     * {@link #getTopNewsItems} instead, for per-item translation and analysis.
     *
     * @return formatted headline list or unavailability notice; never {@code null}
     */
    public String getTopNews() {
        List<SyndEntry> entries = fetchTopEntries();

        if (entries.isEmpty()) {
            log.warn("All RSS feeds unavailable or empty");
            return BotMessages.NEWS_UNAVAILABLE;
        }

        StringBuilder sb = new StringBuilder();
        entries.stream().limit(MAX_HEADLINES).forEach(entry -> sb
                .append("• ").append(entry.getTitle())
                .append(" — ").append(sourceName(entry))
                .append("\n"));

        return sb.toString();
    }

    /**
     * Returns the same top {@value #MAX_HEADLINES} headlines as {@link #getTopNews}, but as
     * structured {@link NewsItem}s for callers that process each item individually.
     *
     * @return up to {@value #MAX_HEADLINES} news items, most recent first; empty if every feed
     *         is unavailable
     */
    public List<NewsItem> getTopNewsItems() {
        return fetchTopEntries().stream()
                .limit(MAX_HEADLINES)
                .map(entry -> new NewsItem(guidOf(entry), entry.getTitle(), entry.getLink(), sourceName(entry)))
                .toList();
    }

    private List<SyndEntry> fetchTopEntries() {
        List<SyndEntry> entries = new ArrayList<>();
        for (String feedUrl : FEED_URLS) {
            entries.addAll(fetchFeed(feedUrl));
        }
        entries.sort(Comparator.comparing(this::publishedDate).reversed());
        return entries;
    }

    private String guidOf(SyndEntry entry) {
        return entry.getUri() != null ? entry.getUri() : entry.getLink();
    }

    private List<SyndEntry> fetchFeed(String feedUrl) {
        try {
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(URI.create(feedUrl).toURL()));
            return feed.getEntries();
        } catch (IOException | FeedException e) {
            log.warn("RSS feed unavailable, skipping: {} ({})", feedUrl, e.getMessage());
            return List.of();
        }
    }

    private Date publishedDate(SyndEntry entry) {
        if (entry.getPublishedDate() != null) return entry.getPublishedDate();
        if (entry.getUpdatedDate() != null) return entry.getUpdatedDate();
        return new Date(0);
    }

    private String sourceName(SyndEntry entry) {
        String link = entry.getLink() != null ? entry.getLink() : "";
        if (link.contains("coindesk")) return "CoinDesk";
        if (link.contains("cointelegraph")) return "CoinTelegraph";
        if (link.contains("decrypt")) return "Decrypt";
        return link.isEmpty() ? "источник неизвестен" : link;
    }
}
