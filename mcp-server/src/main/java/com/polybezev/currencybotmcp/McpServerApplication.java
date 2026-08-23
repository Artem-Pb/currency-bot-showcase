package com.polybezev.currencybotmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Currency Bot MCP server — a standalone process exposing crypto-news
 * tools over HTTP/SSE to any MCP-compatible LLM client (currently: the Timeweb Cloud AI Agent
 * configured for {@code currency-bot}). Not called by currency-bot's own code; the agent calls
 * it directly. See {@code ai/CODE_REFERENCE.md} in the currency-bot repo root for the full
 * architecture and setup steps.
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
