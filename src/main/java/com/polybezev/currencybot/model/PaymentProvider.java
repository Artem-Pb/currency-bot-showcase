package com.polybezev.currencybot.model;

/**
 * Identifies the payment system used to purchase a subscription.
 * Stored as a string in the {@code user_subscriptions} table via {@code @Enumerated(EnumType.STRING)}.
 */
public enum PaymentProvider {

    /** Telegram's built-in virtual currency. No bank account required; 30 % platform fee. */
    STARS,

    /** YooKassa (ЮKassa) — the primary Russian payment gateway (~3.5 % fee). */
    YOOKASSA,

    /** Stripe — for international users outside Russia (~2.9 % + 30¢ fee). */
    STRIPE,

    /** Granted manually by the administrator via the admin panel (no payment). */
    MANUAL
}
