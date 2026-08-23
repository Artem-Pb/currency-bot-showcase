package com.polybezev.currencybot.model;

/**
 * Lifecycle status of a {@code TradeOrder}.
 * Stored as a string in the {@code trade_orders} table via {@code @Enumerated(EnumType.STRING)}.
 */
public enum TradeStatus {

    /** Order was accepted and fully executed by the exchange. */
    FILLED,

    /** Order could not be placed or was rejected — see {@code TradeOrder#errorMessage}. */
    FAILED
}
