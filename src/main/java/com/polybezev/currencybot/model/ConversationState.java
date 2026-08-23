package com.polybezev.currencybot.model;

public enum ConversationState {

    /** No active flow — the user is browsing via reply-keyboard buttons. */
    IDLE,

    // ==================== Converter FSM ====================

    /** Waiting for the user to enter the numeric amount to convert. */
    AWAIT_AMOUNT,

    /** Waiting for the user to select or type the source currency (FROM). */
    AWAIT_FROM,

    /** Waiting for the user to select or type the target currency (TO). */
    AWAIT_TO,

    // ==================== Admin grant FSM ====================

    /** Admin has started the grant flow and is waiting for the target user's chatId. */
    ADMIN_AWAIT_GRANT_ID,

    /** Admin has entered a valid chatId and is waiting to select the tier to grant. */
    ADMIN_AWAIT_GRANT_TIER,

    // ==================== Trade bot setup FSM ====================

    /**
     * User has selected an exchange and is waiting to enter the API key.
     * The chosen {@link SupportedExchange} is held in {@link UserConversationData#getTradeSetupExchange()}.
     */
    TRADE_AWAIT_API_KEY,

    /**
     * API key has been stored temporarily and the bot is waiting for the secret key.
     * The partial key is held in {@link UserConversationData#getTradeSetupApiKey()}.
     */
    TRADE_AWAIT_SECRET,

    /**
     * User pressed "Портфель (ИИ)" and the bot is waiting for a free-text question about
     * their portfolio/trade history, to be forwarded to the AI agent along with their chatId.
     */
    AWAIT_PORTFOLIO_QUESTION,

    // ==================== Watchlist onboarding ====================

    /**
     * Shown once on {@code /start} while the user's watchlist is still empty — bot is waiting
     * for the "Готово" button on the favourites selection keyboard. Distinguishes the
     * onboarding flow from the same keyboard reopened later via "⭐ Настроить избранное"
     * (state stays {@code IDLE} in that case), so {@code CurrencyBot} knows whether "Готово"
     * should return to the main menu or just refresh the "Курсы" view.
     */
    AWAIT_WATCHLIST_SELECTION;

    /**
     * Returns {@code true} if this state belongs to the currency converter FSM flow.
     * <p>
     * Used by the bot router and the edit-in-place mechanism to distinguish converter steps
     * from admin FSM states — so each group stays isolated without naming every state explicitly.
     * Add new converter states here when extending the converter flow.
     */
    public boolean isConverterState() {
        return this == AWAIT_AMOUNT || this == AWAIT_FROM || this == AWAIT_TO;
    }

    /**
     * Returns {@code true} if this state belongs to the trade bot setup FSM flow, including
     * the "Портфель (ИИ)" question step — both are routed to {@code TradeHandler}.
     * <p>
     * Used by the bot router to redirect text input to {@code TradeHandler} during key setup
     * or while awaiting a portfolio question.
     */
    public boolean isTradeBotState() {
        return this == TRADE_AWAIT_API_KEY || this == TRADE_AWAIT_SECRET || this == AWAIT_PORTFOLIO_QUESTION;
    }
}
