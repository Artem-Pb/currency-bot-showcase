package com.polybezev.currencybotmcp.config;

import com.polybezev.currencybotmcp.service.MarketDataService;
import com.polybezev.currencybotmcp.service.PortfolioService;
import com.polybezev.currencybotmcp.service.RssNewsService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@code @Tool}-annotated service methods with Spring AI's MCP server
 * auto-configuration. New tools get added here via {@link MethodToolCallbackProvider},
 * one bean per {@code @Service} — see {@code ai/CODE_REFERENCE.md} for the full tool list.
 */
@Configuration(proxyBeanMethods = false)
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider newsTools(RssNewsService rssNewsService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(rssNewsService)
                .build();
    }

    @Bean
    public ToolCallbackProvider marketDataTools(MarketDataService marketDataService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(marketDataService)
                .build();
    }

    @Bean
    public ToolCallbackProvider portfolioTools(PortfolioService portfolioService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(portfolioService)
                .build();
    }
}
