package com.polybezev.currencybot.scheduler;

import com.polybezev.currencybot.bot.CurrencyBot;
import com.polybezev.currencybot.entity.TradeOrder;
import com.polybezev.currencybot.entity.UserExchangeCredentials;
import com.polybezev.currencybot.formatter.BotMessages;
import com.polybezev.currencybot.model.TradeStatus;
import com.polybezev.currencybot.repository.UserExchangeCredentialsRepository;
import com.polybezev.currencybot.service.TaSignalService;
import com.polybezev.currencybot.service.TaSignalService.SignalResult;
import com.polybezev.currencybot.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;
import java.util.Set;

/**
 * Automated trading scheduler that executes trades on behalf of TIER 3 subscribers
 * based on fresh TA signals from Binance.
 * <p>
 * <b>Run cadence:</b> every 4 hours at 02:00, 06:00, 10:00, 14:00, 18:00, 22:00 Moscow time.
 * The 2-hour offset from midnight avoids collision with {@link SignalScheduler} which runs
 * at 00:00, 04:00, 08:00 etc. — separating the two keeps Binance API load spread out.
 * <p>
 * <b>Per-run flow:</b>
 * <ol>
 *   <li>Fetch all users with {@code tradingEnabled = true} (TIER 3 already implied by credential existence)</li>
 *   <li>For each of the 12 supported coins, fetch a fresh signal via {@link TaSignalService#analyzeFresh}</li>
 *   <li>For each user: if signal is actionable (score ≥ +2 or ≤ -2), attempt a market order</li>
 *   <li>Notify the user about the result (success or failure)</li>
 * </ol>
 * <p>
 * <b>Isolation:</b> a failure for one user or one coin does not abort the run for others —
 * each combination is wrapped in its own try/catch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeScheduler {

    private final TaSignalService taSignalService;
    private final TradeService tradeService;
    private final UserExchangeCredentialsRepository credentialsRepository;
    private final CurrencyBot currencyBot;

    // ==================== SCHEDULER ====================

    /**
     * Main scheduled entry point. Runs every 4 hours offset by 2 hours from midnight Moscow time
     * (02:00, 06:00, 10:00, 14:00, 18:00, 22:00).
     * <p>
     * For each active trader × each coin, attempts a trade if the signal is strong enough.
     * Users with no active traders or empty coin lists produce no API calls.
     */
    @Scheduled(cron = "0 0 2/4 * * *", zone = "Europe/Moscow")
    public void runTradingCycle() {
        List<UserExchangeCredentials> traders = credentialsRepository.findAllWithTradingEnabled();
        if (traders.isEmpty()) return;

        Set<String> coins = taSignalService.supportedCoins();
        log.info("TradeScheduler run: {} active traders, {} coins", traders.size(), coins.size());

        for (String coin : coins) {
            SignalResult signal = fetchSignal(coin);
            if (signal == null) continue;

            if (signal.score() < 2 && signal.score() > -2) continue;

            for (UserExchangeCredentials creds : traders) {
                executeAndNotify(creds, signal);
            }
        }
    }

    // ==================== INTERNAL ====================

    /**
     * Fetches a fresh TA signal for {@code coin}, bypassing the Caffeine cache.
     * Returns {@code null} and logs a warning if the Binance request fails.
     *
     * @param coin uppercase coin symbol (e.g. {@code "BTC"})
     * @return fresh signal result, or {@code null} if the request failed
     */
    private SignalResult fetchSignal(String coin) {
        try {
            return taSignalService.analyzeFresh(coin);
        } catch (Exception e) {
            log.warn("TradeScheduler: signal fetch failed for {}: {}", coin, e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to execute a trade for one user based on the given signal, then sends a
     * Telegram notification about the outcome.
     * <p>
     * If {@link TradeService#executeTrade} returns {@code null} (signal not actionable),
     * no notification is sent. Failures are reported to the user with a brief error message.
     *
     * @param creds  credentials and settings for the user
     * @param signal actionable TA signal (score ≥ +2 or ≤ -2)
     */
    private void executeAndNotify(UserExchangeCredentials creds, SignalResult signal) {
        long chatId = creds.getUser().getChatId();
        try {
            TradeOrder order = tradeService.executeTrade(creds, signal);
            if (order == null) return;

            String text = order.getStatus() == TradeStatus.FILLED
                    ? buildFilledNotification(order)
                    : buildFailedNotification(order);

            SendMessage msg = new SendMessage();
            msg.setChatId(String.valueOf(chatId));
            msg.setText(text);
            currencyBot.send(msg);

        } catch (Exception e) {
            log.error("TradeScheduler: unexpected error for chatId={} coin={}: {}",
                    chatId, signal.coin(), e.getMessage(), e);
        }
    }

    /**
     * Builds the user notification for a successfully filled order.
     *
     * @param order filled trade order with quantity and price populated
     * @return formatted notification text from {@link BotMessages#TRADE_ALERT_FILLED}
     */
    private String buildFilledNotification(TradeOrder order) {
        return BotMessages.TRADE_ALERT_FILLED
                .replace("{side}",     order.getSide().name())
                .replace("{coin}",     order.getCoin())
                .replace("{exchange}", order.getUser() != null ? "" : "")
                .replace("{quantity}", order.getQuantity() != null ? order.getQuantity().toPlainString() : "—")
                .replace("{price}",    order.getPrice()    != null ? order.getPrice().toPlainString()    : "—");
    }

    /**
     * Builds the user notification for a failed order.
     *
     * @param order failed trade order with error message populated
     * @return formatted notification text from {@link BotMessages#TRADE_ALERT_FAILED}
     */
    private String buildFailedNotification(TradeOrder order) {
        String reason = order.getErrorMessage() != null ? order.getErrorMessage() : "Неизвестная ошибка";
        return BotMessages.TRADE_ALERT_FAILED
                .replace("{coin}",   order.getCoin())
                .replace("{reason}", reason);
    }
}
