package com.polybezev.currencybot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Persistent representation of a Telegram user who has interacted with the bot.
 * <p>
 * {@code chatId} is the natural unique identifier (Telegram's own ID) and is used
 * as the basis for {@code equals}/{@code hashCode} — this is safe for use in JPA
 * collections regardless of whether the entity has been persisted yet.
 * <p>
 * {@code @Data} is intentionally avoided: it generates {@code equals}/{@code hashCode}
 * over all fields including the surrogate {@code id}, which breaks {@link java.util.HashSet}
 * semantics for transient entities (before {@code save()}, {@code id} is {@code null}).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(of = "chatId")
public class User {

    /** Surrogate primary key, auto-incremented by the database. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Telegram chat ID — unique, never null, stable for the lifetime of the user account. */
    @Column(unique = true, nullable = false)
    private Long chatId;

    /** Telegram {@code @username}, may be {@code null} if the user has not set one. */
    @Column(length = 64)
    private String username;

    /** Telegram first name as provided by the client; used in personalised messages. */
    @Column(nullable = false, length = 256)
    private String firstName;

    /** Timestamp of first interaction with the bot (registration moment). */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * When {@code true}, the bot silently ignores all messages from this user.
     * Set by an admin via the admin panel ban action.
     */
    @Column(nullable = false)
    private boolean banned = false;

    /**
     * Coin codes the user marked as favourites (watchlist) — the same 12 coins as
     * {@code TaSignalService#supportedCoins()} plus {@code USDT} (see
     * {@code MessageFormatter#WATCHLIST_OPTIONS}). Started out as CBR fiat codes in the first
     * draft; switched to crypto-only 2026-08-02 after live testing — this is what the
     * personalised "Курсы" view and news-impact matching (`CommandHandler.resolveNewsAssets`)
     * are actually meant to be matched against.
     * <p>
     * {@code EAGER} deliberately: read on every {@code /start} and every "Курсы" tap, and
     * {@code CommandHandler} is not transactional, so lazy loading would throw
     * {@link org.hibernate.LazyInitializationException} outside a transaction. The collection
     * is small (manually curated by the user), so eager loading is not a performance concern.
     * All mutation goes through {@code WatchlistService}, not directly on this entity.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_watchlist", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "code", length = 10)
    private Set<String> watchlist = new HashSet<>();
}
