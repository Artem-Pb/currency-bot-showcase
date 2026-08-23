package com.polybezev.currencybot.model;

/**
 * Subscription tiers available in the bot, ordered from lowest (FREE) to highest (TIER_3).
 * <p>
 * The enum ordinal encodes the hierarchy: each higher tier grants access to all lower-tier
 * features. Use {@link #hasAccess(Tier)} instead of raw {@code ordinal()} comparisons
 * so that the access logic stays in one place and callers are not coupled to enum ordering.
 */
public enum Tier {

    /** Free tier — no subscription required. Grants access to rates, converter, and BTC price. */
    FREE(0, "Бесплатно"),

    /** Paid tier 1 — crypto news on demand and the morning AI digest. */
    TIER_1(200, "TIER 1 — Новости и AI"),

    /** Paid tier 2 — TA trading signals (RSI/MACD) with AI explanation. Includes TIER 1. */
    TIER_2(600, "TIER 2 — Торговые сигналы"),

    /** Paid tier 3 — automated trading through the user's own exchange account. Includes TIER 1 and TIER 2. */
    TIER_3(1500, "TIER 3 — Автоторговля");

    /** Price in Telegram Stars for a 30-day subscription. Zero for FREE. */
    public final int starsPrice;

    /** Human-readable label shown in tier cards, invoices, and the admin panel. */
    public final String label;

    Tier(int starsPrice, String label) {
        this.starsPrice = starsPrice;
        this.label = label;
    }

    /**
     * Returns {@code true} if this tier grants access to the features of {@code required}.
     * <p>
     * The access model is cumulative: a higher tier always includes the features of all lower tiers.
     * Use this method instead of comparing {@code ordinal()} values directly.
     *
     * @param required the minimum tier needed to use a feature
     * @return {@code true} if this tier is equal to or above {@code required}
     */
    public boolean hasAccess(Tier required) {
        return this.ordinal() >= required.ordinal();
    }
}
