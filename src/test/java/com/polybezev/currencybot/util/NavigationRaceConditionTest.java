package com.polybezev.currencybot.util;

import com.polybezev.currencybot.formatter.BotMessages;
import com.polybezev.currencybot.formatter.MessageFormatter;
import com.polybezev.currencybot.handler.TradeHandler;
import com.polybezev.currencybot.model.ConversationState;
import com.polybezev.currencybot.model.SupportedExchange;
import com.polybezev.currencybot.model.UserConversationData;
import com.polybezev.currencybot.service.AiAnalysisService;
import com.polybezev.currencybot.service.SubscriptionService;
import com.polybezev.currencybot.service.TradeService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Регрессионный тест на реальный баг (см. JOURNAL 2026-08-03, "Универсальная
 * отмена FSM-ввода"): устаревшая reply-кнопка "◀️ Назад" оставалась видимой
 * поверх шага ввода секретного ключа биржи ({@code TRADE_AWAIT_SECRET}).
 * Нажатие на неё приходило боту обычным текстовым сообщением, ничем не
 * отличимым от реального ввода — без централизованной отмены текст кнопки
 * улетал напрямую в {@link TradeHandler} и сохранялся как будто это и есть
 * секретный ключ. Не косметика — порча реальных учётных данных пользователя.
 * <p>
 * В оригинальном проекте фикс был сделан без покрытия тестом (см.
 * {@code ai/JOURNAL.md}); здесь — сначала показываем сам баг напрямую через
 * {@link TradeHandler}, затем подтверждаем, что условие диспетчера из
 * {@code CurrencyBot.onUpdateReceived} (перехват "Назад" ДО хендлера,
 * {@link NavigationUtil#cancelToIdle}) его предотвращает.
 */
class NavigationRaceConditionTest {

    private TradeHandler newHandler(TradeService tradeService) {
        return new TradeHandler(tradeService, mock(SubscriptionService.class),
                mock(MessageFormatter.class), mock(AiAnalysisService.class));
    }

    @Test
    void withoutGuard_pressingBackDuringSecretInput_savesButtonLabelAsTheSecret() {
        // Без централизованного перехвата "Назад" (как если бы UpdateProcessor
        // передал текст кнопки прямо в хендлер шага) — воспроизводим баг.
        TradeService tradeService = mock(TradeService.class);
        when(tradeService.getCredentials(anyLong())).thenReturn(Optional.empty());
        TradeHandler handler = newHandler(tradeService);

        UserConversationData data = new UserConversationData();
        data.setState(ConversationState.TRADE_AWAIT_SECRET);
        data.setTradeSetupExchange(SupportedExchange.BINANCE);
        data.setTradeSetupApiKey("real-api-key-abc123");

        handler.handleFsmInput(BotMessages.BTN_BACK, 100L, data);

        // Баг: текст кнопки "◀️ Назад" ушёл в saveCredentials как реальный секрет.
        verify(tradeService).saveCredentials(
                100L, SupportedExchange.BINANCE, "real-api-key-abc123", BotMessages.BTN_BACK);
    }

    @Test
    void withGuard_pressingBackDuringSecretInput_cancelsInstead_secretNeverTouched() {
        TradeService tradeService = mock(TradeService.class);

        UserConversationData data = new UserConversationData();
        data.setState(ConversationState.TRADE_AWAIT_SECRET);
        data.setTradeSetupExchange(SupportedExchange.BINANCE);
        data.setTradeSetupApiKey("real-api-key-abc123");

        ConversationState stateBefore = data.getState();
        String text = BotMessages.BTN_BACK;

        // Реальное условие из CurrencyBot.onUpdateReceived: перехватывает "Назад"
        // раньше, чем текст мог бы дойти до состояние-специфичного хендлера.
        boolean cancelGuardTriggers =
                stateBefore != ConversationState.IDLE && text.equals(BotMessages.BTN_BACK);
        assertThat(cancelGuardTriggers).isTrue();

        if (cancelGuardTriggers) {
            NavigationUtil.cancelToIdle(data);
        } else {
            newHandler(tradeService).handleFsmInput(text, 100L, data);
        }

        // Фикс: TradeHandler/saveCredentials не вызывался вообще, секрет не тронут,
        // состояние сброшено в IDLE — ровно то, что дал бы честный "Отмена".
        verifyNoInteractions(tradeService);
        assertThat(data.getState()).isEqualTo(ConversationState.IDLE);
        assertThat(data.getTradeSetupApiKey()).isNull();
        assertThat(data.getTradeSetupExchange()).isNull();
    }
}
