package com.polybezev.currencybot.util;

import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Null-safe helpers for extracting user identity fields from a Telegram {@link Update}.
 * <p>
 * All methods return {@code null} rather than throwing — the Telegram Bot API does not guarantee
 * that {@code Chat} fields are present on every update type, so defensive extraction is required.
 */
public final class UserInfoExtractor {
    private UserInfoExtractor() {}

    /**
     * Extracts the user's first name from the message chat, trimmed.
     * Returns {@code null} if the update has no message, the chat is absent,
     * the first name is absent, or the string is blank.
     *
     * @param update incoming Telegram update
     * @return trimmed first name, or {@code null}
     */
    public static String getFirstName(Update update) {
        if (update == null || !update.hasMessage()) return null;
        Chat chat = update.getMessage().getChat();
        if (chat == null) return null;
        String firstName = chat.getFirstName();
        return (firstName != null && !firstName.trim().isEmpty()) ? firstName.trim() : null;
    }

    /**
     * Extracts the user's Telegram username (without the {@code @} prefix), trimmed.
     * Returns {@code null} if the update has no message, the chat is absent,
     * the username is absent, or the string is blank.
     *
     * @param update incoming Telegram update
     * @return trimmed username, or {@code null}
     */
    public static String getUsername(Update update) {
        if (update == null || !update.hasMessage()) return null;
        Chat chat = update.getMessage().getChat();
        if (chat == null) return null;
        String username = chat.getUserName();
        return (username != null && !username.trim().isEmpty()) ? username.trim() : null;
    }
}
