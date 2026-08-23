package com.polybezev.currencybot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configures named Caffeine caches used throughout the application via {@code @Cacheable}.
 * <p>
 * Cache name constants ({@code CACHE_*}) are declared here so that services reference
 * {@code CacheConfig.CACHE_CURRENCY} instead of a raw string literal — if a cache is renamed,
 * only this file needs to change.
 * <p>
 * TTL values are chosen to balance freshness with external API rate limits:
 * <ul>
 *   <li>{@code currency} — CBR rates update once a day; 2 h is a safe short TTL for active users</li>
 *   <li>{@code currencyList} — the list of supported currencies rarely changes; 4 h is sufficient</li>
 *   <li>{@code crypto} — crypto prices are volatile; 3 min prevents hammering CoinGecko (30 req/min limit)</li>
 *   <li>{@code taSignal} — OHLC data updates every 4 h; 15 min avoids repeated 429s on rapid coin switches</li>
 * </ul>
 */
@Configuration
public class CacheConfig {

    /** Cache name for single-currency CBR rate lookups ({@code CurrencyService}). */
    public static final String CACHE_CURRENCY      = "currency";

    /** Cache name for the full CBR currency list ({@code CurrencyService}). */
    public static final String CACHE_CURRENCY_LIST = "currencyList";

    /** Cache name for CoinGecko crypto price data ({@code CryptoService}). */
    public static final String CACHE_CRYPTO        = "crypto";

    /** Cache name for TA signal analysis results ({@code TaSignalService}). */
    public static final String CACHE_TA_SIGNAL     = "taSignal";

    private static final int CURRENCY_TTL_HOURS      = 2;
    private static final int CURRENCY_LIST_TTL_HOURS = 4;
    private static final int CRYPTO_TTL_MINUTES      = 3;
    private static final int TA_SIGNAL_TTL_MINUTES   = 15;

    /**
     * Creates the application-wide {@link CacheManager} backed by Caffeine.
     * Each cache is registered individually so TTL can be tuned per data type.
     *
     * @return configured {@link CaffeineCacheManager}
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(CACHE_CURRENCY,
                Caffeine.newBuilder().expireAfterWrite(CURRENCY_TTL_HOURS, TimeUnit.HOURS).build());
        manager.registerCustomCache(CACHE_CURRENCY_LIST,
                Caffeine.newBuilder().expireAfterWrite(CURRENCY_LIST_TTL_HOURS, TimeUnit.HOURS).build());
        manager.registerCustomCache(CACHE_CRYPTO,
                Caffeine.newBuilder().expireAfterWrite(CRYPTO_TTL_MINUTES, TimeUnit.MINUTES).build());
        manager.registerCustomCache(CACHE_TA_SIGNAL,
                Caffeine.newBuilder().expireAfterWrite(TA_SIGNAL_TTL_MINUTES, TimeUnit.MINUTES).build());
        return manager;
    }
}
