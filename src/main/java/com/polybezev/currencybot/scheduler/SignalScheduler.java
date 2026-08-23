package com.polybezev.currencybot.scheduler;

import com.polybezev.currencybot.bot.CurrencyBot;
import com.polybezev.currencybot.entity.UserSubscription;
import com.polybezev.currencybot.formatter.MessageFormatter;
import com.polybezev.currencybot.repository.UserSubscriptionRepository;
import com.polybezev.currencybot.service.AiAnalysisService;
import com.polybezev.currencybot.service.TaSignalService;
import com.polybezev.currencybot.service.TaSignalService.SignalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors TA signals for all supported coins every 4 hours and pushes change alerts
 * to TIER 2+ subscribers when a coin's signal flips to an actionable value.
 * <p>
 * <b>Change detection:</b> the last known signal per coin is kept in {@link #lastSignals}.
 * An alert is sent only when the new signal differs from the stored one AND the new signal
 * is not {@code ⚪ HOLD} (score ≠ 0). Neutral-to-neutral transitions are silently ignored.
 * <p>
 * <b>Cold start:</b> on the first run after application startup {@link #lastSignals} is empty.
 * The scheduler populates the map with current signals without sending any alerts, so users
 * are not flooded on every redeploy.
 * <p>
 * <b>Data freshness:</b> {@link TaSignalService#analyzeFresh(String)} is used instead of
 * {@link TaSignalService#analyzeBySymbol(String)} to bypass the 15-minute Caffeine cache
 * and always fetch the latest Binance klines.
 * <p>
 * <b>Expiry guard:</b> subscribers whose subscription has already expired (but whose
 * {@code active} flag was not yet flipped) are filtered out via {@code expiresAt} check.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SignalScheduler {

    private final TaSignalService taSignalService;
    private final UserSubscriptionRepository subscriptionRepository;
    private final AiAnalysisService aiAnalysisService;
    private final MessageFormatter formatter;
    private final CurrencyBot currencyBot;

    /** Last observed signal string per coin symbol (e.g. {@code "BTC" → "🟢 BUY"}). */
    private final ConcurrentHashMap<String, String> lastSignals = new ConcurrentHashMap<>();

    // ==================== SCHEDULER ====================

    /**
     * Runs every 4 hours (00:00, 04:00, 08:00, 12:00, 16:00, 20:00 Moscow time).
     * <p>
     * On cold start: populates {@link #lastSignals} without alerting.
     * On subsequent runs: compares fresh signals against stored ones and broadcasts
     * actionable changes to all active TIER 2+ subscribers.
     */
    @Scheduled(cron = "0 0 */4 * * *", zone = "Europe/Moscow")
    public void checkSignals() {
        Set<String> coins = taSignalService.supportedCoins();

        if (lastSignals.isEmpty()) {
            initColdStart(coins);
            return;
        }

        List<UserSubscription> subscribers = subscriptionRepository.findAllActiveTier2Plus()
                .stream()
                .filter(sub -> sub.getExpiresAt().isAfter(LocalDateTime.now()))
                .toList();

        log.info("SignalScheduler run: {} coins, {} TIER2+ subscribers", coins.size(), subscribers.size());

        if (subscribers.isEmpty()) return;

        for (String coin : coins) {
            processOneCoin(coin, subscribers);
        }
    }

    // ==================== INTERNAL ====================

    /**
     * First run after startup: fetches current signals for all coins and stores them
     * without sending alerts. This prevents a mass notification on every redeploy.
     * <p>
     * Individual coin failures are logged as warnings and do not abort the loop —
     * the remaining coins are still initialised.
     *
     * @param coins set of uppercase coin symbols returned by {@link TaSignalService#supportedCoins()}
     */
    private void initColdStart(Set<String> coins) {
        log.info("SignalScheduler cold start — initialising signal map for {} coins, no alerts sent", coins.size());
        for (String coin : coins) {
            try {
                SignalResult r = taSignalService.analyzeFresh(coin);
                lastSignals.put(coin, r.signal());
            } catch (Exception e) {
                log.warn("Cold start analysis failed for {}: {}", coin, e.getMessage());
            }
        }
    }

    /**
     * Fetches a fresh signal for {@code coin}, compares it with the stored value,
     * and — if the signal changed to something actionable — sends alerts to all {@code subscribers}.
     * <p>
     * {@link #lastSignals} is updated with the new signal regardless of whether an alert was sent,
     * so subsequent runs always compare against the most recent observed state.
     * <p>
     * If the Binance request fails, the stored signal is left unchanged and the error is logged
     * without re-throwing — other coins in the same run are not affected.
     * AI failure is handled by {@link AiAnalysisService#generateSignalExplanation} returning
     * {@code null}, so the alert is still sent without the explanation.
     *
     * @param coin        uppercase coin symbol (e.g. {@code "BTC"})
     * @param subscribers active TIER 2+ subscribers; list is pre-fetched once per scheduler run
     */
    private void processOneCoin(String coin, List<UserSubscription> subscribers) {
        try {
            SignalResult result = taSignalService.analyzeFresh(coin);
            String prevSignal = lastSignals.getOrDefault(coin, "");
            String newSignal  = result.signal();

            lastSignals.put(coin, newSignal);

            if (newSignal.equals(prevSignal) || result.score() == 0) return;

            log.info("Signal change detected for {}: [{}] → [{}]", coin, prevSignal, newSignal);

            String aiExplanation = aiAnalysisService.generateSignalExplanation(result);
            String card = formatter.buildSignalChangeCard(result, prevSignal, aiExplanation);

            for (UserSubscription sub : subscribers) {
                SendMessage msg = new SendMessage();
                msg.setChatId(String.valueOf(sub.getUser().getChatId()));
                msg.setText(card);
                msg.setParseMode("Markdown");
                currencyBot.send(msg);
            }

            log.info("Signal alert sent for {} to {} subscribers", coin, subscribers.size());

        } catch (Exception e) {
            log.error("SignalScheduler failed for {}: {}", coin, e.getMessage(), e);
        }
    }
}
