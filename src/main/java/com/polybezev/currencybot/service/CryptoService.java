package com.polybezev.currencybot.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.polybezev.currencybot.config.CacheConfig;
import com.polybezev.currencybot.model.CryptoPriceModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;

/**
 * Fetches real-time cryptocurrency prices from the CoinGecko public API.
 * Results are cached with a short TTL (see {@link CacheConfig#CACHE_CRYPTO}) to avoid
 * hammering the free-tier rate limit on every user request.
 */
@Service
@Slf4j
public class CryptoService {

    private static final String COINGECKO_URL =
            "https://api.coingecko.com/api/v3/simple/price?ids=%s&vs_currencies=rub,usd&include_24hr_change=true";

    /**
     * Maps a bot coin ticker to its CoinGecko API identifier. Same 12 coins as
     * {@link TaSignalService#supportedCoins()} plus {@code USDT} — the shared vocabulary for
     * everything that treats coins as a fixed catalogue (watchlist, autotrading price lookups).
     * Centralised here (rather than duplicated per caller) so the two stay in sync automatically.
     */
    private static final Map<String, String> COIN_TO_ID = Map.ofEntries(
            Map.entry("BTC",  "bitcoin"),
            Map.entry("ETH",  "ethereum"),
            Map.entry("SOL",  "solana"),
            Map.entry("BNB",  "binancecoin"),
            Map.entry("XRP",  "ripple"),
            Map.entry("DOGE", "dogecoin"),
            Map.entry("ADA",  "cardano"),
            Map.entry("AVAX", "avalanche-2"),
            Map.entry("DOT",  "polkadot"),
            Map.entry("LINK", "chainlink"),
            Map.entry("TON",  "the-open-network"),
            Map.entry("LTC",  "litecoin"),
            Map.entry("USDT", "tether")
    );

    /**
     * Resolves a bot coin ticker to its CoinGecko identifier for use with {@link #getCryptoPrice}.
     *
     * @param symbol uppercase (or any case) coin ticker, e.g. {@code "BTC"}
     * @return CoinGecko coin ID, e.g. {@code "bitcoin"}
     * @throws IllegalArgumentException if the ticker is not in the known map
     */
    public String coinGeckoId(String symbol) {
        String id = COIN_TO_ID.get(symbol.toUpperCase());
        if (id == null) throw new IllegalArgumentException("No CoinGecko ID for coin: " + symbol);
        return id;
    }

    /**
     * Returns the current price of a coin in RUB and USD, together with its 24-hour change.
     * <p>
     * Results are cached by {@code coinId} for {@link CacheConfig#CRYPTO_TTL_MINUTES} minutes.
     * The cache is named {@link CacheConfig#CACHE_CRYPTO}.
     *
     * @param coinId CoinGecko coin identifier (e.g. {@code "bitcoin"}, {@code "ethereum"})
     * @return price model with RUB/USD prices and 24h change percentage
     * @throws IOException if the CoinGecko API is unreachable or the response is malformed
     */
    @Cacheable(CacheConfig.CACHE_CRYPTO)
    public CryptoPriceModel getCryptoPrice(String coinId) throws IOException {
        URL url = new URL(String.format(COINGECKO_URL, coinId));

        JsonObject root = JsonParser.parseReader(
                new InputStreamReader(url.openStream())
        ).getAsJsonObject();

        JsonObject coin = root.getAsJsonObject(coinId);

        CryptoPriceModel model = new CryptoPriceModel();
        model.setPriceRub(coin.get("rub").getAsDouble());
        model.setPriceUsd(coin.get("usd").getAsDouble());
        model.setChange24h(coin.get("rub_24h_change").getAsDouble());

        return model;
    }
}
