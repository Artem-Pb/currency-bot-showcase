package com.polybezev.currencybot.util;

import com.polybezev.currencybot.model.ConversationState;
import com.polybezev.currencybot.model.UserConversationData;

/**
 * Aborts an in-progress free-text FSM step (converter, admin grant, TIER 3 exchange setup,
 * portfolio AI question) by clearing all temporary fields and resetting the state to
 * {@link ConversationState#IDLE}.
 * <p>
 * Deliberately unaware of which flow was active — clearing fields unrelated to the current
 * step is a no-op, so one method safely covers every text-awaiting state. Which screen to
 * show after cancelling is a router-level decision (depends on {@link
 * com.polybezev.currencybot.model.UserMode}) and stays in {@code CurrencyBot}, not here.
 */
public final class NavigationUtil {

    private NavigationUtil() {}

    /**
     * Clears converter, admin-grant, and trade-setup temporary fields and resets the state.
     *
     * @param data FSM conversation state to reset; mutated in place
     */
    public static void cancelToIdle(UserConversationData data) {
        data.setState(ConversationState.IDLE);
        data.setAmount(null);
        data.setFromCurrency(null);
        data.setAdminGrantTargetId(null);
        data.setTradeSetupExchange(null);
        data.setTradeSetupApiKey(null);
        data.setLastBotMessageId(null);
    }
}
