package com.polybezev.currencybot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Cached translation + general AI analysis for a single news item, keyed by its RSS guid
 * (falls back to the article link when a feed omits {@code <guid>}).
 * <p>
 * One row per distinct news item regardless of how many subscribers view it — that is the
 * whole point of this cache: {@code AiAnalysisService} computes the translation and general
 * summary once per news item, not once per (user, request) pair, since that half of the work
 * does not depend on who is asking. The personalised "impact on your positions/watchlist"
 * line is computed separately per user and is never stored here.
 * <p>
 * No active eviction: only the current top 5 headlines per feed are ever fetched, and the
 * feeds themselves only carry a few days of articles, so stale rows accumulate slowly and are
 * simply never read again. {@code createdAt} is kept for observability and in case a cleanup
 * job becomes worth adding later — not built now, since it is not yet a real problem.
 */
@Entity
@Table(name = "news_analysis_cache")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(of = "guid")
public class NewsAnalysisCache {

    /** Surrogate primary key, auto-incremented by the database. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RSS guid, or the article link if the feed omits {@code <guid>} — unique per news item. */
    @Column(unique = true, nullable = false, length = 512)
    private String guid;

    /** Russian translation of the original headline/title. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String translation;

    /** Short AI-generated explanation of what the news means, in Russian, shared across all users. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    /** When this entry was computed — kept for observability, not used for eviction yet. */
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
