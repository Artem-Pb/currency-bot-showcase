package com.polybezev.currencybot.bot;

import com.polybezev.currencybot.formatter.BotMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotInitializer {
    private final CurrencyBot currencyBot;

    /**
     * Registers the bot with the Telegram API and publishes the global command menu
     * shown in the "/" shortcut list inside Telegram clients.
     * <p>
     * Triggered once on Spring context refresh ({@link ContextRefreshedEvent}).
     * Command descriptions are kept in {@link BotMessages} so they stay in sync
     * with the rest of the UI copy.
     *
     * @throws TelegramApiException if bot registration or the command list update fails
     */
    @EventListener(ContextRefreshedEvent.class)
    public void init() throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(currencyBot);

        currencyBot.execute(new SetMyCommands(List.of(
                new BotCommand("/start",   BotMessages.CMD_START_DESC),
                new BotCommand("/help",    BotMessages.CMD_HELP_DESC),
                new BotCommand("/curse",   BotMessages.CMD_CURSE_DESC),
                new BotCommand("/convert", BotMessages.CMD_CONVERT_DESC),
                new BotCommand("/list",    BotMessages.CMD_LIST_DESC),
                new BotCommand("/btc",     BotMessages.CMD_BTC_DESC),
                new BotCommand("/tier",    BotMessages.CMD_TIER_DESC),
                new BotCommand("/signal",  BotMessages.CMD_SIGNAL_DESC)
        ), new BotCommandScopeDefault(), null));

        log.info("CurrencyBot registered successfully");
    }
}
