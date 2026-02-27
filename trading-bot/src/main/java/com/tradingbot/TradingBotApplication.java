package com.tradingbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Ta4j Trading Bot.
 *
 * Optimizations enabled:
 * - @EnableCaching: Caffeine in-memory cache for market data
 * - @EnableAsync: Non-blocking strategy execution
 * - @EnableScheduling: Periodic market scanning
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class TradingBotApplication {

    public static void main(String[] args) {
        // Tune Spring Boot startup for low-memory cloud environments
        System.setProperty("spring.jmx.enabled", "false");
        System.setProperty("spring.main.lazy-initialization", "true");
        SpringApplication.run(TradingBotApplication.class, args);
    }
}
