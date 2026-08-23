package com.polybezev.currencybot.entity;

import com.polybezev.currencybot.model.SupportedExchange;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores a user's exchange API credentials for automated trading (TIER 3).
 * <p>
 * API key and secret are never persisted in plaintext — both fields hold
 * AES-256-GCM ciphertext produced by {@code EncryptionService}:
 * {@code Base64(IV ‖ ciphertext)} stored in a single column per field.
 * The master encryption key lives only in the {@code ENCRYPTION_KEY} environment
 * variable and is never written to the database.
 * <p>
 * One user may have at most one active set of credentials ({@code unique = true}
 * on {@code user_id}). Saving new credentials replaces the existing row via the
 * service layer rather than accumulating historical rows.
 * <p>
 * {@code @Data} is avoided on JPA entities — see {@link User} for rationale.
 * {@code user} is excluded from {@code @ToString} to prevent lazy-init exceptions
 * when the proxy is accessed outside a transaction.
 */
@Entity
@Table(name = "user_exchange_credentials")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "user")
public class UserExchangeCredentials {

    /** Surrogate primary key, auto-incremented by the database. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The owner of these credentials. One-to-one: each user may connect one exchange account.
     * Loaded lazily to avoid joins on every query.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Exchange these credentials belong to (BINANCE, BYBIT). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportedExchange exchange;

    /**
     * AES-256-GCM encrypted API key: {@code Base64(12-byte IV ‖ ciphertext)}.
     * Decrypted at runtime by {@code EncryptionService} before use; never logged.
     */
    @Column(nullable = false, length = 1000)
    private String encryptedApiKey;

    /**
     * AES-256-GCM encrypted API secret: {@code Base64(12-byte IV ‖ ciphertext)}.
     * Decrypted at runtime by {@code EncryptionService} before use; never logged.
     */
    @Column(nullable = false, length = 1000)
    private String encryptedSecret;

    /**
     * Maximum percentage of the USDT balance to spend on a single trade (1–100).
     * Default is 10 % — conservative enough for most users to start with.
     */
    @Column(nullable = false)
    private int maxTradePercent = 10;

    /**
     * Whether the automated trading scheduler is currently active for this user.
     * Set to {@code false} by default; the user explicitly enables trading from the bot UI.
     */
    @Column(nullable = false)
    private boolean tradingEnabled = false;

    /** Timestamp when these credentials were first saved. */
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
