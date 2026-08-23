package com.polybezev.currencybot.handler;

import com.polybezev.currencybot.entity.TradeOrder;
import com.polybezev.currencybot.entity.UserExchangeCredentials;
import com.polybezev.currencybot.formatter.BotMessages;
import com.polybezev.currencybot.formatter.MessageFormatter;
import com.polybezev.currencybot.model.*;
import com.polybezev.currencybot.service.AiAnalysisService;
import com.polybezev.currencybot.service.SubscriptionService;
import com.polybezev.currencybot.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Handles all user interactions with the TIER 3 automated trading feature:
 * exchange setup FSM, trading toggle, history view, and credential deletion.
 * <p>
 * <b>Setup FSM states:</b>
 * <ol>
 *   <li>User presses 🔑 Привязать биржу → inline exchange selection keyboard</li>
 *   <li>{@code TRADE_EXCHANGE_*} callback → exchange stored in {@link UserConversationData},
 *       state set to {@link ConversationState#TRADE_AWAIT_API_KEY}</li>
 *   <li>User enters API key → stored temporarily in {@link UserConversationData#getTradeSetupApiKey()},
 *       state advances to {@link ConversationState#TRADE_AWAIT_SECRET}</li>
 *   <li>User enters secret → credentials encrypted and persisted, FSM reset to IDLE</li>
 * </ol>
 * <p>
 * <b>Security:</b> the API key is held in {@link UserConversationData} only between steps 3 and 4
 * and cleared immediately after {@link TradeService#saveCredentials} returns. It is never logged.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeHandler {

    private final TradeService tradeService;
    private final SubscriptionService subscriptionService;
    private final MessageFormatter formatter;
    private final AiAnalysisService aiAnalysisService;

    private static final int HISTORY_LIMIT = 10;

    // ==================== ENTRY ====================

    /**
     * Enters the TRADE_BOT UI section for the given user.
     * Requires TIER 3 access; returns an access-denied message otherwise.
     * <p>
     * Shows the appropriate status screen based on whether the user has credentials saved.
     *
     * @param chatId Telegram chat ID
     * @param data   FSM state; {@link UserMode} is set to {@link UserMode#TRADE_BOT}
     * @return trade bot status screen with the contextual reply keyboard
     */
    public SendMessage enterTradeBotSection(long chatId, UserConversationData data) {
        if (!subscriptionService.hasAccess(chatId, Tier.TIER_3)) {
            return msg(chatId, BotMessages.TRADE_BOT_TIER_REQUIRED);
        }
        data.setMode(UserMode.TRADE_BOT);
        return buildStatusScreen(chatId);
    }

    // ==================== REPLY KEYBOARD ROUTING ====================

    /**
     * Routes reply-keyboard button presses while the user is in {@link UserMode#TRADE_BOT}.
     *
     * @param text   button label pressed by the user
     * @param chatId sender's chat ID
     * @param data   current FSM state; may be modified (mode set to LK on back, state changes for setup)
     * @return response message to send back
     */
    public SendMessage handleTradeBotText(String text, long chatId, UserConversationData data) {
        return switch (text) {
            case BotMessages.BTN_CONNECT_EXCHANGE -> startExchangeSetup(chatId);
            case BotMessages.BTN_ENABLE_TRADING   -> enableTrading(chatId, true);
            case BotMessages.BTN_DISABLE_TRADING  -> enableTrading(chatId, false);
            case BotMessages.BTN_TRADE_HISTORY    -> showHistory(chatId);
            case BotMessages.BTN_PORTFOLIO_AI     -> startPortfolioQuestion(chatId, data);
            case BotMessages.BTN_DELETE_KEYS      -> deleteKeys(chatId);
            case BotMessages.BTN_BACK -> {
                data.setMode(UserMode.LK);
                Tier tier = subscriptionService.getActiveTier(chatId);
                yield msg(chatId, BotMessages.LK_HEADER, formatter.buildLkKeyboard(tier));
            }
            default -> buildStatusScreen(chatId);
        };
    }

    // ==================== SETUP FSM ====================

    /**
     * Handles inline keyboard callbacks from the exchange selection step.
     * Stores the chosen exchange in {@code data}, advances the FSM to
     * {@link ConversationState#TRADE_AWAIT_API_KEY}, and prompts for the API key.
     *
     * @param callbackData full callback data string (e.g. {@code "TRADE_EXCHANGE_BINANCE"})
     * @param chatId       sender's chat ID
     * @param data         FSM state; exchange and state are updated
     * @return API key prompt message, or an error if the exchange name is unrecognised
     */
    public SendMessage handleExchangeCallback(String callbackData, long chatId, UserConversationData data) {
        String exchangeName = callbackData.substring(BotMessages.CALLBACK_TRADE_EXCHANGE_PREFIX.length());
        try {
            SupportedExchange exchange = SupportedExchange.valueOf(exchangeName);
            data.setTradeSetupExchange(exchange);
            data.setState(ConversationState.TRADE_AWAIT_API_KEY);
            String prompt = BotMessages.TRADE_AWAIT_API_KEY.replace("{exchange}", exchange.displayName);
            SendMessage m = msg(chatId, prompt, formatter.buildCancelKeyboard());
            m.setParseMode("Markdown");
            return m;
        } catch (IllegalArgumentException e) {
            return msg(chatId, BotMessages.UNKNOWN_COMMAND);
        }
    }

    /**
     * Processes text input during the trade bot setup FSM.
     * <p>
     * In {@link ConversationState#TRADE_AWAIT_API_KEY}: stores the key temporarily and
     * advances to {@link ConversationState#TRADE_AWAIT_SECRET}.
     * <p>
     * In {@link ConversationState#TRADE_AWAIT_SECRET}: encrypts and saves both credentials,
     * clears all temporary FSM fields, and resets the state to {@link ConversationState#IDLE}.
     *
     * @param text   raw user input for the current FSM step
     * @param chatId sender's chat ID
     * @param data   FSM state; mutated as the flow progresses
     * @return next prompt or success message
     */
    public SendMessage handleFsmInput(String text, long chatId, UserConversationData data) {
        return switch (data.getState()) {
            case TRADE_AWAIT_API_KEY -> {
                data.setTradeSetupApiKey(text);
                data.setState(ConversationState.TRADE_AWAIT_SECRET);
                yield msg(chatId, BotMessages.TRADE_AWAIT_SECRET, formatter.buildCancelKeyboard());
            }
            case TRADE_AWAIT_SECRET -> {
                SupportedExchange exchange = data.getTradeSetupExchange();
                String apiKey = data.getTradeSetupApiKey();
                try {
                    tradeService.saveCredentials(chatId, exchange, apiKey, text);
                    log.info("Trade credentials saved for chatId={}", chatId);
                } finally {
                    data.setTradeSetupApiKey(null);
                    data.setTradeSetupExchange(null);
                    data.setState(ConversationState.IDLE);
                }
                yield msg(chatId,
                        BotMessages.TRADE_SETUP_SUCCESS.replace("{exchange}", exchange.displayName),
                        buildTradeBotKeyboard(chatId));
            }
            case AWAIT_PORTFOLIO_QUESTION -> answerPortfolioQuestion(chatId, text, data);
            default -> buildStatusScreen(chatId);
        };
    }

    // ==================== FEATURE ACTIONS ====================

    /**
     * Enables or disables automated trading and returns the updated status screen.
     *
     * @param chatId  Telegram chat ID
     * @param enabled {@code true} to activate, {@code false} to pause
     * @return status message with the appropriate text
     */
    private SendMessage enableTrading(long chatId, boolean enabled) {
        tradeService.setTradingEnabled(chatId, enabled);
        String text = enabled ? BotMessages.TRADE_ENABLED : BotMessages.TRADE_DISABLED;
        return msg(chatId, text, buildTradeBotKeyboard(chatId));
    }

    /**
     * Builds and returns the trade history screen.
     * Shows a "no history" message if no orders have been placed yet.
     *
     * @param chatId Telegram chat ID
     * @return history message or empty-history notice
     */
    private SendMessage showHistory(long chatId) {
        List<TradeOrder> orders = tradeService.getHistory(chatId, HISTORY_LIMIT);
        if (orders.isEmpty()) return msg(chatId, BotMessages.TRADE_NO_HISTORY);
        return msg(chatId, formatter.buildTradeHistory(orders));
    }

    /**
     * Starts the "Портфель (ИИ)" flow: advances the FSM to
     * {@link ConversationState#AWAIT_PORTFOLIO_QUESTION} and prompts the user for a question.
     *
     * @param chatId Telegram chat ID
     * @param data   FSM state; mutated to await the question
     * @return prompt message
     */
    private SendMessage startPortfolioQuestion(long chatId, UserConversationData data) {
        data.setState(ConversationState.AWAIT_PORTFOLIO_QUESTION);
        return msg(chatId, BotMessages.PORTFOLIO_AI_AWAIT_QUESTION, formatter.buildCancelKeyboard());
    }

    /**
     * Forwards the user's free-text question to the AI agent along with their chatId
     * (so the agent can call {@code get_user_portfolio} with the right {@code userId}),
     * resets the FSM to {@link ConversationState#IDLE}, and returns the answer.
     *
     * @param chatId sender's chat ID
     * @param text   the user's question
     * @param data   FSM state; reset to IDLE regardless of outcome
     * @return AI answer with disclaimer, or a fallback message if the agent is unavailable
     */
    private SendMessage answerPortfolioQuestion(long chatId, String text, UserConversationData data) {
        data.setState(ConversationState.IDLE);
        String answer = aiAnalysisService.generatePortfolioAnswer(chatId, text);
        SendMessage m = msg(chatId,
                answer != null ? answer + BotMessages.PORTFOLIO_AI_DISCLAIMER : BotMessages.PORTFOLIO_AI_UNAVAILABLE,
                buildTradeBotKeyboard(chatId));
        m.setParseMode("Markdown");
        return m;
    }

    /**
     * Deletes the user's exchange credentials and returns a confirmation message.
     *
     * @param chatId Telegram chat ID
     * @return deletion confirmation with the no-keys keyboard
     */
    private SendMessage deleteKeys(long chatId) {
        tradeService.deleteCredentials(chatId);
        return msg(chatId, BotMessages.TRADE_KEYS_DELETED, formatter.buildTradeBotKeyboard(false, false));
    }

    // ==================== HELPERS ====================

    /**
     * Starts the exchange selection step by showing the inline exchange keyboard.
     *
     * @param chatId Telegram chat ID
     * @return exchange selection prompt with inline keyboard
     */
    private SendMessage startExchangeSetup(long chatId) {
        return msg(chatId, BotMessages.TRADE_SELECT_EXCHANGE, formatter.buildExchangeKeyboard());
    }

    /**
     * Builds the contextual trade bot status screen based on the user's current credential state.
     *
     * @param chatId Telegram chat ID
     * @return status screen with the appropriate keyboard and text
     */
    private SendMessage buildStatusScreen(long chatId) {
        Optional<UserExchangeCredentials> creds = tradeService.getCredentials(chatId);
        if (creds.isEmpty()) {
            return msgHtml(chatId, formatter.buildTradeBotNoKeys(),
                    formatter.buildTradeBotKeyboard(false, false));
        }
        UserExchangeCredentials c = creds.get();
        return msgHtml(chatId,
                formatter.buildTradeBotStatus(c.getExchange().displayName, c.isTradingEnabled()),
                formatter.buildTradeBotKeyboard(true, c.isTradingEnabled()));
    }

    /**
     * Builds the trade bot reply keyboard based on the user's current credential state,
     * fetching the state from the database.
     *
     * @param chatId Telegram chat ID
     * @return contextual reply keyboard
     */
    private ReplyKeyboard buildTradeBotKeyboard(long chatId) {
        Optional<UserExchangeCredentials> creds = tradeService.getCredentials(chatId);
        return creds
                .map(c -> formatter.buildTradeBotKeyboard(true, c.isTradingEnabled()))
                .orElseGet(() -> formatter.buildTradeBotKeyboard(false, false));
    }

    private SendMessage msg(long chatId, String text) {
        SendMessage m = new SendMessage();
        m.setChatId(String.valueOf(chatId));
        m.setText(text);
        return m;
    }

    private SendMessage msg(long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage m = msg(chatId, text);
        m.setReplyMarkup(keyboard);
        return m;
    }

    /**
     * Like {@link #msg(long, String, ReplyKeyboard)}, but sets {@code ParseMode.HTML} — used for
     * the panel-style status cards ({@link MessageFormatter#buildTradeBotStatus},
     * {@link MessageFormatter#buildTradeBotNoKeys}).
     */
    private SendMessage msgHtml(long chatId, String text, ReplyKeyboard keyboard) {
        SendMessage m = msg(chatId, text, keyboard);
        m.setParseMode("HTML");
        return m;
    }
}
