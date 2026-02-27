package com.tradingbot.strategy;

import com.tradingbot.model.TradeSignal;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.pnl.ReturnCriterion;
import org.ta4j.core.num.DecimalNum;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Evaluates a Ta4j {@link Strategy} against a live {@link BarSeries}
 * and emits a {@link TradeSignal}.
 *
 * Optimization notes:
 * - Reuses the same BarSeries instance (no copy) to avoid GC pressure
 * - Only evaluates the last bar (index = endIndex) for live trading
 * - Backtest path uses BarSeriesManager for full historical analysis
 */
public class StrategyRunner {

    private final BarSeries series;
    private final Strategy strategy;
    private final TradingRecord tradingRecord;
    private final String strategyName;

    public StrategyRunner(BarSeries series, Strategy strategy, String strategyName) {
        this.series = series;
        this.strategy = strategy;
        this.strategyName = strategyName;
        this.tradingRecord = new org.ta4j.core.BaseTradingRecord();
    }

    /**
     * Evaluate the latest bar and return a signal.
     * Called on every new bar arrival (e.g., every minute).
     */
    public TradeSignal evaluate(String symbol) {
        int endIndex = series.getEndIndex();
        if (endIndex < 0) {
            return holdSignal(symbol, "No bars available");
        }

        boolean shouldEnter = strategy.shouldEnter(endIndex, tradingRecord);
        boolean shouldExit  = strategy.shouldExit(endIndex, tradingRecord);

        if (shouldEnter) {
            tradingRecord.enter(endIndex, series.getBar(endIndex).getClosePrice(), DecimalNum.valueOf(1));
            return new TradeSignal(
                symbol,
                TradeSignal.SignalType.BUY,
                toBigDecimal(series.getBar(endIndex).getClosePrice()),
                BigDecimal.valueOf(0.8),
                strategyName,
                ZonedDateTime.now(),
                "Strategy entry condition met at bar " + endIndex
            );
        }

        if (shouldExit) {
            tradingRecord.exit(endIndex, series.getBar(endIndex).getClosePrice(), DecimalNum.valueOf(1));
            return new TradeSignal(
                symbol,
                TradeSignal.SignalType.SELL,
                toBigDecimal(series.getBar(endIndex).getClosePrice()),
                BigDecimal.valueOf(0.8),
                strategyName,
                ZonedDateTime.now(),
                "Strategy exit condition met at bar " + endIndex
            );
        }

        return holdSignal(symbol, "No signal at bar " + endIndex);
    }

    /**
     * Full backtest — returns total return percentage.
     * Use this for strategy optimization / parameter tuning.
     */
    public double backtest() {
        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record = manager.run(strategy);
        ReturnCriterion criterion = new ReturnCriterion();
        return criterion.calculate(series, record).doubleValue();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private TradeSignal holdSignal(String symbol, String reason) {
        int endIndex = Math.max(series.getEndIndex(), 0);
        BigDecimal price = endIndex >= 0
            ? toBigDecimal(series.getBar(endIndex).getClosePrice())
            : BigDecimal.ZERO;
        return new TradeSignal(
            symbol,
            TradeSignal.SignalType.HOLD,
            price,
            BigDecimal.valueOf(0.5),
            strategyName,
            ZonedDateTime.now(),
            reason
        );
    }

    private BigDecimal toBigDecimal(org.ta4j.core.num.Num num) {
        return BigDecimal.valueOf(num.doubleValue());
    }
}
