package com.tradingbot.controller;

import com.tradingbot.model.TradeSignal;
import com.tradingbot.service.MarketDataService;
import com.tradingbot.service.TradingEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API for the trading bot.
 *
 * Endpoints:
 *   GET  /api/health          — liveness probe (used by cloud platforms)
 *   GET  /api/status          — bot status summary
 *   GET  /api/signals         — all signal history
 *   GET  /api/signals/{symbol} — signals for one symbol
 *   POST /api/scan            — trigger manual scan
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")   // allow Next.js dashboard to call this
public class BotController {

    private final TradingEngine tradingEngine;
    private final MarketDataService marketDataService;

    public BotController(TradingEngine tradingEngine, MarketDataService marketDataService) {
        this.tradingEngine = tradingEngine;
        this.marketDataService = marketDataService;
    }

    // ─── Health ───────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", ZonedDateTime.now().toString(),
            "totalSignals", tradingEngine.getTotalSignals()
        ));
    }

    // ─── Status ───────────────────────────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "status", "RUNNING",
            "watchedSymbols", tradingEngine.getWatchedSymbols(),
            "totalSignals", tradingEngine.getTotalSignals(),
            "timestamp", ZonedDateTime.now().toString()
        ));
    }

    // ─── Signals ──────────────────────────────────────────────────────────────

    @GetMapping("/signals")
    public ResponseEntity<Map<String, List<TradeSignal>>> allSignals() {
        return ResponseEntity.ok(tradingEngine.getSignalHistory());
    }

    @GetMapping("/signals/{symbol}")
    public ResponseEntity<List<TradeSignal>> signalsForSymbol(@PathVariable String symbol) {
        Map<String, List<TradeSignal>> history = tradingEngine.getSignalHistory();
        List<TradeSignal> signals = history.getOrDefault(symbol.toUpperCase(), List.of());
        return ResponseEntity.ok(signals);
    }

    // ─── Manual Trigger ───────────────────────────────────────────────────────

    @PostMapping("/scan")
    public ResponseEntity<Map<String, String>> triggerScan() {
        tradingEngine.getWatchedSymbols().forEach(tradingEngine::evaluateSymbol);
        return ResponseEntity.ok(Map.of(
            "message", "Scan triggered for " + tradingEngine.getWatchedSymbols().size() + " symbols",
            "timestamp", ZonedDateTime.now().toString()
        ));
    }
}
