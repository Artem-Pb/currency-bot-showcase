package com.polybezev.currencybot.model;

import lombok.Data;

/**
 * In-memory FSM state for a single Telegram user, stored in {@code UserStateService}.
 * <p>
 * Holds the active conversation step, current UI mode, and any intermediate values
 * accumulated across multi-step flows (converter FSM, admin grant FSM).
 * <p>
 * Initialised with {@link ConversationState#IDLE} and {@link UserMode#MAIN} so that
 * a newly created entry behaves correctly without explicit setup by the caller.
 */
@Data
public class UserConversationData {

    /** Current step in the active FSM flow. IDLE means no flow is in progress. */
    private ConversationState state;

    /** Current UI section the user is in (main menu, personal account, admin panel). */
    private UserMode mode;

    /**
     * The mode the user was in before entering {@link UserMode#SIGNALS}.
     * Used to restore the correct keyboard when the user presses back.
     * {@code null} when not relevant.
     */
    private UserMode previousMode;

    // ==================== Converter FSM ====================

    /** Amount entered by the user in the AWAIT_AMOUNT step. {@code null} until set. */
    private Double amount;

    /** Source currency code entered in the AWAIT_FROM step. {@code null} until set. */
    private String fromCurrency;

    // ==================== Edit-in-place ====================

    /**
     * Telegram message ID of the last bot message sent during an FSM flow.
     * Used by the edit-in-place mechanism to update the same message instead of
     * sending new ones. Cleared when the user exits the converter FSM.
     */
    private Integer lastBotMessageId;

    // ==================== Admin grant FSM ====================

    /**
     * Chat ID of the user targeted in the admin grant flow.
     * Set after the admin enters a valid chatId in {@link ConversationState#ADMIN_AWAIT_GRANT_ID}.
     * {@code null} when no grant flow is active.
     */
    private Long adminGrantTargetId;

    // ==================== Trade bot setup FSM ====================

    /**
     * Exchange selected by the user in the first step of the trade bot setup flow.
     * Held here temporarily until the secret key is confirmed and credentials are persisted.
     * {@code null} when no setup flow is active.
     */
    private com.polybezev.currencybot.model.SupportedExchange tradeSetupExchange;

    /**
     * API key entered by the user in {@link ConversationState#TRADE_AWAIT_API_KEY}.
     * Held in memory only for the duration of the setup flow; cleared immediately after
     * credentials are encrypted and written to the database — never persisted in plaintext.
     * {@code null} when no setup flow is active.
     */
    private String tradeSetupApiKey;

    public UserConversationData() {
        this.state = ConversationState.IDLE;
        this.mode = UserMode.MAIN;
    }
}
