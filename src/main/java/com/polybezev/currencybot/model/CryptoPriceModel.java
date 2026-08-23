package com.polybezev.currencybot.model;

import lombok.Data;

/**
 * Holds the current price and 24-hour change data for a single cryptocurrency,
 * as returned by the CoinGecko API via {@code CryptoService}.
 * <p>
 * {@code symbol} is not populated by the API response — it is set by the caller
 * (e.g. {@code "BTC"}) after the model is returned, since CoinGecko identifies
 * coins by ID ({@code "bitcoin"}) rather than ticker symbol.
 */
@Data
public class CryptoPriceModel {

    /** Ticker symbol displayed to the user (e.g. "BTC", "ETH"). Set by the caller, not by the API. */
    private String symbol;

    /** Current price in Russian rubles. */
    private double priceRub;

    /** Current price in US dollars. */
    private double priceUsd;

    /** Price change over the last 24 hours as a percentage (positive = up, negative = down). */
    private double change24h;
}
