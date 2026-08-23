package com.polybezev.currencybot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configures shared HTTP infrastructure used across the application.
 * <p>
 * {@link RestTemplate} is a singleton bean injected into any service that calls external HTTP APIs
 * (CoinGecko, CBR, CryptoPanic, Binance OHLCV). Keeping it here — rather than inside a
 * domain-specific config like {@link AiConfig} — makes the dependency visible and reusable.
 */
@Configuration
public class WebConfig {

    /**
     * Creates the shared {@link RestTemplate} for all outbound HTTP calls.
     * A single instance is safe to share across services — {@link RestTemplate} is thread-safe.
     *
     * @return default {@link RestTemplate} with no custom interceptors
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
