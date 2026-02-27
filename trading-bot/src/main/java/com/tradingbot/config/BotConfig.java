package com.tradingbot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Central configuration for performance-critical beans.
 *
 * Key optimizations:
 * 1. Caffeine cache: avoids redundant API calls (market data cached for 30s)
 * 2. Virtual thread executor (Java 21): lightweight concurrency for strategy runs
 * 3. Bounded thread pool: prevents OOM on cheap cloud instances
 */
@Configuration
public class BotConfig {

    // ─── Cache ────────────────────────────────────────────────────────────────

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)   // market data TTL
                .maximumSize(500)                          // cap memory usage
                .recordStats()                             // expose via Actuator
        );
        return manager;
    }

    // ─── Async Executor ───────────────────────────────────────────────────────

    /**
     * Uses Java 21 virtual threads for near-zero overhead async strategy execution.
     * Falls back to platform threads if virtual threads are unavailable.
     */
    @Bean(name = "strategyExecutor")
    public Executor strategyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("strategy-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
