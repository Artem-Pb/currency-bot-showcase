package com.polybezev.currencybot.entity;

import com.polybezev.currencybot.model.TradeSide;
import com.polybezev.currencybot.model.TradeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records a single trade order placed (or attempted) by the automated trading system on behalf of a user.
 * <p>
 * Each row represents one market-order execution attempt. A {@link TradeStatus#FILLED} row means
 * the exchange accepted and executed the order; {@link TradeStatus#FAILED} rows carry the
 * rejection reason in {@link #errorMessage} for debugging and user display.
 * <p>
 * Orders are append-only — existing rows are never modified after creation.
 * {@code user} is excluded from {@code @ToString} to prevent lazy-init exceptions.
 */
@Entity
@Table(name = "trade_orders")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "user")
public class TradeOrder {

    /** Surrogate primary key, auto-incremented by the database. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user on whose behalf this order was placed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Coin symbol that was traded (e.g. {@code "BTC"}, {@code "ETH"}). */
    @Column(nullable = false)
    private String coin;

    /** Direction of the order — BUY (acquire coin) or SELL (exit position). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeSide side;

    /**
     * Amount of the base asset transacted (e.g. {@code 0.00123456} BTC).
     * {@code null} when the order failed before a quantity could be determined.
     */
    @Column(precision = 20, scale = 8)
    private BigDecimal quantity;

    /**
     * Execution price of the market order in USDT.
     * {@code null} when the order failed or the exchange did not return a fill price.
     */
    @Column(precision = 20, scale = 8)
    private BigDecimal price;

    /**
     * Order ID assigned by the exchange upon successful placement.
     * {@code null} for failed orders where the exchange rejected the request before assigning an ID.
     */
    @Column
    private String exchangeOrderId;

    /** Whether the order was executed ({@link TradeStatus#FILLED}) or rejected ({@link TradeStatus#FAILED}). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeStatus status;

    /**
     * Human-readable error message from the exchange or internal validation, populated only
     * when {@link #status} is {@link TradeStatus#FAILED}.
     */
    @Column(length = 500)
    private String errorMessage;

    /** Timestamp when this order record was created (before placement attempt). */
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
