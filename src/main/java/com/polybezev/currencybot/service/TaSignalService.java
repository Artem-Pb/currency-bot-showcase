package com.polybezev.currencybot.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.polybezev.currencybot.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Calculates TA signals for crypto coins using RSI, MACD, and EMA trend indicators.
 * <p>
 * Data source: Binance public klines API — 1-hour candles, last 200 bars (~8 days).
 * No API key required.
 * <p>
 * Each indicator contributes +1 (bullish), 0 (neutral), or -1 (bearish) to a composite score:
 * <ul>
 *   <li>RSI(14) &lt; 40 → oversold (+1); &gt; 60 → overbought (-1)</li>
 *   <li>MACD(12,26) above signal line EMA(9) → bullish (+1)</li>
 *   <li>EMA(20) above EMA(50) → uptrend (+1)</li>
 * </ul>
 * Final verdict: BUY ≥ +2 · WEAK BUY +1 · HOLD 0 · WEAK SELL -1 · SELL ≤ -2.
 * <p>
 * Results are cached per symbol for {@link CacheConfig#CACHE_TA_SIGNAL} (15 min TTL).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaSignalService {

    private final RestTemplate restTemplate;

    /** Binance klines: 1-hour candles, last 200 bars ≈ 8 days of history. */
    private static final String KLINES_URL =
            "https://api.binance.com/api/v3/klines?symbol=%s&interval=1h&limit=200";

    /** Maps bot coin symbol → Binance USDT trading pair. */
    private static final Map<String, String> COIN_SYMBOLS = Map.ofEntries(
            Map.entry("BTC",  "BTCUSDT"),
            Map.entry("ETH",  "ETHUSDT"),
            Map.entry("SOL",  "SOLUSDT"),
            Map.entry("BNB",  "BNBUSDT"),
            Map.entry("XRP",  "XRPUSDT"),
            Map.entry("DOGE", "DOGEUSDT"),
            Map.entry("ADA",  "ADAUSDT"),
            Map.entry("AVAX", "AVAXUSDT"),
            Map.entry("DOT",  "DOTUSDT"),
            Map.entry("LINK", "LINKUSDT"),
            Map.entry("TON",  "TONUSDT"),
            Map.entry("LTC",  "LTCUSDT")
    );

    /**
     * Holds the result of a TA analysis for a single coin.
     *
     * @param coin       display symbol (e.g. {@code "BTC"})
     * @param rsi        RSI(14) value at the last bar
     * @param macd       MACD(12,26) value at the last bar
     * @param macdSignal MACD signal line EMA(9) at the last bar
     * @param ema20      EMA(20) of close price at the last bar
     * @param ema50      EMA(50) of close price at the last bar
     * @param score      composite score in range [-3, +3]
     * @param signal     human-readable verdict (e.g. {@code "🟢 BUY"})
     */
    public record SignalResult(
            String coin,
            double rsi,
            double macd,
            double macdSignal,
            double ema20,
            double ema50,
            int    score,
            String signal
    ) {}

    /**
     * Returns the set of coin symbols supported by this service.
     *
     * @return unmodifiable set of uppercase coin symbols
     */
    public Set<String> supportedCoins() {
        return COIN_SYMBOLS.keySet();
    }

    /**
     * Looks up the Binance trading pair for {@code symbol} and runs a full TA analysis.
     * Results are cached for 15 minutes (cache: {@link CacheConfig#CACHE_TA_SIGNAL}).
     *
     * @param symbol uppercase coin symbol (e.g. {@code "BTC"})
     * @return signal result with all indicator values and composite verdict
     * @throws IllegalArgumentException if the symbol is not in the supported coin list
     */
    @Cacheable(CacheConfig.CACHE_TA_SIGNAL)
    public SignalResult analyzeBySymbol(String symbol) {
        String pair = COIN_SYMBOLS.get(symbol.toUpperCase());
        if (pair == null) throw new IllegalArgumentException("Unknown coin: " + symbol);
        return analyze(pair, symbol.toUpperCase());
    }

    /**
     * Same as {@link #analyzeBySymbol} but bypasses the Caffeine cache.
     * Used by {@code SignalScheduler} to always fetch fresh Binance data regardless of TTL.
     *
     * @param symbol uppercase coin symbol (e.g. {@code "BTC"})
     * @return fresh signal result, never served from cache
     * @throws IllegalArgumentException if the symbol is not in the supported coin list
     */
    public SignalResult analyzeFresh(String symbol) {
        String pair = COIN_SYMBOLS.get(symbol.toUpperCase());
        if (pair == null) throw new IllegalArgumentException("Unknown coin: " + symbol);
        return analyze(pair, symbol.toUpperCase());
    }

    /**
     * Fetches 200 × 1h candles from Binance and computes RSI(14), MACD(12,26,9),
     * EMA(20), and EMA(50). Combines them into a composite score and a signal verdict.
     *
     * @param pair     Binance trading pair (e.g. {@code "BTCUSDT"})
     * @param coinName display symbol used in the result (e.g. {@code "BTC"})
     * @return full signal result
     */
    public SignalResult analyze(String pair, String coinName) {
        String json = restTemplate.getForObject(String.format(KLINES_URL, pair), String.class);

        JsonArray klines = JsonParser.parseString(json).getAsJsonArray();
        BaseBarSeries series = new BaseBarSeries(pair);

        for (var el : klines) {
            JsonArray bar = el.getAsJsonArray();
            long   timestamp = bar.get(0).getAsLong();
            double open      = Double.parseDouble(bar.get(1).getAsString());
            double high      = Double.parseDouble(bar.get(2).getAsString());
            double low       = Double.parseDouble(bar.get(3).getAsString());
            double close     = Double.parseDouble(bar.get(4).getAsString());

            ZonedDateTime time = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC);
            series.addBar(BaseBar.builder()
                    .timePeriod(Duration.ofHours(1))
                    .endTime(time)
                    .openPrice(series.numOf(open))
                    .highPrice(series.numOf(high))
                    .lowPrice(series.numOf(low))
                    .closePrice(series.numOf(close))
                    .volume(series.numOf(0))
                    .build());
        }

        int last = series.getEndIndex();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        RSIIndicator  rsiInd     = new RSIIndicator(closePrice, 14);
        MACDIndicator macdInd    = new MACDIndicator(closePrice, 12, 26);
        EMAIndicator  signalLine = new EMAIndicator(macdInd, 9);
        EMAIndicator  ema20Ind   = new EMAIndicator(closePrice, 20);
        EMAIndicator  ema50Ind   = new EMAIndicator(closePrice, 50);

        double rsi      = rsiInd.getValue(last).doubleValue();
        double macd     = macdInd.getValue(last).doubleValue();
        double macdSig  = signalLine.getValue(last).doubleValue();
        double ema20Val = ema20Ind.getValue(last).doubleValue();
        double ema50Val = ema50Ind.getValue(last).doubleValue();

        int rsiScore  = scoreRsi(rsi);
        int macdScore = scoreMacd(macd, macdSig);
        int emaScore  = scoreEma(ema20Val, ema50Val);
        int score     = rsiScore + macdScore + emaScore;

        log.info("[TA] {} bars={} | RSI={} score={} | MACD={} sig={} score={} | EMA20={} EMA50={} score={} | TOTAL={} → {}",
                coinName, series.getBarCount(),
                String.format("%.2f", rsi),      rsiScore,
                String.format("%.4f", macd),     String.format("%.4f", macdSig), macdScore,
                String.format("%.2f", ema20Val), String.format("%.2f", ema50Val), emaScore,
                score, scoreToSignal(score));

        String signal = scoreToSignal(score);
        return new SignalResult(coinName, rsi, macd, macdSig, ema20Val, ema50Val, score, signal);
    }

    // ==================== SCORING ====================

    /** RSI &lt; 40 → oversold (+1), RSI &gt; 60 → overbought (-1), else neutral (0). */
    private int scoreRsi(double rsi) {
        if (rsi < 40) return  1;
        if (rsi > 60) return -1;
        return 0;
    }

    /** MACD above signal line → bullish (+1), below → bearish (-1). */
    private int scoreMacd(double macd, double signal) {
        if (macd > signal) return  1;
        if (macd < signal) return -1;
        return 0;
    }

    /** EMA20 above EMA50 → uptrend (+1), below → downtrend (-1). */
    private int scoreEma(double ema20, double ema50) {
        if (ema20 > ema50) return  1;
        if (ema20 < ema50) return -1;
        return 0;
    }

    private String scoreToSignal(int score) {
        if (score >= 2)  return "🟢 BUY";
        if (score == 1)  return "🟡 WEAK BUY";
        if (score == 0)  return "⚪ HOLD";
        if (score == -1) return "🟡 WEAK SELL";
        return "🔴 SELL";
    }
}
