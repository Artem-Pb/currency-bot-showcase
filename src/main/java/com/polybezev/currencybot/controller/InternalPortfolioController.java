package com.polybezev.currencybot.controller;

import com.polybezev.currencybot.dto.PortfolioResponse;
import com.polybezev.currencybot.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only internal API consumed exclusively by {@code mcp-server}'s {@code get_user_portfolio}
 * MCP tool — never called by Telegram users or exposed in bot UI. Kept inside the bot process
 * (not a shared database) so the bot remains the single owner of its own data; see
 * {@code ai/CODE_REFERENCE.md} for the full cross-service architecture.
 * <p>
 * Protected by a shared-secret header rather than full auth infrastructure — proportionate to
 * an MVP with a single trusted caller behind a private tunnel, not a public-facing API.
 */
@RestController
@RequestMapping("/internal/portfolio")
@RequiredArgsConstructor
public class InternalPortfolioController {

    private final TradeService tradeService;

    /** Shared secret both this endpoint and mcp-server's {@code PortfolioService} are configured with. */
    @Value("${internal.mcp.token}")
    private String internalToken;

    /**
     * Returns the portfolio snapshot for the given Telegram chat ID.
     *
     * @param chatId Telegram chat ID (the {@code userId} the agent passes to the MCP tool)
     * @param token  value of the {@code X-Internal-Token} header; must match {@link #internalToken}
     * @return {@code 200} with the snapshot, or {@code 403} if the token does not match
     */
    @GetMapping("/{chatId}")
    public ResponseEntity<PortfolioResponse> getPortfolio(
            @PathVariable long chatId,
            @RequestHeader("X-Internal-Token") String token) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(tradeService.getPortfolioSummary(chatId));
    }
}
