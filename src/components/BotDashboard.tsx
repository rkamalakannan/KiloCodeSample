"use client";

import { useEffect, useState, useCallback } from "react";

// ─── Types ────────────────────────────────────────────────────────────────────

interface BotStatus {
  status: string;
  watchedSymbols: string[];
  totalSignals: number;
  timestamp: string;
}

interface TradeSignal {
  symbol: string;
  type: "BUY" | "SELL" | "HOLD";
  price: number;
  confidence: number;
  strategyName: string;
  timestamp: string;
  reason: string;
}

// ─── Config ───────────────────────────────────────────────────────────────────

// Set this to your deployed bot URL (Railway/Fly.io)
const BOT_API_URL = process.env.NEXT_PUBLIC_BOT_API_URL ?? "http://localhost:8080";

// ─── Component ────────────────────────────────────────────────────────────────

export default function BotDashboard() {
  const [status, setStatus] = useState<BotStatus | null>(null);
  const [signals, setSignals] = useState<Record<string, TradeSignal[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date());

  const fetchData = useCallback(async () => {
    try {
      const [statusRes, signalsRes] = await Promise.all([
        fetch(`${BOT_API_URL}/api/status`),
        fetch(`${BOT_API_URL}/api/signals`),
      ]);

      if (!statusRes.ok || !signalsRes.ok) {
        throw new Error("Bot API unreachable");
      }

      const [statusData, signalsData] = await Promise.all([
        statusRes.json(),
        signalsRes.json(),
      ]);

      setStatus(statusData);
      setSignals(signalsData);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Connection failed");
    } finally {
      setLoading(false);
      setLastRefresh(new Date());
    }
  }, []);

  // Auto-refresh every 30 seconds
  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 30_000);
    return () => clearInterval(interval);
  }, [fetchData]);

  const triggerScan = async () => {
    try {
      await fetch(`${BOT_API_URL}/api/scan`, { method: "POST" });
      setTimeout(fetchData, 2000); // refresh after 2s
    } catch {
      setError("Failed to trigger scan");
    }
  };

  // ─── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="min-h-screen bg-neutral-900 text-white p-6">
      {/* Header */}
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-3xl font-bold text-white">
              📈 Ta4j Trading Bot
            </h1>
            <p className="text-neutral-400 mt-1 text-sm">
              Last refresh: {lastRefresh.toLocaleTimeString()}
            </p>
          </div>
          <div className="flex gap-3">
            <button
              onClick={fetchData}
              className="px-4 py-2 bg-neutral-700 hover:bg-neutral-600 rounded-lg text-sm transition-colors"
            >
              🔄 Refresh
            </button>
            <button
              onClick={triggerScan}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg text-sm font-medium transition-colors"
            >
              ⚡ Scan Now
            </button>
          </div>
        </div>

        {/* Error Banner */}
        {error && (
          <div className="mb-6 p-4 bg-red-900/50 border border-red-700 rounded-lg text-red-300">
            ⚠️ {error} — Make sure the bot is running at{" "}
            <code className="text-red-200">{BOT_API_URL}</code>
          </div>
        )}

        {/* Status Cards */}
        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
            {[1, 2, 3].map((i) => (
              <div key={i} className="bg-neutral-800 rounded-xl p-5 animate-pulse h-24" />
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
            <StatusCard
              label="Bot Status"
              value={status?.status ?? "OFFLINE"}
              icon={status?.status === "RUNNING" ? "🟢" : "🔴"}
              sub={status ? `Since ${new Date(status.timestamp).toLocaleTimeString()}` : ""}
            />
            <StatusCard
              label="Total Signals"
              value={String(status?.totalSignals ?? 0)}
              icon="📊"
              sub="All time"
            />
            <StatusCard
              label="Watching"
              value={status?.watchedSymbols?.join(", ") ?? "—"}
              icon="👁️"
              sub={`${status?.watchedSymbols?.length ?? 0} symbols`}
            />
          </div>
        )}

        {/* Signal Tables */}
        <div className="space-y-6">
          {Object.entries(signals).map(([symbol, symbolSignals]) => (
            <SignalTable key={symbol} symbol={symbol} signals={symbolSignals} />
          ))}
          {!loading && Object.keys(signals).length === 0 && (
            <div className="text-center py-16 text-neutral-500">
              <p className="text-4xl mb-3">📭</p>
              <p>No signals yet &mdash; click &ldquo;Scan Now&rdquo; to trigger the first scan</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function StatusCard({
  label,
  value,
  icon,
  sub,
}: {
  label: string;
  value: string;
  icon: string;
  sub: string;
}) {
  return (
    <div className="bg-neutral-800 rounded-xl p-5 border border-neutral-700">
      <div className="flex items-center gap-2 text-neutral-400 text-sm mb-2">
        <span>{icon}</span>
        <span>{label}</span>
      </div>
      <div className="text-xl font-bold text-white truncate">{value}</div>
      <div className="text-neutral-500 text-xs mt-1">{sub}</div>
    </div>
  );
}

function SignalTable({
  symbol,
  signals,
}: {
  symbol: string;
  signals: TradeSignal[];
}) {
  const recent = [...signals].reverse().slice(0, 20);

  return (
    <div className="bg-neutral-800 rounded-xl border border-neutral-700 overflow-hidden">
      <div className="px-5 py-4 border-b border-neutral-700 flex items-center justify-between">
        <h2 className="font-semibold text-white">{symbol}</h2>
        <span className="text-neutral-400 text-sm">{signals.length} signals</span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-neutral-400 text-xs border-b border-neutral-700">
              <th className="px-5 py-3 text-left">Time</th>
              <th className="px-5 py-3 text-left">Signal</th>
              <th className="px-5 py-3 text-right">Price</th>
              <th className="px-5 py-3 text-right">Confidence</th>
              <th className="px-5 py-3 text-left">Reason</th>
            </tr>
          </thead>
          <tbody>
            {recent.map((signal, i) => (
              <tr
                key={i}
                className="border-b border-neutral-700/50 hover:bg-neutral-700/30 transition-colors"
              >
                <td className="px-5 py-3 text-neutral-400">
                  {new Date(signal.timestamp).toLocaleTimeString()}
                </td>
                <td className="px-5 py-3">
                  <SignalBadge type={signal.type} />
                </td>
                <td className="px-5 py-3 text-right font-mono text-white">
                  ${Number(signal.price).toLocaleString()}
                </td>
                <td className="px-5 py-3 text-right">
                  <ConfidenceBar value={Number(signal.confidence)} />
                </td>
                <td className="px-5 py-3 text-neutral-400 text-xs max-w-xs truncate">
                  {signal.reason}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function SignalBadge({ type }: { type: "BUY" | "SELL" | "HOLD" }) {
  const styles = {
    BUY:  "bg-green-900/60 text-green-400 border border-green-700",
    SELL: "bg-red-900/60 text-red-400 border border-red-700",
    HOLD: "bg-neutral-700 text-neutral-400 border border-neutral-600",
  };
  const icons = { BUY: "▲", SELL: "▼", HOLD: "—" };
  return (
    <span className={`px-2 py-0.5 rounded text-xs font-medium ${styles[type]}`}>
      {icons[type]} {type}
    </span>
  );
}

function ConfidenceBar({ value }: { value: number }) {
  const pct = Math.round(value * 100);
  const color = pct >= 80 ? "bg-green-500" : pct >= 60 ? "bg-yellow-500" : "bg-neutral-500";
  return (
    <div className="flex items-center gap-2 justify-end">
      <div className="w-16 bg-neutral-700 rounded-full h-1.5">
        <div className={`${color} h-1.5 rounded-full`} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-neutral-400 text-xs w-8">{pct}%</span>
    </div>
  );
}
