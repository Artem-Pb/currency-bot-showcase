package com.polybezev.currencybot.service;

import com.polybezev.currencybot.model.SupportedExchange;
import com.polybezev.currencybot.model.TradeSide;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Thin wrapper around the XChange library for placing market orders on supported exchanges.
 * <p>
 * Each public method receives decrypted API credentials and is stateless — no exchange
 * connection is kept alive between calls. A new {@link Exchange} instance is created per
 * operation and discarded afterwards, which is safe but slightly slower than connection pooling.
 * For the current usage volume (a handful of users, a few trades per day) this is acceptable.
 * <p>
 * <b>Security note:</b> API keys are decrypted immediately before use and never stored in
 * fields of this class or logged at any level. The {@link EncryptionService} decryption
 * happens in {@code TradeService}, not here — this service receives plaintext keys only.
 * <p>
 * <b>Supported exchanges:</b> {@link SupportedExchange#BINANCE} and {@link SupportedExchange#BYBIT}.
 * Both are routed through the same XChange interface; only the {@link ExchangeSpecification}
 * differs between them.
 */
@Service
@Slf4j
public class ExchangeService {

    /**
     * Result of a market order placement attempt.
     *
     * @param orderId  exchange-assigned order ID; {@code null} if placement failed
     * @param quantity amount of base asset transacted (e.g. BTC)
     * @param price    estimated fill price in USDT; may be {@code null} for market orders
     *                 where the exchange does not return an immediate fill price
     */
    public record OrderResult(String orderId, BigDecimal quantity, BigDecimal price) {}

    // ==================== BALANCE ====================

    /**
     * Returns the available USDT balance for the given credentials.
     * Used by {@code TradeService} to calculate the trade amount before placing an order.
     *
     * @param exchange   exchange to connect to
     * @param apiKey     plaintext API key (already decrypted by the caller)
     * @param apiSecret  plaintext API secret (already decrypted by the caller)
     * @return available USDT balance, or {@link BigDecimal#ZERO} if the wallet is empty
     * @throws Exception if the exchange rejects the credentials or the request fails
     */
    public BigDecimal getUsdtBalance(SupportedExchange exchange, String apiKey, String apiSecret) throws Exception {
        Exchange ex = connect(exchange, apiKey, apiSecret);
        return ex.getAccountService()
                .getAccountInfo()
                .getWallet()
                .getBalance(Currency.USDT)
                .getAvailable();
    }

    /**
     * A single non-zero coin balance currently held on the exchange.
     *
     * @param coin      uppercase coin symbol (e.g. {@code "BTC"})
     * @param available available balance (excludes amounts locked in open orders)
     */
    public record Position(String coin, BigDecimal available) {}

    /**
     * Returns all non-zero coin balances on the exchange, excluding USDT (the quote currency,
     * not a "position").
     * <p>
     * {@link com.polybezev.currencybot.entity.TradeOrder} only records order history, not
     * current holdings, so this live wallet read is the only way to know what a TIER 3 user is
     * actually still holding — needed for phase 7's news-impact matching (an AI comment on a
     * news item is only "about your position" if you currently hold that coin).
     *
     * @param exchange   exchange to connect to
     * @param apiKey     plaintext API key (already decrypted by the caller)
     * @param apiSecret  plaintext API secret (already decrypted by the caller)
     * @return non-zero balances excluding USDT; empty if the wallet holds only USDT or nothing
     * @throws Exception if the exchange rejects the credentials or the request fails
     */
    public List<Position> getOpenPositions(SupportedExchange exchange, String apiKey, String apiSecret) throws Exception {
        Exchange ex = connect(exchange, apiKey, apiSecret);
        return ex.getAccountService().getAccountInfo().getWallet().getBalances().values().stream()
                .filter(balance -> !balance.getCurrency().equals(Currency.USDT))
                .filter(balance -> balance.getAvailable().compareTo(BigDecimal.ZERO) > 0)
                .map(balance -> new Position(balance.getCurrency().getCurrencyCode(), balance.getAvailable()))
                .toList();
    }

    // ==================== ORDERS ====================

    /**
     * Places a market order for the given coin on the specified exchange.
     * <p>
     * For {@link TradeSide#BUY}: spends {@code usdtAmount} USDT to acquire the base asset
     * at the current market price. The quantity is computed as {@code usdtAmount / currentPrice},
     * rounded down to 6 decimal places to stay within exchange lot-size rules.
     * <p>
     * For {@link TradeSide#SELL}: sells the entire computed quantity back to USDT.
     * In a real implementation the quantity should come from the open position rather than
     * being re-derived from the current price — this simplified version is sufficient for MVP.
     *
     * @param exchange    exchange to trade on
     * @param apiKey      plaintext API key
     * @param apiSecret   plaintext API secret
     * @param coin        uppercase coin symbol (e.g. {@code "BTC"}, {@code "ETH"})
     * @param side        direction — BUY or SELL
     * @param usdtAmount  USDT value to spend (BUY) or approximate value to sell (SELL)
     * @param currentPrice current market price in USDT, used to convert amount to base-asset quantity
     * @return order result with the exchange order ID and executed quantity
     * @throws Exception if the exchange rejects the order or the connection fails
     */
    public OrderResult placeMarketOrder(SupportedExchange exchange, String apiKey, String apiSecret,
                                        String coin, TradeSide side,
                                        BigDecimal usdtAmount, BigDecimal currentPrice) throws Exception {
        Exchange ex = connect(exchange, apiKey, apiSecret);

        CurrencyPair pair = new CurrencyPair(coin, "USDT");
        BigDecimal quantity = usdtAmount.divide(currentPrice, 6, RoundingMode.DOWN);

        Order.OrderType type = side == TradeSide.BUY ? Order.OrderType.BID : Order.OrderType.ASK;
        MarketOrder order = new MarketOrder.Builder(type, pair)
                .originalAmount(quantity)
                .build();

        String orderId = ex.getTradeService().placeMarketOrder(order);
        log.info("[Trade] {} {} {} qty={} price≈{} orderId={}",
                exchange, side, coin, quantity, currentPrice, orderId);

        return new OrderResult(orderId, quantity, currentPrice);
    }

    // ==================== HELPERS ====================

    /**
     * Creates and returns a connected XChange {@link Exchange} instance for the given credentials.
     * The returned instance is not cached — a new connection is established per call.
     *
     * @param exchange  target exchange
     * @param apiKey    plaintext API key
     * @param apiSecret plaintext API secret
     * @return authenticated exchange instance ready for account and trade service calls
     */
    private Exchange connect(SupportedExchange exchange, String apiKey, String apiSecret) {
        ExchangeSpecification spec = switch (exchange) {
            case BINANCE -> new BinanceExchange().getDefaultExchangeSpecification();
            case BYBIT   -> new BybitExchange().getDefaultExchangeSpecification();
        };
        spec.setApiKey(apiKey);
        spec.setSecretKey(apiSecret);
        return ExchangeFactory.INSTANCE.createExchange(spec);
    }
}
