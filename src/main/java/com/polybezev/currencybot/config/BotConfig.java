package com.polybezev.currencybot.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Holds bot credentials and admin configuration loaded from {@code application.properties}.
 * <p>
 * Spring Boot loads {@code application.properties} automatically — no {@code @PropertySource} needed.
 * Only {@code @Getter} is generated: setters, {@code equals} and {@code hashCode} are not
 * appropriate for an immutable configuration class.
 */
@Configuration
@Getter
public class BotConfig {

    /** Telegram bot username, bound to {@code bot.name} in application.properties. */
    @Value("${bot.name}")
    private String botName;

    /** Telegram bot API token, bound to {@code bot.token} in application.properties. */
    @Value("${bot.token}")
    private String token;

    /** Telegram chat ID of the administrator, bound to {@code bot.admin.chatId}. */
    @Value("${bot.admin.chatId}")
    private long adminChatId;
}
