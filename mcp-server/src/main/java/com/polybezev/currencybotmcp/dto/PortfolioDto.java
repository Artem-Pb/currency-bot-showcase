package com.polybezev.currencybotmcp.dto;

import java.util.List;

/**
 * Mirrors currency-bot's {@code PortfolioResponse} for JSON deserialization only — mcp-server
 * has no compile-time dependency on the bot's classes (separate Maven project by design, see
 * {@code ai/CODE_REFERENCE.md}). Field names must stay in sync with the bot's DTO.
 */
public record PortfolioDto(
        long chatId,
        boolean exchangeConnected,
        String exchangeName,
        boolean tradingEnabled,
        List<TradeSummary> recentTrades
) {
    public record TradeSummary(
            String coin,
            String side,
            String quantity,
            String price,
            String status,
            String createdAt
    ) {}
}
