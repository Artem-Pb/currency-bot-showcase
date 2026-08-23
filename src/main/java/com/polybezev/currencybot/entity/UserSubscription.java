package com.polybezev.currencybot.entity;

import com.polybezev.currencybot.model.PaymentProvider;
import com.polybezev.currencybot.model.Tier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a single subscription purchase — one row per payment transaction.
 * <p>
 * A user may have multiple rows (renewals, upgrades); the active subscription is the one
 * where {@code active = true} and {@code expiresAt} is in the future.
 * <p>
 * {@code @Data} is intentionally avoided on JPA entities (see {@link User} for rationale).
 * This class has no natural business key, so {@code equals}/{@code hashCode} are left as
 * reference equality ({@code Object} defaults) — the safest choice for entities without
 * a stable unique field beyond the surrogate {@code id}.
 * <p>
 * {@code user} is excluded from {@code @ToString} to prevent {@link org.hibernate.LazyInitializationException}
 * when the proxy is accessed outside a transaction.
 */
@Entity
@Table(name = "user_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "user")
public class UserSubscription {

    /** Surrogate primary key, auto-incremented by the database. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who made this purchase. Loaded lazily to avoid joins on every query. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Subscription tier purchased (FREE, TIER_1, TIER_2, TIER_3). Stored as its name string. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tier tier;

    /**
     * Amount paid for this subscription in the currency of the payment provider.
     * Precision 10, scale 2 is explicit — without it Hibernate uses a platform-dependent default.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid;

    /** Timestamp when the subscription became active (payment confirmed). */
    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** Timestamp when the subscription expires. Access is denied once this passes. */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** Payment system used (TELEGRAM_STARS, YOOKASSA, STRIPE). Stored as its name string. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider paymentProvider;

    /**
     * Whether this subscription is currently active.
     * {@code false} after expiry, manual revocation, or if superseded by a newer subscription.
     */
    @Column(nullable = false)
    private boolean active;
}
