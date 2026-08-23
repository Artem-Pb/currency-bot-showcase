package com.polybezev.currencybot.dto;

import java.util.List;

/**
 * Read-only snapshot of a user's trading state, served by {@code InternalPortfolioController}
 * to the MCP server's {@code get_user_portfolio} tool. Never exposed to Telegram users directly —
 * consumed only by the AI agent via HTTP.
 *
 * @param chatId            Telegram chat ID this snapshot belongs to
 * @param exchangeConnected whether the user currently has exchange credentials saved
 * @param exchangeName      display name of the connected exchange, or {@code null} if none
 * @param tradingEnabled    whether the automated trading scheduler is active for this user
 * @param recentTrades      most recent trade orders, newest first
 */
public record PortfolioResponse(
        long chatId,
        boolean exchangeConnected,
        String exchangeName,
        boolean tradingEnabled,
        List<TradeSummary> recentTrades
) {

    /**
     * One trade order row, flattened to strings for simple JSON transport and direct
     * consumption by an LLM — no client-side parsing of numeric/date types required.
     *
     * @param coin      traded coin symbol, e.g. {@code "BTC"}
     * @param side      {@code "BUY"} or {@code "SELL"}
     * @param quantity  base-asset quantity transacted, or {@code null} if the order failed
     * @param price     execution price in USDT, or {@code null} if the order failed
     * @param status    {@code "FILLED"} or {@code "FAILED"}
     * @param createdAt ISO-8601 timestamp of the order
     */
    public record TradeSummary(
            String coin,
            String side,
            String quantity,
            String price,
            String status,
            String createdAt
    ) {}
}
