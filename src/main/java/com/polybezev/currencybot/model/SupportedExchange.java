package com.polybezev.currencybot.model;

/**
 * Cryptocurrency exchanges supported by the automated trading feature (TIER 3).
 * <p>
 * Each constant carries the display name shown in bot messages and the Binance-style
 * symbol suffix used when building trading pairs (e.g. {@code "USDT"}).
 * Additional exchanges can be added here; {@code ExchangeService} maps each constant
 * to the corresponding XChange {@code Exchange} implementation.
 */
public enum SupportedExchange {

    /** Binance — the default and most liquid exchange supported. */
    BINANCE("Binance"),

    /** Bybit — alternative exchange for users who prefer it over Binance. */
    BYBIT("Bybit");

    /** Human-readable name shown in keyboards and status messages. */
    public final String displayName;

    SupportedExchange(String displayName) {
        this.displayName = displayName;
    }
}
