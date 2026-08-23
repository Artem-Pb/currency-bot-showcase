package com.polybezev.currencybot.model;

/**
 * Direction of a trade order placed by the automated trading system.
 * Stored as a string in the {@code trade_orders} table via {@code @Enumerated(EnumType.STRING)}.
 */
public enum TradeSide {

    /** Buy the base asset (e.g. spend USDT to acquire BTC). */
    BUY,

    /** Sell the base asset (e.g. convert BTC back to USDT). */
    SELL
}
