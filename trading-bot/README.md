# Ta4j Trading Bot

Optimized algorithmic trading bot built with **Ta4j 0.16** + **Spring Boot 3** + **Java 21**.
Runs 24/7 on cheap cloud providers (Railway $5/mo or Fly.io free tier).

---

## 🏗️ Architecture

```
trading-bot/
├── src/main/java/com/tradingbot/
│   ├── TradingBotApplication.java     # Spring Boot entry point
│   ├── config/
│   │   └── BotConfig.java             # Caffeine cache + async executor
│   ├── model/
│   │   ├── Bar.java                   # OHLCV record (Java 21)
│   │   └── TradeSignal.java           # Signal record (BUY/SELL/HOLD)
│   ├── strategy/
│   │   ├── CompositeStrategy.java     # EMA + RSI + MACD + BB + Stochastic
│   │   └── StrategyRunner.java        # Ta4j strategy evaluator
│   ├── service/
│   │   ├── MarketDataService.java     # Binance API + Caffeine cache
│   │   └── TradingEngine.java         # Scheduler + async evaluation
│   └── controller/
│       └── BotController.java         # REST API + health endpoint
├── Dockerfile                         # Multi-stage, JRE-only, non-root
├── railway.toml                       # Railway deployment config
├── fly.toml                           # Fly.io deployment config
└── .github/workflows/deploy.yml       # CI/CD pipeline
```

---

## ⚡ Performance Optimizations

| Optimization | What it does | Impact |
|---|---|---|
| **ZGC** (`-XX:+UseZGC`) | Sub-millisecond GC pauses | No latency spikes during trading |
| **Caffeine cache** | Market data cached 30s | 90%+ fewer API calls |
| **Async execution** | Each symbol on separate thread | Parallel evaluation |
| **maxBarCount(500)** | Caps BarSeries memory | Fits in 512MB RAM |
| **Lazy init** | Spring beans loaded on demand | 40% faster startup |
| **OkHttp pool** | Reuses TCP connections | Lower latency |
| **Java 21 records** | Zero-boilerplate immutable data | Less GC pressure |
| **Multi-stage Docker** | JRE-only runtime image | ~200MB smaller image |

---

## 📊 Strategy: CompositeEMA-RSI-MACD-BB

**BUY** when ALL of:
- EMA(9) crosses above EMA(21) — trend turning bullish
- RSI(14) < 65 — not yet overbought
- MACD > Signal line — momentum confirmation
- Price near lower Bollinger Band OR Stochastic < 30 — good entry timing

**SELL** when ANY of:
- EMA(9) crosses below EMA(21) — trend turning bearish
- RSI(14) > 70 — overbought
- Price above upper Bollinger Band — extended
- MACD < Signal AND RSI > 60 — momentum fading

---

## 🚀 Cloud Deployment

### Option A: Railway ($5/month — recommended)

1. Push code to GitHub
2. Go to [railway.app](https://railway.app) → New Project → Deploy from GitHub
3. Select the `trading-bot/` directory
4. Set environment variables in Railway dashboard:
   ```
   BOT_SYMBOLS=BTCUSDT,ETHUSDT,SOLUSDT
   BOT_INTERVAL=5m
   BOT_SCAN_RATE_MS=60000
   ```
5. Railway auto-detects `railway.toml` and deploys 24/7

### Option B: Fly.io (Free tier — 3 VMs always-on)

```bash
# Install flyctl
curl -L https://fly.io/install.sh | sh

# Deploy
cd trading-bot
fly launch --dockerfile Dockerfile --name ta4j-trading-bot
fly deploy

# Set secrets
fly secrets set BOT_API_KEY=xxx BOT_API_SECRET=yyy
```

### Option C: Render (Free tier — spins down after 15min inactivity)

> ⚠️ Render free tier sleeps — use a cron job to ping `/api/health` every 10 minutes
> to keep it awake, or upgrade to $7/month paid plan.

1. New Web Service → Connect GitHub
2. Build Command: `cd trading-bot && ./mvnw package -DskipTests`
3. Start Command: `java $JAVA_OPTS -jar trading-bot/target/ta4j-trading-bot-*.jar`

---

## 🔧 Local Development

```bash
cd trading-bot

# Build
./mvnw package -DskipTests

# Run
java -jar target/ta4j-trading-bot-*.jar

# Or with Docker
docker build -t trading-bot .
docker run -p 8080:8080 \
  -e BOT_SYMBOLS=BTCUSDT,ETHUSDT \
  -e BOT_INTERVAL=5m \
  trading-bot
```

---

## 🌐 API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Liveness probe |
| GET | `/api/status` | Bot status + watched symbols |
| GET | `/api/signals` | All signal history |
| GET | `/api/signals/{symbol}` | Signals for one symbol |
| POST | `/api/scan` | Trigger manual scan |
| GET | `/actuator/prometheus` | Prometheus metrics |
| GET | `/actuator/health` | Spring health details |

---

## ⚙️ Configuration

All settings configurable via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `BOT_SYMBOLS` | `BTCUSDT,ETHUSDT,SOLUSDT` | Comma-separated symbols |
| `BOT_INTERVAL` | `5m` | Candle interval (1m/5m/15m/1h/1d) |
| `BOT_BARS` | `200` | Historical bars to fetch |
| `BOT_SCAN_RATE_MS` | `60000` | Scan frequency in milliseconds |
| `PORT` | `8080` | HTTP port (auto-set by Railway/Fly.io) |

---

## 📈 Adding Your Own Strategy

1. Create a new method in [`CompositeStrategy.java`](src/main/java/com/tradingbot/strategy/CompositeStrategy.java)
2. Use any Ta4j indicators from the [Ta4j docs](https://ta4j.github.io/ta4j-wiki/)
3. Return a `BaseStrategy` with entry/exit rules
4. Pass it to `StrategyRunner` in `TradingEngine.java`

---

## 🔔 Adding Notifications

To get Telegram alerts on BUY/SELL signals, add to `TradingEngine.dispatchSignal()`:

```java
// In dispatchSignal() method:
if (signal.isActionable()) {
    String msg = String.format("🚨 %s %s @ $%s", signal.type(), signal.symbol(), signal.price());
    telegramService.send(msg);  // implement TelegramService
}
```
