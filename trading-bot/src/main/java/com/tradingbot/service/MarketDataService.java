package com.tradingbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Fetches OHLCV market data from Binance public API (no API key required).
 *
 * Optimizations:
 * - @Cacheable: results cached for 30s (configured in BotConfig) — avoids
 *   hammering the exchange on every strategy tick
 * - OkHttpClient with connection pooling: reuses TCP connections
 * - maxBarCount(500): caps BarSeries memory footprint
 *
 * To use a different exchange, implement the same interface and swap the bean.
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    // Binance public klines endpoint — no auth needed
    private static final String BINANCE_KLINES =
        "https://api.binance.com/api/v3/klines?symbol=%s&interval=%s&limit=%d";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MarketDataService() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .connectionPool(new okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetch OHLCV bars for a symbol.
     *
     * @param symbol   e.g. "BTCUSDT", "ETHUSDT"
     * @param interval e.g. "1m", "5m", "1h", "1d"
     * @param limit    number of bars (max 1000)
     * @return populated BarSeries ready for Ta4j indicators
     */
    @Cacheable(value = "marketData", key = "#symbol + '-' + #interval + '-' + #limit")
    public BarSeries fetchBars(String symbol, String interval, int limit) {
        String url = String.format(BINANCE_KLINES, symbol, interval, limit);
        log.debug("Fetching market data: {}", url);

        Request request = new Request.Builder().url(url).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("Failed to fetch market data for {}: HTTP {}", symbol, response.code());
                return emptySeriesFor(symbol);
            }

            String body = response.body().string();
            return parseKlines(symbol, body);

        } catch (IOException e) {
            log.error("Network error fetching market data for {}: {}", symbol, e.getMessage());
            return emptySeriesFor(symbol);
        }
    }

    // ─── Parsing ──────────────────────────────────────────────────────────────

    private BarSeries parseKlines(String symbol, String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);

        BarSeries series = new BaseBarSeriesBuilder()
            .withName(symbol)
            .withMaxBarCount(500)   // cap memory: only keep last 500 bars
            .build();

        for (JsonNode kline : root) {
            // Binance kline format:
            // [openTime, open, high, low, close, volume, closeTime, ...]
            long openTimeMs  = kline.get(0).asLong();
            long closeTimeMs = kline.get(6).asLong();

            ZonedDateTime beginTime = Instant.ofEpochMilli(openTimeMs)
                .atZone(ZoneOffset.UTC);
            ZonedDateTime endTime = Instant.ofEpochMilli(closeTimeMs)
                .atZone(ZoneOffset.UTC);

            double open   = kline.get(1).asDouble();
            double high   = kline.get(2).asDouble();
            double low    = kline.get(3).asDouble();
            double close  = kline.get(4).asDouble();
            double volume = kline.get(5).asDouble();

            series.addBar(
                Duration.between(beginTime, endTime),
                endTime,
                open, high, low, close, volume
            );
        }

        log.info("Loaded {} bars for {}", series.getBarCount(), symbol);
        return series;
    }

    private BarSeries emptySeriesFor(String symbol) {
        return new BaseBarSeries(symbol);
    }
}
