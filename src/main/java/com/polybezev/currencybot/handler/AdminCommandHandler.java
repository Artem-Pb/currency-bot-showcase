package com.polybezev.currencybot.handler;

import com.polybezev.currencybot.config.BotConfig;
import com.polybezev.currencybot.entity.User;
import com.polybezev.currencybot.formatter.BotMessages;
import com.polybezev.currencybot.formatter.MessageFormatter;
import com.polybezev.currencybot.model.ConversationState;
import com.polybezev.currencybot.model.Tier;
import com.polybezev.currencybot.model.UserConversationData;
import com.polybezev.currencybot.model.UserMode;
import com.polybezev.currencybot.repository.UserRepository;
import com.polybezev.currencybot.scheduler.MorningDigestScheduler;
import com.polybezev.currencybot.service.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all admin-only interactions: slash commands, reply-keyboard buttons inside the admin panel,
 * FSM input for the grant flow, and inline keyboard callbacks prefixed with {@code ADMIN_}.
 * <p>
 * {@code MorningDigestScheduler} is injected with {@code @Lazy} to break the Spring circular
 * dependency: {@code AdminCommandHandler} → {@code MorningDigestScheduler} → {@code CurrencyBot}
 * → {@code AdminCommandHandler}. The lazy proxy is resolved on first access.
 */
@Component
@Slf4j
public class AdminCommandHandler {

    private final BotConfig botConfig;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final MessageFormatter formatter;

    // @Lazy breaks the circular dependency with CurrencyBot
    @Lazy
    @Autowired
    private MorningDigestScheduler digestScheduler;

    private static final int PAGE_SIZE = 5;

    public AdminCommandHandler(BotConfig botConfig,
                               SubscriptionService subscriptionService,
                               UserRepository userRepository,
                               MessageFormatter formatter) {
        this.botConfig = botConfig;
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
        this.formatter = formatter;
    }

    /**
     * Returns {@code true} if the given chat ID belongs to the configured administrator.
     *
     * @param chatId Telegram chat ID to check
     * @return {@code true} if this is the admin user
     */
    public boolean isAdmin(long chatId) {
        return chatId == botConfig.getAdminChatId();
    }

    /**
     * Returns {@code true} if the text is a legacy admin slash command
     * ({@code /digest}, {@code /grant}, {@code /ban}).
     * These are handled with priority before the main router.
     *
     * @param text message text to test
     * @return {@code true} if it is a recognised admin slash command
     */
    public boolean isAdminCommand(String text) {
        return text.equals("/digest") || text.startsWith("/grant") || text.startsWith("/ban");
    }

    // ==================== SLASH КОМАНДЫ ====================

    /**
     * Dispatches legacy admin slash commands to their specific handlers.
     *
     * @param text   the full slash command text
     * @param chatId admin's chat ID
     * @return response message
     */
    public SendMessage handle(String text, long chatId) {
        if (text.equals("/digest")) {
            digestScheduler.sendMorningDigest();
            return msg(chatId, BotMessages.ADMIN_DIGEST_TRIGGERED);
        }
        if (text.startsWith("/grant")) return handleGrant(text, chatId);
        if (text.startsWith("/ban"))   return handleBanCommand(text, chatId);
        return msg(chatId, BotMessages.ADMIN_UNKNOWN_CMD);
    }

    /**
     * Handles {@code /grant <chatId> <tier>} — grants a tier directly via slash command.
     *
     * @param text   the full slash command text
     * @param chatId admin's chat ID
     * @return success or format-error message
     */
    private SendMessage handleGrant(String text, long chatId) {
        String[] parts = text.split(" ");
        if (parts.length != 3) return msg(chatId, BotMessages.ADMIN_GRANT_FORMAT);
        try {
            long targetId = Long.parseLong(parts[1]);
            Tier tier = Tier.valueOf(parts[2].toUpperCase());
            subscriptionService.grantSubscription(targetId, tier);
            log.info("Admin {} granted {} to {}", chatId, tier, targetId);
            return msg(chatId, BotMessages.ADMIN_GRANT_SUCCESS
                    .replace("{tier}",     tier.label)
                    .replace("{targetId}", String.valueOf(targetId)));
        } catch (NumberFormatException e) {
            return msg(chatId, BotMessages.ADMIN_GRANT_FORMAT);
        } catch (IllegalArgumentException e) {
            return msg(chatId, BotMessages.ADMIN_GRANT_UNKNOWN_TIER);
        }
    }

    /**
     * Handles {@code /ban <chatId>} — bans a user directly via slash command.
     *
     * @param text   the full slash command text
     * @param chatId admin's chat ID
     * @return success or format-error message
     */
    private SendMessage handleBanCommand(String text, long chatId) {
        String[] parts = text.split(" ");
        if (parts.length != 2) return msg(chatId, BotMessages.ADMIN_BAN_FORMAT);
        try {
            long targetId = Long.parseLong(parts[1]);
            subscriptionService.banUser(targetId);
            return msg(chatId, BotMessages.ADMIN_BAN_SUCCESS.replace("{chatId}", String.valueOf(targetId)));
        } catch (NumberFormatException e) {
            return msg(chatId, BotMessages.ADMIN_GRANT_ID_ERROR);
        }
    }

    // ==================== КНОПОЧНЫЙ РЕЖИМ АДМИНКИ ====================

    /**
     * Switches the user to admin panel mode and sends the admin reply keyboard.
     *
     * @param chatId admin's chat ID
     * @param data   FSM state; mode is set to {@link UserMode#ADMIN}, state to {@link ConversationState#IDLE}
     * @return admin panel header with admin keyboard
     */
    public SendMessage enterAdminPanel(long chatId, UserConversationData data) {
        data.setMode(UserMode.ADMIN);
        data.setState(ConversationState.IDLE);
        return msg(chatId, BotMessages.ADMIN_HEADER, formatter.buildAdminKeyboard());
    }

    /**
     * Routes reply-keyboard button presses while the user is in ADMIN mode (IDLE state).
     *
     * @param text    button label pressed
     * @param chatId  admin's chat ID
     * @param data    FSM state; may be modified (state/mode transitions)
     * @param isAdmin whether the caller is the admin (passed through for main keyboard on back)
     * @return response message
     */
    public SendMessage handleAdminText(String text, long chatId, UserConversationData data, boolean isAdmin) {
        switch (text) {
            case BotMessages.BTN_USERS -> { return showUserList(chatId, 0); }
            case BotMessages.BTN_GRANT -> {
                data.setState(ConversationState.ADMIN_AWAIT_GRANT_ID);
                return msg(chatId, BotMessages.ADMIN_GRANT_ASK_ID, formatter.buildCancelKeyboard());
            }
            case BotMessages.BTN_BANS -> { return showBannedList(chatId); }
            case BotMessages.BTN_BACK -> {
                data.setMode(UserMode.MAIN);
                data.setState(ConversationState.IDLE);
                return msg(chatId, formatter.buildStartText(null), formatter.buildMainKeyboard(isAdmin));
            }
            default -> { return msg(chatId, BotMessages.ADMIN_PANEL_PROMPT); }
        }
    }

    /**
     * Handles text input during the admin grant FSM flow.
     * <p>
     * State {@link ConversationState#ADMIN_AWAIT_GRANT_ID}: validates the entered chatId,
     * stores it, advances to {@link ConversationState#ADMIN_AWAIT_GRANT_TIER},
     * and shows the tier selection keyboard.
     *
     * @param text   user input for the current FSM step
     * @param chatId admin's chat ID
     * @param data   FSM state; mutated as the flow progresses
     * @return next prompt or error message
     */
    public SendMessage handleAdminFsmInput(String text, long chatId, UserConversationData data) {
        return switch (data.getState()) {
            case ADMIN_AWAIT_GRANT_ID -> {
                try {
                    long id = Long.parseLong(text.trim());
                    data.setAdminGrantTargetId(id);
                    data.setState(ConversationState.ADMIN_AWAIT_GRANT_TIER);
                    String prompt = BotMessages.ADMIN_GRANT_ASK_TIER.replace("{targetId}", String.valueOf(id));
                    yield msg(chatId, prompt, formatter.buildAdminGrantTierKeyboard());
                } catch (NumberFormatException e) {
                    yield msg(chatId, BotMessages.ADMIN_GRANT_ID_ERROR);
                }
            }
            default -> msg(chatId, BotMessages.ADMIN_FSM_UNEXPECTED);
        };
    }

    /**
     * Handles inline keyboard callbacks from the admin panel.
     * <p>
     * Supported callback prefixes:
     * <ul>
     *   <li>{@code ADMIN_GRANT_TIER_<tier>} — completes the grant flow for the stored target user</li>
     *   <li>{@code ADMIN_BAN_<chatId>} — bans the specified user</li>
     *   <li>{@code ADMIN_UNBAN_<chatId>} — unbans the specified user</li>
     *   <li>{@code ADMIN_START_GRANT_<chatId>} — starts the grant flow for a specific user</li>
     *   <li>{@code ADMIN_PAGE_<page>} — navigates to a page in the user list</li>
     * </ul>
     *
     * @param data callback data string
     * @param chatId admin's chat ID
     * @param fsm  current FSM state for the admin
     * @return response message, or {@code null} if the callback is unrecognised
     */
    public SendMessage handleAdminCallback(String data, long chatId, UserConversationData fsm) {
        if (data.startsWith("ADMIN_GRANT_TIER_")) {
            String tierName = data.substring("ADMIN_GRANT_TIER_".length());
            Long targetId = fsm.getAdminGrantTargetId();
            if (targetId == null) return msg(chatId, BotMessages.ADMIN_GRANT_TARGET_LOST);
            try {
                Tier tier = Tier.valueOf(tierName);
                subscriptionService.grantSubscription(targetId, tier);
                fsm.setState(ConversationState.IDLE);
                fsm.setAdminGrantTargetId(null);
                log.info("Admin {} granted {} to {}", chatId, tier, targetId);
                return msg(chatId, BotMessages.ADMIN_GRANT_SUCCESS
                        .replace("{targetId}", String.valueOf(targetId))
                        .replace("{tier}",     tier.label));
            } catch (Exception e) {
                return msg(chatId, BotMessages.ADMIN_GRANT_EXEC_ERROR.replace("{reason}", e.getMessage()));
            }
        }
        if (data.startsWith("ADMIN_BAN_")) {
            long targetId = Long.parseLong(data.substring("ADMIN_BAN_".length()));
            subscriptionService.banUser(targetId);
            return msg(chatId, BotMessages.ADMIN_BAN_SUCCESS.replace("{chatId}", String.valueOf(targetId)));
        }
        if (data.startsWith("ADMIN_UNBAN_")) {
            long targetId = Long.parseLong(data.substring("ADMIN_UNBAN_".length()));
            subscriptionService.unbanUser(targetId);
            return msg(chatId, BotMessages.ADMIN_UNBAN_SUCCESS.replace("{chatId}", String.valueOf(targetId)));
        }
        if (data.startsWith("ADMIN_START_GRANT_")) {
            long targetId = Long.parseLong(data.substring("ADMIN_START_GRANT_".length()));
            fsm.setAdminGrantTargetId(targetId);
            fsm.setState(ConversationState.ADMIN_AWAIT_GRANT_TIER);
            String prompt = BotMessages.ADMIN_GRANT_ASK_TIER.replace("{targetId}", String.valueOf(targetId));
            return msg(chatId, prompt, formatter.buildAdminGrantTierKeyboard());
        }
        if (data.startsWith("ADMIN_PAGE_")) {
            int page = Integer.parseInt(data.substring("ADMIN_PAGE_".length()));
            return showUserList(chatId, page);
        }
        return null;
    }

    // ==================== СПИСКИ ====================

    /**
     * Builds a paginated user list with per-user inline action buttons (grant tier, ban/unban).
     * Pagination navigation buttons are appended when there are multiple pages.
     *
     * @param chatId admin's chat ID
     * @param page   zero-based page index
     * @return user list message with inline keyboard
     */
    public SendMessage showUserList(long chatId, int page) {
        Page<User> userPage = userRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, PAGE_SIZE));
        List<String> lines = new ArrayList<>();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (User u : userPage.getContent()) {
            Tier tier     = subscriptionService.getActiveTier(u.getChatId());
            String name   = u.getFirstName() != null ? u.getFirstName() : "—";
            String uname  = u.getUsername() != null ? " (@" + u.getUsername() + ")" : "";
            String banned = u.isBanned() ? " 🚫" : "";
            lines.add(String.format("%s%s%s\n  ID: %d · %s", name, uname, banned, u.getChatId(), tier.label));

            String banLabel    = u.isBanned() ? "✅ Разбан" : "🚫 Бан";
            String banCallback = u.isBanned()
                    ? "ADMIN_UNBAN_" + u.getChatId()
                    : "ADMIN_BAN_"   + u.getChatId();
            rows.add(List.of(
                    btn(name + " · 🔑 Тир", "ADMIN_START_GRANT_" + u.getChatId()),
                    btn(name + " · " + banLabel, banCallback)
            ));
        }

        if (userPage.getTotalPages() > 1) {
            List<InlineKeyboardButton> nav = new ArrayList<>();
            if (page > 0)
                nav.add(btn("◀ Назад",   "ADMIN_PAGE_" + (page - 1)));
            if (page < userPage.getTotalPages() - 1)
                nav.add(btn("Вперёд ▶", "ADMIN_PAGE_" + (page + 1)));
            if (!nav.isEmpty()) rows.add(nav);
        }

        return msg(chatId,
                formatter.buildAdminUserList(lines, page + 1, userPage.getTotalPages()),
                new InlineKeyboardMarkup(rows));
    }

    /**
     * Returns a flat list of all banned users.
     * <p>
     * Shows a "no banned users" message when the count is zero.
     *
     * @param chatId admin's chat ID
     * @return ban list message
     */
    private SendMessage showBannedList(long chatId) {
        long count = userRepository.countByBanned(true);
        if (count == 0) return msg(chatId, BotMessages.ADMIN_BAN_NONE);
        List<User> banned = userRepository.findByBanned(true);
        List<String> lines = new ArrayList<>();
        banned.forEach(u -> lines.add(u.getChatId() + " — " + u.getFirstName()));
        return msg(chatId, BotMessages.ADMIN_BAN_LIST
                .replace("{count}", String.valueOf(count))
                .replace("{list}",  String.join("\n", lines)));
    }

    // ==================== HELPERS ====================

    private InlineKeyboardButton btn(String text, String callback) {
        InlineKeyboardButton b = new InlineKeyboardButton();
        b.setText(text);
        b.setCallbackData(callback);
        return b;
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
}
