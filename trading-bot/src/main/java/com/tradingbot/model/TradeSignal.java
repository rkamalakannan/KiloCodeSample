package com.tradingbot.model;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Immutable trade signal emitted by a strategy.
 */
public record TradeSignal(
    String symbol,
    SignalType type,
    BigDecimal price,
    BigDecimal confidence,   // 0.0 – 1.0
    String strategyName,
    ZonedDateTime timestamp,
    String reason
) {
    public enum SignalType {
        BUY, SELL, HOLD
    }

    /** True if this signal should trigger an order */
    public boolean isActionable() {
        return type != SignalType.HOLD && confidence.compareTo(BigDecimal.valueOf(0.6)) >= 0;
    }
}
