package com.polybezev.currencybot.bot;

import com.polybezev.currencybot.config.BotConfig;
import com.polybezev.currencybot.formatter.BotMessages;
import com.polybezev.currencybot.handler.AdminCommandHandler;
import com.polybezev.currencybot.handler.CommandHandler;
import com.polybezev.currencybot.handler.PaymentHandler;
import com.polybezev.currencybot.handler.TradeHandler;
import com.polybezev.currencybot.model.ConversationState;
import com.polybezev.currencybot.model.Tier;
import com.polybezev.currencybot.model.UserConversationData;
import com.polybezev.currencybot.model.UserMode;
import com.polybezev.currencybot.service.SubscriptionService;
import com.polybezev.currencybot.service.UserStateService;
import com.polybezev.currencybot.util.NavigationUtil;
import com.polybezev.currencybot.util.UserInfoExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@RequiredArgsConstructor
@Slf4j
public class CurrencyBot extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final CommandHandler commandHandler;
    private final AdminCommandHandler adminCommandHandler;
    private final UserStateService userStateService;
    private final SubscriptionService subscriptionService;
    private final PaymentHandler paymentHandler;
    private final TradeHandler tradeHandler;

    // ==================== BOT IDENTITY ====================

    /**
     * Returns the bot's username as configured in {@link BotConfig}.
     */
    @Override
    public String getBotUsername() { return botConfig.getBotName(); }

    /**
     * Returns the bot's API token as configured in {@link BotConfig}.
     */
    @Override
    public String getBotToken() { return botConfig.getToken(); }

    // ==================== ROUTING ====================

    /**
     * Entry point for all incoming Telegram updates.
     * <p>
     * Routing priority (top-down, first match wins):
     * <ol>
     *   <li>Pre-checkout queries — confirmed immediately, no domain logic</li>
     *   <li>Callback queries — delegated to {@link #handleCallback(Update)}</li>
     *   <li>Successful payments — delegated to {@link PaymentHandler}</li>
     *   <li>Non-text messages — ignored</li>
     *   <li>Banned users — silently ignored</li>
     *   <li>Universal cancel — the stale reply-keyboard "◀️ Назад" button pressed while any
     *       free-text FSM step is active (converter, admin grant, trade setup, portfolio
     *       question); see {@link #cancelActiveInput}</li>
     *   <li>Admin slash-commands — handled with priority for admin users</li>
     *   <li>Admin panel FSM — non-IDLE admin state (text input mid-flow)</li>
     *   <li>Admin panel buttons — ADMIN mode, IDLE state</li>
     *   <li>Admin panel entry — {@link BotMessages#BTN_ADMIN} button press</li>
     *   <li>Converter FSM — non-IDLE converter states (see {@link ConversationState#isConverterState()})</li>
     *   <li>Slash-commands — any text starting with "/"</li>
     *   <li>Plain text and reply-keyboard buttons</li>
     * </ol>
     *
     * @param update incoming Telegram update
     */
    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasPreCheckoutQuery()) {
            AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery();
            answer.setPreCheckoutQueryId(update.getPreCheckoutQuery().getId());
            answer.setOk(true);
            try { execute(answer); } catch (TelegramApiException ignored) {}
            return;
        }

        if (update.hasCallbackQuery()) {
            handleCallback(update);
            return;
        }

        if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
            long chatId = update.getMessage().getChatId();
            send(paymentHandler.handleSuccessfulPayment(chatId, update.getMessage().getSuccessfulPayment()));
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String text      = update.getMessage().getText();
        long   chatId    = update.getMessage().getChatId();
        deleteUserMessage(chatId, update.getMessage().getMessageId());
        String username  = UserInfoExtractor.getUsername(update);
        String firstName = UserInfoExtractor.getFirstName(update);

        SubscriptionService.UserRegistration reg =
                subscriptionService.getOrCreateUser(chatId, username, firstName);

        if (reg.user().isBanned()) return;

        if (reg.isNew()) {
            log.info("New user registered: {}", chatId);
        }

        log.info("User {} sent: {}", chatId, text);

        boolean isAdmin = adminCommandHandler.isAdmin(chatId);

        if (isAdmin && adminCommandHandler.isAdminCommand(text)) {
            send(adminCommandHandler.handle(text, chatId));
            return;
        }

        UserConversationData data = userStateService.getOrCreate(chatId);
        ConversationState stateBefore = data.getState();
        UserMode modeBefore = data.getMode();

        SendMessage response;

        if (stateBefore != ConversationState.IDLE && text.equals(BotMessages.BTN_BACK)) {
            response = cancelActiveInput(chatId, data);
        } else if (modeBefore == UserMode.ADMIN && stateBefore != ConversationState.IDLE) {
            response = adminCommandHandler.handleAdminFsmInput(text, chatId, data);
        } else if (modeBefore == UserMode.ADMIN) {
            response = adminCommandHandler.handleAdminText(text, chatId, data, isAdmin);
        } else if (isAdmin && text.equals(BotMessages.BTN_ADMIN)) {
            response = adminCommandHandler.enterAdminPanel(chatId, data);
        } else if (data.getState().isTradeBotState()) {
            response = tradeHandler.handleFsmInput(text, chatId, data);
        } else if (stateBefore.isConverterState()) {
            response = commandHandler.handleFsmInput(text, chatId, data);
        } else if (text.startsWith("/")) {
            response = commandHandler.handleCommand(text, chatId, firstName, data, isAdmin);
        } else {
            response = commandHandler.handleText(text, chatId, data, isAdmin);
        }

        sendWithFsmEdit(chatId, data, stateBefore, response);
    }

    /**
     * Handles all incoming callback queries from inline keyboard buttons.
     * <p>
     * Always ACKs the query immediately to dismiss the loading spinner on the button.
     * Routing by callback data:
     * <ul>
     *   <li>{@code ADMIN_*} — admin panel actions</li>
     *   <li>FSM states {@link ConversationState#AWAIT_FROM}/{@link ConversationState#AWAIT_TO}
     *       — currency selection step inside the converter flow</li>
     *   <li>{@code SIGNAL_*} — TA signal for a specific coin (suffix = coin ID)</li>
     *   <li>{@link BotMessages#CALLBACK_BTC} — Bitcoin price card</li>
     *   <li>{@code BUY_*} — subscription payment invoice (suffix = tier name)</li>
     *   <li>other — currency code from the currencies inline keyboard (e.g. USD, EUR, CNY)</li>
     * </ul>
     *
     * @param update incoming Telegram update containing the callback query
     */
    private void handleCallback(Update update) {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        String data = update.getCallbackQuery().getData();
        log.info("User {} callback: {}", chatId, data);

        try {
            AnswerCallbackQuery ack = new AnswerCallbackQuery();
            ack.setCallbackQueryId(update.getCallbackQuery().getId());
            execute(ack);
        } catch (TelegramApiException ignored) {}

        UserConversationData fsm = userStateService.getOrCreate(chatId);

        if (data.equals(BotMessages.CALLBACK_CANCEL_INPUT)) {
            send(cancelActiveInput(chatId, fsm));
            return;
        }

        if (data.startsWith("ADMIN_")) {
            SendMessage resp = adminCommandHandler.handleAdminCallback(data, chatId, fsm);
            if (resp != null) send(resp);
            return;
        }

        if (data.startsWith(BotMessages.CALLBACK_TRADE_EXCHANGE_PREFIX)) {
            send(tradeHandler.handleExchangeCallback(data, chatId, fsm));
            return;
        }

        if (data.equals(BotMessages.CALLBACK_WATCHLIST_EDIT_OPEN)) {
            send(commandHandler.openWatchlistEditor(chatId));
            return;
        }

        if (data.startsWith(BotMessages.CALLBACK_WATCHLIST_TOGGLE_PREFIX)) {
            String code = data.substring(BotMessages.CALLBACK_WATCHLIST_TOGGLE_PREFIX.length());
            editKeyboardOnly(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                    commandHandler.handleWatchlistToggle(chatId, code));
            return;
        }

        if (data.equals(BotMessages.CALLBACK_WATCHLIST_DONE)) {
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            if (fsm.getState() == ConversationState.AWAIT_WATCHLIST_SELECTION) {
                fsm.setState(ConversationState.IDLE);
                tryEdit(chatId, messageId, commandHandler.handleWatchlistDoneOnboarding(chatId));
                send(commandHandler.resumeMainMenu(chatId, adminCommandHandler.isAdmin(chatId)));
            } else {
                tryEdit(chatId, messageId, commandHandler.handleWatchlistRates(chatId));
            }
            return;
        }

        SendMessage response;

        if (fsm.getState() == ConversationState.AWAIT_FROM
                || fsm.getState() == ConversationState.AWAIT_TO) {
            ConversationState stateBefore = fsm.getState();
            response = commandHandler.handleFsmInput(data, chatId, fsm);
            sendWithFsmEdit(chatId, fsm, stateBefore, response);
            return;
        }

        if (data.startsWith("SIGNAL_")) {
            response = commandHandler.handleSignalForCoin(chatId, data.substring(7));
        } else if (data.equals(BotMessages.CALLBACK_BTC)) {
            response = commandHandler.handleBtc(chatId);
        } else if (data.startsWith("BUY_")) {
            Tier tier = Tier.valueOf(data.substring(4));
            try { execute(paymentHandler.sendInvoice(chatId, tier)); }
            catch (TelegramApiException e) {
                log.error("Invoice error {}: {}", chatId, e.getMessage(), e);
            }
            return;
        } else {
            // Currency code callback from the currencies inline keyboard (e.g. USD, EUR, CNY)
            response = commandHandler.handleCurrencyRequest(data, chatId);
        }

        send(response);
    }

    /**
     * Aborts any in-progress free-text FSM step (converter, admin grant, trade bot setup,
     * portfolio AI question) and returns the screen matching the user's current {@link UserMode}.
     * <p>
     * Reached two ways: the inline {@link BotMessages#BTN_CANCEL_INLINE} button attached to every
     * such prompt, and — as a safety net — pressing the stale reply-keyboard "◀️ Назад" button
     * that stays visible from before the FSM step started (see routing priority in {@link
     * #onUpdateReceived}). Before this existed, that stale button's own label text was accepted
     * as raw FSM input; in {@link ConversationState#TRADE_AWAIT_SECRET} that meant it got
     * encrypted and saved as the user's exchange secret.
     *
     * @param chatId sender's chat ID
     * @param data   FSM state; cleared and reset to {@link ConversationState#IDLE} in place
     * @return the admin panel, trade bot status screen, LK, or main menu, depending on {@code data.getMode()}
     */
    private SendMessage cancelActiveInput(long chatId, UserConversationData data) {
        NavigationUtil.cancelToIdle(data);
        return switch (data.getMode()) {
            case ADMIN -> adminCommandHandler.enterAdminPanel(chatId, data);
            case TRADE_BOT -> tradeHandler.enterTradeBotSection(chatId, data);
            case LK -> commandHandler.enterLk(chatId, data);
            default -> commandHandler.resumeMainMenu(chatId, adminCommandHandler.isAdmin(chatId));
        };
    }

    // ==================== FSM EDIT-IN-PLACE ====================

    /**
     * Sends a bot reply, applying edit-in-place when the user is mid-converter FSM.
     * <p>
     * If {@code stateBefore} was a converter FSM state and a tracked message ID exists,
     * the previous message is edited rather than sending a new one — keeping the chat clean.
     * Falls back to sending a new message if editing fails (e.g. message is too old).
     * The tracked ID is cleared once the user exits the converter FSM.
     *
     * @param chatId      target chat
     * @param data        current FSM conversation data for this user
     * @param stateBefore FSM state captured before the handler ran
     * @param response    the message to send or use as the edit source
     */
    private void sendWithFsmEdit(long chatId, UserConversationData data,
                                  ConversationState stateBefore, SendMessage response) {
        Integer prevMsgId = data.getLastBotMessageId();

        if (stateBefore.isConverterState() && prevMsgId != null) {
            boolean edited = tryEdit(chatId, prevMsgId, response);
            if (!edited) {
                Message sent = sendTracked(response);
                if (sent != null) data.setLastBotMessageId(sent.getMessageId());
            }
            UserConversationData fresh = userStateService.getOrCreate(chatId);
            if (!fresh.getState().isConverterState()) {
                fresh.setLastBotMessageId(null);
            }
        } else {
            Message sent = sendTracked(response);
            if (sent != null && data.getState().isConverterState()) {
                data.setLastBotMessageId(sent.getMessageId());
            }
        }
    }

    /**
     * Attempts to edit an existing Telegram message in-place.
     * <p>
     * Text, parse mode, and inline keyboard markup are transferred from {@code source}.
     * Reply keyboard markup is intentionally skipped — it cannot be set via {@link EditMessageText}.
     * Copying parse mode matters as of the panel-style HTML cards (2026-08-03): the converter
     * FSM's result message ({@code CommandHandler.buildConvertResult}) reaches Telegram through
     * this edit-in-place path, not a fresh {@code send} — without it, {@code <b>} tags would show
     * up as literal text instead of being rendered.
     *
     * @param chatId    target chat
     * @param messageId ID of the message to edit
     * @param source    {@link SendMessage} whose text, parse mode, and inline markup to apply
     * @return {@code true} if the edit succeeded; {@code false} if Telegram rejected it
     */
    private boolean tryEdit(long chatId, int messageId, SendMessage source) {
        try {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(String.valueOf(chatId));
            edit.setMessageId(messageId);
            edit.setText(source.getText());
            edit.setParseMode(source.getParseMode());
            if (source.getReplyMarkup() instanceof InlineKeyboardMarkup kb) {
                edit.setReplyMarkup(kb);
            }
            execute(edit);
            return true;
        } catch (TelegramApiException e) {
            log.warn("Edit failed for msg {} of {}: {}", messageId, chatId, e.getMessage());
            return false;
        }
    }

    /**
     * Updates only the inline keyboard of an existing message, leaving its text untouched.
     * Used by the watchlist toggle callback so checking/unchecking a favourite doesn't
     * require re-sending the surrounding rates text or onboarding prompt on every tap.
     *
     * @param chatId    target chat
     * @param messageId ID of the message whose keyboard to replace
     * @param keyboard  refreshed inline keyboard
     */
    private void editKeyboardOnly(long chatId, int messageId, InlineKeyboardMarkup keyboard) {
        try {
            EditMessageReplyMarkup edit = new EditMessageReplyMarkup();
            edit.setChatId(String.valueOf(chatId));
            edit.setMessageId(messageId);
            edit.setReplyMarkup(keyboard);
            execute(edit);
        } catch (TelegramApiException e) {
            log.warn("Watchlist keyboard edit failed for {}: {}", chatId, e.getMessage());
        }
    }

    // ==================== MESSAGING ====================

    /**
     * Silently deletes the user's incoming message so the chat stays clean.
     * Failures (e.g. bot lacks delete permissions) are swallowed — not critical.
     */
    private void deleteUserMessage(long chatId, int messageId) {
        try {
            DeleteMessage del = new DeleteMessage();
            del.setChatId(String.valueOf(chatId));
            del.setMessageId(messageId);
            execute(del);
        } catch (TelegramApiException e) {
            log.warn("Could not delete user message {} in {}: {}", messageId, chatId, e.getMessage());
        }
    }

    /**
     * Sends a message, logging errors on failure. The resulting message ID is discarded.
     * Use {@link #sendTracked(SendMessage)} when the returned message ID is needed
     * (e.g. for FSM edit-in-place tracking).
     *
     * @param message the message to send; {@code null} is silently ignored
     */
    public void send(SendMessage message) {
        if (message == null) return;
        try { execute(message); }
        catch (TelegramApiException e) {
            log.error("Send error for {}: {}", message.getChatId(), e.getMessage(), e);
        }
    }

    /**
     * Sends a message and returns the resulting {@link Message} object, including its assigned ID.
     * Used by the FSM edit-in-place mechanism to remember which message to edit on the next step.
     *
     * @param message the message to send; {@code null} is silently ignored
     * @return the sent {@link Message}, or {@code null} if sending failed
     */
    public Message sendTracked(SendMessage message) {
        if (message == null) return null;
        try { return execute(message); }
        catch (TelegramApiException e) {
            log.error("Send error for {}: {}", message.getChatId(), e.getMessage(), e);
            return null;
        }
    }
}
