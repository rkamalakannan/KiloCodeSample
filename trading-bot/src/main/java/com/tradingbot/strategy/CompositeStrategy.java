package com.tradingbot.strategy;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

/**
 * Composite multi-indicator strategy combining:
 *
 *  1. EMA crossover  (trend direction)
 *  2. RSI            (momentum / overbought-oversold)
 *  3. MACD           (momentum confirmation)
 *  4. Bollinger Bands (volatility / mean reversion)
 *  5. Stochastic     (entry timing)
 *
 * BUY  when: EMA9 crosses above EMA21 AND RSI < 65 AND MACD > signal AND price near lower BB
 * SELL when: EMA9 crosses below EMA21 OR RSI > 70 OR price above upper BB
 *
 * Optimization: all indicators share the same ClosePriceIndicator instance
 * to avoid redundant computation.
 */
@Component
public class CompositeStrategy {

    public Strategy build(BarSeries series) {
        // ── Shared base indicator (computed once, reused everywhere) ──────────
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // ── EMA Crossover ─────────────────────────────────────────────────────
        EMAIndicator ema9  = new EMAIndicator(closePrice, 9);
        EMAIndicator ema21 = new EMAIndicator(closePrice, 21);
        EMAIndicator ema50 = new EMAIndicator(closePrice, 50);

        // ── RSI ───────────────────────────────────────────────────────────────
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        // ── MACD ──────────────────────────────────────────────────────────────
        MACDIndicator macd       = new MACDIndicator(closePrice, 12, 26);
        EMAIndicator  macdSignal = new EMAIndicator(macd, 9);

        // ── Bollinger Bands ───────────────────────────────────────────────────
        BollingerBandsMiddleIndicator bbMiddle = new BollingerBandsMiddleIndicator(
            new EMAIndicator(closePrice, 20)
        );
        StandardDeviationIndicator stdDev = new StandardDeviationIndicator(closePrice, 20);
        BollingerBandsUpperIndicator bbUpper = new BollingerBandsUpperIndicator(bbMiddle, stdDev);
        BollingerBandsLowerIndicator bbLower = new BollingerBandsLowerIndicator(bbMiddle, stdDev);

        // ── Stochastic ────────────────────────────────────────────────────────
        StochasticOscillatorKIndicator stochK = new StochasticOscillatorKIndicator(series, 14);

        // ── Entry Rules (BUY) ─────────────────────────────────────────────────
        Rule emaBullish    = new CrossedUpIndicatorRule(ema9, ema21);          // short EMA crosses above long
        Rule rsiNotOverbought = new UnderIndicatorRule(rsi, 65);               // RSI below 65
        Rule macdBullish   = new OverIndicatorRule(macd, macdSignal);          // MACD above signal line
        Rule nearLowerBB   = new UnderIndicatorRule(closePrice, bbMiddle);     // price below middle BB
        Rule stochOversold = new UnderIndicatorRule(stochK, 30);               // stochastic oversold

        Rule entryRule = emaBullish
            .and(rsiNotOverbought)
            .and(macdBullish)
            .and(nearLowerBB.or(stochOversold));

        // ── Exit Rules (SELL) ─────────────────────────────────────────────────
        Rule emaBearish    = new CrossedDownIndicatorRule(ema9, ema21);        // short EMA crosses below long
        Rule rsiOverbought = new OverIndicatorRule(rsi, 70);                   // RSI above 70
        Rule aboveUpperBB  = new OverIndicatorRule(closePrice, bbUpper);       // price above upper BB
        Rule macdBearish   = new UnderIndicatorRule(macd, macdSignal);         // MACD below signal

        Rule exitRule = emaBearish
            .or(rsiOverbought)
            .or(aboveUpperBB)
            .or(macdBearish.and(new OverIndicatorRule(rsi, 60)));

        return new BaseStrategy("CompositeEMA-RSI-MACD-BB", entryRule, exitRule, 9);
    }

    /**
     * Lightweight scalping strategy for high-frequency intervals (1m/5m).
     * Uses only EMA + RSI for minimal computation overhead.
     */
    public Strategy buildScalping(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema5  = new EMAIndicator(closePrice, 5);
        EMAIndicator ema13 = new EMAIndicator(closePrice, 13);
        RSIIndicator rsi   = new RSIIndicator(closePrice, 7);

        Rule entryRule = new CrossedUpIndicatorRule(ema5, ema13)
            .and(new UnderIndicatorRule(rsi, 60));

        Rule exitRule = new CrossedDownIndicatorRule(ema5, ema13)
            .or(new OverIndicatorRule(rsi, 75));

        return new BaseStrategy("ScalpingEMA5-13", entryRule, exitRule, 5);
    }
}
