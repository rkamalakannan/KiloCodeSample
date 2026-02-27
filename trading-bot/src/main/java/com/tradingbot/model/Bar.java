package com.tradingbot.model;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Immutable OHLCV bar record.
 * Using Java 21 records for zero-boilerplate, immutable data.
 */
public record Bar(
    ZonedDateTime beginTime,
    ZonedDateTime endTime,
    BigDecimal openPrice,
    BigDecimal highPrice,
    BigDecimal lowPrice,
    BigDecimal closePrice,
    BigDecimal volume
) {
    /** Convenience: percentage change from open to close */
    public double changePercent() {
        if (openPrice.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return closePrice.subtract(openPrice)
                         .divide(openPrice, 6, java.math.RoundingMode.HALF_UP)
                         .multiply(BigDecimal.valueOf(100))
                         .doubleValue();
    }
}
