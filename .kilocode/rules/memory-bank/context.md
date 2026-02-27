# Active Context: Next.js Starter Template + Ta4j Trading Bot

## Current State

**Template Status**: ✅ Ready for development
**Trading Bot**: ✅ Fully scaffolded with optimizations + cloud deployment

## Recently Completed

- [x] Base Next.js 16 setup with App Router
- [x] TypeScript configuration with strict mode
- [x] Tailwind CSS 4 integration
- [x] ESLint configuration
- [x] Memory bank documentation
- [x] Recipe system for common features
- [x] **Ta4j Trading Bot** — full Java 21 + Spring Boot 3 implementation
- [x] **Performance optimizations** — ZGC, Caffeine cache, async execution, lazy init
- [x] **Composite strategy** — EMA + RSI + MACD + Bollinger Bands + Stochastic
- [x] **Dockerfile** — multi-stage, JRE-only, non-root, ZGC flags
- [x] **Railway deployment** — `railway.toml` for $5/month always-on
- [x] **Fly.io deployment** — `fly.toml` for free tier always-on
- [x] **GitHub Actions CI/CD** — build → test → Docker push → Railway deploy
- [x] **REST API** — `/api/health`, `/api/status`, `/api/signals`, `/api/scan`
- [x] **Next.js dashboard** — `BotDashboard.tsx` shows live signals + status

## Current Structure

| File/Directory | Purpose | Status |
|----------------|---------|--------|
| `src/app/page.tsx` | Home page → BotDashboard | ✅ Ready |
| `src/app/layout.tsx` | Root layout | ✅ Ready |
| `src/components/BotDashboard.tsx` | Live trading dashboard | ✅ Ready |
| `trading-bot/` | Java trading bot | ✅ Ready |
| `trading-bot/pom.xml` | Maven config (Ta4j 0.16, Spring Boot 3.2) | ✅ Ready |
| `trading-bot/Dockerfile` | Multi-stage container build | ✅ Ready |
| `trading-bot/railway.toml` | Railway cloud config | ✅ Ready |
| `trading-bot/fly.toml` | Fly.io cloud config | ✅ Ready |
| `trading-bot/.github/workflows/deploy.yml` | CI/CD pipeline | ✅ Ready |
| `.kilocode/` | AI context & recipes | ✅ Ready |

## Trading Bot Architecture

```
trading-bot/src/main/java/com/tradingbot/
├── TradingBotApplication.java     # @EnableCaching @EnableAsync @EnableScheduling
├── config/BotConfig.java          # Caffeine cache + ThreadPoolTaskExecutor
├── model/Bar.java                 # Java 21 record (OHLCV)
├── model/TradeSignal.java         # Java 21 record (BUY/SELL/HOLD)
├── strategy/CompositeStrategy.java # EMA+RSI+MACD+BB+Stochastic
├── strategy/StrategyRunner.java    # Ta4j evaluator + backtest
├── service/MarketDataService.java  # Binance API + @Cacheable
├── service/TradingEngine.java      # @Scheduled scanner + @Async eval
└── controller/BotController.java   # REST API
```

## Cloud Deployment Options (Cheapest Always-On)

| Provider | Cost | Always-On | Notes |
|----------|------|-----------|-------|
| **Railway** | $5/month | ✅ Yes | Recommended — easiest setup |
| **Fly.io** | Free | ✅ Yes | 3 VMs free, `auto_stop_machines=false` |
| **Render** | Free | ⚠️ Sleeps | Need ping cron to keep awake |

## Session History

| Date | Changes |
|------|---------|
| Initial | Template created with base setup |
| 2026-02-27 | Added Ta4j trading bot with full optimization + cloud deployment |
