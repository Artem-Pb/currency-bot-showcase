package com.polybezev.currencybotmcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Shared HTTP client infrastructure for MCP tools that call external services
 * (alternative.me, CoinGecko, currency-bot's internal API) — mirrors {@code WebConfig}
 * in the currency-bot project.
 */
@Configuration
public class RestClientConfig {

    /**
     * Single shared {@link RestTemplate} instance — thread-safe, reusable across all tool services.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}