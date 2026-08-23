package com.polybezev.currencybot.service;

import com.polybezev.currencybot.dto.PortfolioResponse;
import com.polybezev.currencybot.entity.TradeOrder;
import com.polybezev.currencybot.entity.User;
import com.polybezev.currencybot.entity.UserExchangeCredentials;
import com.polybezev.currencybot.model.SupportedExchange;
import com.polybezev.currencybot.model.TradeSide;
import com.polybezev.currencybot.model.TradeStatus;
import com.polybezev.currencybot.repository.TradeOrderRepository;
import com.polybezev.currencybot.repository.UserExchangeCredentialsRepository;
import com.polybezev.currencybot.repository.UserRepository;
import com.polybezev.currencybot.service.TaSignalService.SignalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the full automated trading lifecycle: credential management,
 * trade decisions, order execution, and history retrieval.
 * <p>
 * <b>Credential flow:</b>
 * <ol>
 *   <li>User provides API key + secret via the bot setup FSM</li>
 *   <li>{@link #saveCredentials} encrypts both values via {@link EncryptionService} and persists them</li>
 *   <li>{@link TradeScheduler} calls {@link #executeTrade} which decrypts on-demand and discards plaintext immediately</li>
 * </ol>
 * <p>
 * <b>Trade decision logic:</b> a BUY is placed when signal score ≥ +2 and a SELL when score ≤ -2.
 * HOLD and weak signals are ignored. The trade amount is computed as
 * {@code usdtBalance × maxTradePercent / 100}.
 * <p>
 * Every execution attempt — successful or not — produces a {@link TradeOrder} row so that
 * users can review the full history including failures.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeService {

    private final UserRepository userRepository;
    private final UserExchangeCredentialsRepository credentialsRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final EncryptionService encryptionService;
    private final ExchangeService exchangeService;
    private final CryptoService cryptoService;

    // ==================== CREDENTIAL MANAGEMENT ====================

    /**
     * Encrypts and saves exchange API credentials for a user.
     * <p>
     * If the user already has credentials saved, they are replaced — the old row is deleted
     * and a new one is inserted. Trading is disabled by default on new credentials;
     * the user must explicitly enable it after setup.
     *
     * @param chatId    Telegram chat ID of the user
     * @param exchange  exchange these credentials belong to
     * @param apiKey    plaintext API key entered by the user; encrypted before persistence
     * @param apiSecret plaintext API secret entered by the user; encrypted before persistence
     */
    @Transactional
    public void saveCredentials(long chatId, SupportedExchange exchange, String apiKey, String apiSecret) {
        User user = getUser(chatId);

        credentialsRepository.findByUser(user).ifPresent(credentialsRepository::delete);

        UserExchangeCredentials creds = new UserExchangeCredentials();
        creds.setUser(user);
        creds.setExchange(exchange);
        creds.setEncryptedApiKey(encryptionService.encrypt(apiKey));
        creds.setEncryptedSecret(encryptionService.encrypt(apiSecret));
        creds.setCreatedAt(LocalDateTime.now());
        credentialsRepository.save(creds);

        log.info("Exchange credentials saved: chatId={} exchange={}", chatId, exchange);
    }

    /**
     * Returns the saved credentials for a user, if any.
     *
     * @param chatId Telegram chat ID
     * @return credentials wrapped in {@link Optional}, or empty if the user has not connected an exchange
     */
    public Optional<UserExchangeCredentials> getCredentials(long chatId) {
        return userRepository.findByChatId(chatId)
                .flatMap(credentialsRepository::findByUser);
    }

    /**
     * Deletes the user's exchange credentials and disables trading.
     * No-op if the user has no credentials saved.
     *
     * @param chatId Telegram chat ID
     */
    @Transactional
    public void deleteCredentials(long chatId) {
        userRepository.findByChatId(chatId)
                .flatMap(credentialsRepository::findByUser)
                .ifPresent(creds -> {
                    credentialsRepository.delete(creds);
                    log.info("Exchange credentials deleted: chatId={}", chatId);
                });
    }

    /**
     * Enables or disables automated trading for a user.
     * Requires credentials to be saved first — if none exist this method is a no-op.
     *
     * @param chatId  Telegram chat ID
     * @param enabled {@code true} to activate trading, {@code false} to pause it
     */
    @Transactional
    public void setTradingEnabled(long chatId, boolean enabled) {
        userRepository.findByChatId(chatId)
                .flatMap(credentialsRepository::findByUser)
                .ifPresent(creds -> {
                    creds.setTradingEnabled(enabled);
                    credentialsRepository.save(creds);
                    log.info("Trading {} for chatId={}", enabled ? "enabled" : "disabled", chatId);
                });
    }

    // ==================== TRADE EXECUTION ====================

    /**
     * Evaluates a TA signal and, if actionable, places a market order on the user's exchange.
     * <p>
     * Decision rules:
     * <ul>
     *   <li>score ≥ +2 → {@link TradeSide#BUY}</li>
     *   <li>score ≤ -2 → {@link TradeSide#SELL}</li>
     *   <li>anything else → no trade, method returns {@code null}</li>
     * </ul>
     * API keys are decrypted immediately before the exchange call and not retained afterwards.
     * The result (including failures) is persisted as a {@link TradeOrder} row.
     *
     * @param creds  pre-fetched credentials for the user; must not be {@code null}
     * @param signal fresh TA signal result from {@link TaSignalService#analyzeFresh}
     * @return saved {@link TradeOrder} if a trade was attempted, or {@code null} if the signal was not actionable
     */
    @Transactional
    public TradeOrder executeTrade(UserExchangeCredentials creds, SignalResult signal) {
        if (signal.score() > -2 && signal.score() < 2) return null;

        TradeSide side = signal.score() >= 2 ? TradeSide.BUY : TradeSide.SELL;
        TradeOrder order = buildPendingOrder(creds.getUser(), signal.coin(), side);

        try {
            String apiKey    = encryptionService.decrypt(creds.getEncryptedApiKey());
            String apiSecret = encryptionService.decrypt(creds.getEncryptedSecret());

            BigDecimal balance = exchangeService.getUsdtBalance(creds.getExchange(), apiKey, apiSecret);
            BigDecimal usdtAmount = balance.multiply(
                    BigDecimal.valueOf(creds.getMaxTradePercent()).divide(BigDecimal.valueOf(100)));

            BigDecimal currentPrice = BigDecimal.valueOf(
                    cryptoService.getCryptoPrice(cryptoService.coinGeckoId(signal.coin())).getPriceUsd());

            ExchangeService.OrderResult result = exchangeService.placeMarketOrder(
                    creds.getExchange(), apiKey, apiSecret,
                    signal.coin(), side, usdtAmount, currentPrice);

            order.setExchangeOrderId(result.orderId());
            order.setQuantity(result.quantity());
            order.setPrice(result.price());
            order.setStatus(TradeStatus.FILLED);

        } catch (Exception e) {
            log.error("Trade execution failed for chatId={} coin={}: {}", creds.getUser().getChatId(), signal.coin(), e.getMessage(), e);
            order.setStatus(TradeStatus.FAILED);
            order.setErrorMessage(truncate(e.getMessage(), 500));
        }

        return tradeOrderRepository.save(order);
    }

    /**
     * Returns the user's currently held non-zero coin balances on their connected exchange.
     * Used for phase 7's news-impact matching — decrypts credentials on demand exactly like
     * {@link #executeTrade}, discards them immediately after the call.
     *
     * @param chatId Telegram chat ID
     * @return open positions, or an empty list if the user has no exchange connected or the
     *         live wallet read fails (network/auth error — logged, not propagated, since this
     *         is a best-effort enrichment for AI commentary, not a critical path)
     */
    public List<ExchangeService.Position> getOpenPositions(long chatId) {
        Optional<UserExchangeCredentials> creds = getCredentials(chatId);
        if (creds.isEmpty()) return List.of();
        try {
            String apiKey    = encryptionService.decrypt(creds.get().getEncryptedApiKey());
            String apiSecret = encryptionService.decrypt(creds.get().getEncryptedSecret());
            return exchangeService.getOpenPositions(creds.get().getExchange(), apiKey, apiSecret);
        } catch (Exception e) {
            log.warn("Failed to fetch open positions for chatId={}: {}", chatId, e.getMessage());
            return List.of();
        }
    }

    // ==================== HISTORY ====================

    /**
     * Returns the last {@code limit} trade orders for the given user, newest first.
     *
     * @param chatId Telegram chat ID
     * @param limit  maximum number of orders to return
     * @return list of trade orders, may be empty if the user has never traded
     */
    public List<TradeOrder> getHistory(long chatId, int limit) {
        return userRepository.findByChatId(chatId)
                .map(user -> tradeOrderRepository.findByUserOrderByCreatedAtDesc(
                        user, PageRequest.of(0, limit)))
                .orElseGet(List::of);
    }

    /**
     * Maximum number of trade orders included in {@link #getPortfolioSummary}.
     * Matches the limit shown on the bot's own trade history screen ({@code TradeHandler}).
     */
    private static final int PORTFOLIO_HISTORY_LIMIT = 10;

    /**
     * Builds a read-only snapshot of a user's trading state for the {@code get_user_portfolio}
     * MCP tool: exchange connection status, trading toggle, and recent order history.
     * <p>
     * Never decrypts or exposes API credentials — only metadata already safe to display
     * in the bot's own UI.
     *
     * @param chatId Telegram chat ID
     * @return portfolio snapshot; {@code exchangeConnected} is {@code false} and
     *         {@code exchangeName} is {@code null} if the user never connected an exchange
     */
    public PortfolioResponse getPortfolioSummary(long chatId) {
        Optional<UserExchangeCredentials> creds = getCredentials(chatId);
        List<PortfolioResponse.TradeSummary> recentTrades = getHistory(chatId, PORTFOLIO_HISTORY_LIMIT)
                .stream()
                .map(o -> new PortfolioResponse.TradeSummary(
                        o.getCoin(),
                        o.getSide().name(),
                        o.getQuantity() != null ? o.getQuantity().toPlainString() : null,
                        o.getPrice() != null ? o.getPrice().toPlainString() : null,
                        o.getStatus().name(),
                        o.getCreatedAt().toString()))
                .toList();

        return creds
                .map(c -> new PortfolioResponse(chatId, true, c.getExchange().displayName,
                        c.isTradingEnabled(), recentTrades))
                .orElseGet(() -> new PortfolioResponse(chatId, false, null, false, recentTrades));
    }

    // ==================== HELPERS ====================

    /**
     * Creates a new {@link TradeOrder} with status unset, ready for execution.
     * The {@code createdAt} timestamp is set to now.
     *
     * @param user the trading user
     * @param coin coin symbol being traded
     * @param side BUY or SELL
     * @return unsaved order entity
     */
    private TradeOrder buildPendingOrder(User user, String coin, TradeSide side) {
        TradeOrder order = new TradeOrder();
        order.setUser(user);
        order.setCoin(coin);
        order.setSide(side);
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }

    /**
     * Returns the existing {@link User} entity for a chat ID.
     *
     * @param chatId Telegram chat ID
     * @return user entity
     * @throws IllegalStateException if no user record exists for this chat ID
     */
    private User getUser(long chatId) {
        return userRepository.findByChatId(chatId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + chatId));
    }

    /**
     * Truncates a string to at most {@code max} characters to fit within a database column.
     *
     * @param s   source string, may be {@code null}
     * @param max maximum length
     * @return truncated string, or {@code null} if the input was {@code null}
     */
    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
