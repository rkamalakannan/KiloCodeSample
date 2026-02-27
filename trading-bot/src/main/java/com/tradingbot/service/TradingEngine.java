package com.tradingbot.service;

import com.tradingbot.model.TradeSignal;
import com.tradingbot.strategy.CompositeStrategy;
import com.tradingbot.strategy.StrategyRunner;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core trading engine — orchestrates market data fetching, strategy evaluation,
 * and signal dispatch.
 *
 * Scheduling:
 *   - @Scheduled(fixedRateString): runs every N seconds (configurable via env var)
 *   - @Async: each symbol evaluated on a separate thread from the strategyExecutor pool
 *
 * Metrics (exposed at /actuator/prometheus):
 *   - trading.signals.buy / sell / hold counts
 *   - trading.evaluation.duration histogram
 */
@Service
public class TradingEngine {

    private static final Logger log = LoggerFactory.getLogger(TradingEngine.class);

    private final MarketDataService marketDataService;
    private final CompositeStrategy compositeStrategy;
    private final MeterRegistry meterRegistry;

    // Configurable via environment variables
    @Value("${bot.symbols:BTCUSDT,ETHUSDT,SOLUSDT}")
    private String symbolsConfig;

    @Value("${bot.interval:5m}")
    private String interval;

    @Value("${bot.bars:200}")
    private int barsToFetch;

    // In-memory signal history (last 100 per symbol)
    private final Map<String, List<TradeSignal>> signalHistory = new ConcurrentHashMap<>();
    private final AtomicInteger totalSignals = new AtomicInteger(0);

    // Metrics
    private Counter buyCounter;
    private Counter sellCounter;
    private Counter holdCounter;
    private Timer evaluationTimer;

    public TradingEngine(
        MarketDataService marketDataService,
        CompositeStrategy compositeStrategy,
        MeterRegistry meterRegistry
    ) {
        this.marketDataService = marketDataService;
        this.compositeStrategy = compositeStrategy;
        this.meterRegistry = meterRegistry;
    }

    // Lazy-init metrics after Spring context is ready
    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        buyCounter      = meterRegistry.counter("trading.signals", "type", "buy");
        sellCounter     = meterRegistry.counter("trading.signals", "type", "sell");
        holdCounter     = meterRegistry.counter("trading.signals", "type", "hold");
        evaluationTimer = meterRegistry.timer("trading.evaluation.duration");
    }

    // ─── Scheduled Scan ───────────────────────────────────────────────────────

    /**
     * Main loop: runs every 60 seconds by default.
     * Override with BOT_SCAN_RATE_MS environment variable.
     */
    @Scheduled(fixedRateString = "${bot.scanRateMs:60000}")
    public void scan() {
        String[] symbols = symbolsConfig.split(",");
        log.info("Starting scan for {} symbols on {} interval", symbols.length, interval);

        for (String symbol : symbols) {
            evaluateSymbol(symbol.trim());
        }
    }

    // ─── Async Evaluation ─────────────────────────────────────────────────────

    @Async("strategyExecutor")
    public void evaluateSymbol(String symbol) {
        evaluationTimer.record(() -> {
            try {
                BarSeries series = marketDataService.fetchBars(symbol, interval, barsToFetch);

                if (series.getBarCount() < 30) {
                    log.warn("Not enough bars for {} (got {}), skipping", symbol, series.getBarCount());
                    return;
                }

                StrategyRunner runner = new StrategyRunner(
                    series,
                    compositeStrategy.build(series),
                    "CompositeStrategy"
                );

                TradeSignal signal = runner.evaluate(symbol);
                recordSignal(symbol, signal);
                dispatchSignal(signal);

            } catch (Exception e) {
                log.error("Error evaluating {}: {}", symbol, e.getMessage(), e);
            }
        });
    }

    // ─── Signal Handling ──────────────────────────────────────────────────────

    private void recordSignal(String symbol, TradeSignal signal) {
        signalHistory.computeIfAbsent(symbol, k -> new ArrayList<>());
        List<TradeSignal> history = signalHistory.get(symbol);

        synchronized (history) {
            history.add(signal);
            // Keep only last 100 signals per symbol
            if (history.size() > 100) {
                history.remove(0);
            }
        }

        totalSignals.incrementAndGet();

        switch (signal.type()) {
            case BUY  -> buyCounter.increment();
            case SELL -> sellCounter.increment();
            case HOLD -> holdCounter.increment();
        }
    }

    private void dispatchSignal(TradeSignal signal) {
        if (signal.isActionable()) {
            log.info("🚨 ACTIONABLE SIGNAL: {} {} @ {} [{}] confidence={}",
                signal.type(), signal.symbol(), signal.price(),
                signal.strategyName(), signal.confidence());
            // TODO: connect to broker API (Alpaca, IBKR, Binance) here
        } else {
            log.debug("Signal: {} {} @ {}", signal.type(), signal.symbol(), signal.price());
        }
    }

    // ─── Public API (for REST controller) ────────────────────────────────────

    public Map<String, List<TradeSignal>> getSignalHistory() {
        return signalHistory;
    }

    public int getTotalSignals() {
        return totalSignals.get();
    }

    public List<String> getWatchedSymbols() {
        return List.of(symbolsConfig.split(","));
    }
}
