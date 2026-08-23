package com.polybezev.currencybot.model;

/**
 * Represents the active UI section a user is currently viewing.
 * Stored in {@link UserConversationData} and used by the bot router to direct
 * reply-keyboard button presses to the correct handler.
 */
public enum UserMode {

    /** Default mode — main reply keyboard with rates, converter, signals, and subscription. */
    MAIN,

    /** Personal account mode — LK reply keyboard with balance, news, and AI digest. */
    LK,

    /** Admin panel mode — admin reply keyboard visible only to the administrator. */
    ADMIN,

    /** TA signal mode — coin selection reply keyboard with back button. */
    SIGNALS,

    /** Trade bot mode — trading setup and history screen (TIER 3). */
    TRADE_BOT
}
