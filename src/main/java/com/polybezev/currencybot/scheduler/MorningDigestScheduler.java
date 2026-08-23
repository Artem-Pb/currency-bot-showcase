package com.polybezev.currencybot.scheduler;

import com.polybezev.currencybot.bot.CurrencyBot;
import com.polybezev.currencybot.entity.UserSubscription;
import com.polybezev.currencybot.repository.UserSubscriptionRepository;
import com.polybezev.currencybot.service.AiAnalysisService;
import com.polybezev.currencybot.service.MarketDataService;
import com.polybezev.currencybot.service.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;

/**
 * Sends the daily morning market digest to all active paid subscribers at 08:00 Moscow time.
 * <p>
 * The digest is generated once and broadcast to every eligible subscriber in a single loop —
 * market data and the AI response are not re-fetched per user.
 * <p>
 * Architecture note: this class depends directly on {@link CurrencyBot} to send messages.
 * If the sending mechanism ever needs to change (e.g. batching, rate-limiting), extract a
 * {@code MessageSender} interface and inject it here instead of the bot directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MorningDigestScheduler {

    private final UserSubscriptionRepository subscriptionRepository;
    private final MarketDataService marketDataService;
    private final NewsService newsService;
    private final AiAnalysisService aiAnalysisService;
    private final CurrencyBot currencyBot;

    /**
     * Fetches market data, news and an AI-generated digest, then sends the result
     * to every active paid subscriber (TIER 1, TIER 2, TIER 3).
     * <p>
     * Triggered daily at 08:00 Moscow time ({@code "Europe/Moscow"}).
     * If any step fails the entire run is aborted and the error is logged —
     * subscribers receive nothing rather than a partial or incorrect digest.
     * <p>
     * Also called manually via the admin panel ({@code /digest} slash command).
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Moscow")
    public void sendMorningDigest() {
        List<UserSubscription> subscribers = subscriptionRepository.findAllActivePaid();
        log.info("Starting morning digest for {} subscribers", subscribers.size());
        if (subscribers.isEmpty()) return;

        try {
            String marketData = marketDataService.getMarketSnapshot();
            String news       = newsService.getTopNews();
            String digest     = aiAnalysisService.generateMorningDigest(marketData, news);

            for (UserSubscription sub : subscribers) {
                long chatId = sub.getUser().getChatId();
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                message.setText(digest);
                currencyBot.send(message);
                log.info("Morning digest sent to user {}", chatId);
            }
        } catch (Exception e) {
            log.error("Morning digest failed: {}", e.getMessage(), e);
        }
    }
}
