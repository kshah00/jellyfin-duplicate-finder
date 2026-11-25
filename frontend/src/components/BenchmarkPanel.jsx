import { useState } from 'react';
import { runBenchmark } from '../api.js';
import { formatBytes, formatInt, formatMs, formatPct } from '../format.js';

const SIZES = [1000, 10000, 100000];

/** Grouped horizontal bars on a log scale: one group per dataset size, two series. */
function BarChart({ title, groups, series }) {
  const values = groups.flatMap((g) => series.map((s) => g[s.key]));
  const max = Math.max(...values);
  const min = Math.min(...values.filter((v) => v > 0));
  const lo = Math.log10(min) - 0.3;
  const hi = Math.log10(max) + 0.05;
  const scale = (v) => Math.max(0.02, (Math.log10(Math.max(v, 1e-6)) - lo) / (hi - lo));

  const labelW = 70;
  const barH = 16;
  const gap = 4;
  const groupGap = 16;
  const width = 560;
  const plotW = width - labelW - 90;
  const groupH = series.length * (barH + gap);
  const height = groups.length * (groupH + groupGap) + 8;

  return (
    <figure className="chart">
      <figcaption>
        <span>{title}</span>
        <span className="chart-legend">
          {series.map((s) => (
            <span key={s.key}>
              <i style={{ background: s.color }} /> {s.label}
            </span>
          ))}
        </span>
      </figcaption>
      <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={title}>
        {groups.map((g, gi) => {
          const y0 = gi * (groupH + groupGap);
          return (
            <g key={g.label} transform={`translate(0, ${y0})`}>
              <text x={labelW - 10} y={groupH / 2} className="axis-label" textAnchor="end" dominantBaseline="middle">
                {g.label}
              </text>
              {series.map((s, si) => {
                const w = scale(g[s.key]) * plotW;
                const y = si * (barH + gap);
                return (
                  <g key={s.key}>
                    <rect x={labelW} y={y} width={w} height={barH} rx="3" fill={s.color} />
                    <text x={labelW + w + 6} y={y + barH / 2} className="bar-value" dominantBaseline="middle">
                      {formatMs(g[s.key])}
                    </text>
                  </g>
                );
              })}
            </g>
          );
        })}
      </svg>
      <p className="muted small">Median of measured runs, log scale.</p>
    </figure>
  );
}

export default function BenchmarkPanel() {
  const [sizes, setSizes] = useState([1000, 10000]);
  const [runs, setRuns] = useState(5);
  const [queries, setQueries] = useState(200);
  const [busy, setBusy] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  async function start() {
    setBusy(true);
    setError(null);
    try {
      setReport(await runBenchmark({ sizes, measuredRuns: Number(runs), warmupRuns: 2, queries: Number(queries), seed: 42 }));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  const toggle = (n) => setSizes((s) => (s.includes(n) ? s.filter((x) => x !== n) : [...s, n].sort((a, b) => a - b)));

  return (
    <div>
      <p className="muted">
        Seeded synthetic libraries with planted duplicates. Each timing is the median of the measured runs after
        warm-up; both sides of every comparison are checked to return identical answers.
      </p>
      <div className="params">
        <fieldset>
          <legend>Dataset sizes</legend>
          {SIZES.map((n) => (
            <label key={n} className="check">
              <input type="checkbox" checked={sizes.includes(n)} onChange={() => toggle(n)} />
              {n.toLocaleString('en-US')}
            </label>
          ))}
        </fieldset>
        <label>
          Measured runs
          <input type="number" min="1" max="25" value={runs} onChange={(e) => setRuns(e.target.value)} />
        </label>
        <label>
          Fuzzy queries
          <input type="number" min="1" max="5000" value={queries} onChange={(e) => setQueries(e.target.value)} />
        </label>
        <button type="button" className="primary self-end" disabled={busy || sizes.length === 0} onClick={start}>
          {busy ? 'Running…' : 'Run benchmark'}
        </button>
      </div>
      {sizes.includes(100000) && !busy && <p className="muted small">100k includes brute-force Levenshtein over every title, so expect it to take a while.</p>}
      {error && (
        <div className="error" role="alert">
          <strong>{error.message}</strong>
        </div>
      )}

      {report && (
        <div className="bench">
          <p className="machine mono small">
            {report.machine.cpu} · {report.machine.availableProcessors} CPUs · {report.machine.os} ({report.machine.arch}) ·
            Java {report.machine.javaVersion} ({report.machine.javaVendor}) · {report.totalSeconds.toFixed(1)} s total ·
            seed {report.config.seed} · {report.config.warmupRuns} warm-up + {report.config.measuredRuns} runs
          </p>

          <div className="two-col">
            <BarChart
              title="Exact duplicates"
              groups={report.results.map((r) => ({
                label: formatInt(r.records),
                bloom: r.dedupe.bloomPipeline.medianMs,
                hash: r.dedupe.hashSetBaseline.medianMs,
              }))}
              series={[
                { key: 'bloom', label: 'Bloom + exact check', color: 'var(--c1)' },
                { key: 'hash', label: 'HashSet only', color: 'var(--c2)' },
              ]}
            />
            <BarChart
              title="Fuzzy title search"
              groups={report.results.map((r) => ({
                label: formatInt(r.records),
                trie: r.fuzzy.trie.medianMs,
                brute: r.fuzzy.bruteForce.medianMs,
              }))}
              series={[
                { key: 'trie', label: 'Trie + pruning', color: 'var(--c1)' },
                { key: 'brute', label: 'Brute-force Levenshtein', color: 'var(--c3)' },
              ]}
            />
          </div>

          <h3>Bloom filter + exact check vs HashSet</h3>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th className="num">Records</th>
                  <th className="num">Bloom + check</th>
                  <th className="num">HashSet</th>
                  <th className="num">Duplicates</th>
                  <th className="num">m / k</th>
                  <th className="num">Filter size</th>
                  <th className="num">Screened out</th>
                  <th className="num">False positives</th>
                  <th className="num">Observed / theory FP</th>
                </tr>
              </thead>
              <tbody>
                {report.results.map((r) => (
                  <tr key={r.records}>
                    <td className="num">{formatInt(r.records)}</td>
                    <td className="num">{formatMs(r.dedupe.bloomPipeline.medianMs)}</td>
                    <td className="num">{formatMs(r.dedupe.hashSetBaseline.medianMs)}</td>
                    <td className="num">
                      {formatInt(r.dedupe.duplicatesBloom)} {r.dedupe.answersAgree ? '✓' : '✗'}
                    </td>
                    <td className="num">
                      {formatInt(r.dedupe.bloomBits)} / {r.dedupe.bloomHashes}
                    </td>
                    <td className="num">{formatBytes(r.dedupe.bloomBytes)}</td>
                    <td className="num">{formatInt(r.dedupe.screenedOut)}</td>
                    <td className="num">{formatInt(r.dedupe.falsePositives)}</td>
                    <td className="num">
                      {formatPct(r.dedupe.observedFpRate)} / {formatPct(r.dedupe.theoreticalFpp)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h3>Trie fuzzy search vs brute-force Levenshtein</h3>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th className="num">Records</th>
                  <th className="num">Titles</th>
                  <th className="num">Trie nodes</th>
                  <th className="num">Queries</th>
                  <th className="num">Trie</th>
                  <th className="num">Brute force</th>
                  <th className="num">Speed-up</th>
                  <th>Same results</th>
                </tr>
              </thead>
              <tbody>
                {report.results.map((r) => (
                  <tr key={r.records}>
                    <td className="num">{formatInt(r.records)}</td>
                    <td className="num">{formatInt(r.fuzzy.indexedTitles)}</td>
                    <td className="num">{formatInt(r.fuzzy.trieNodes)}</td>
                    <td className="num">{r.fuzzy.queries}</td>
                    <td className="num">{formatMs(r.fuzzy.trie.medianMs)}</td>
                    <td className="num">{formatMs(r.fuzzy.bruteForce.medianMs)}</td>
                    <td className="num strong">{r.fuzzy.speedup.toFixed(1)}×</td>
                    <td>{r.fuzzy.answersAgree ? '✓ identical' : '✗ differ'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h3>Detection accuracy on planted duplicates</h3>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th className="num">Records</th>
                  <th className="num">Planted exact / near</th>
                  <th className="num">Precision</th>
                  <th className="num">Recall</th>
                  <th className="num">F1</th>
                  <th className="num">Exact recall</th>
                  <th className="num">Near recall</th>
                  <th className="num">Full scan</th>
                </tr>
              </thead>
              <tbody>
                {report.results.map((r) => (
                  <tr key={r.records}>
                    <td className="num">{formatInt(r.records)}</td>
                    <td className="num">
                      {formatInt(r.plantedExact)} / {formatInt(r.plantedNear)}
                    </td>
                    <td className="num">{formatPct(r.accuracy.accuracy.precision)}</td>
                    <td className="num">{formatPct(r.accuracy.accuracy.recall)}</td>
                    <td className="num">{formatPct(r.accuracy.accuracy.f1)}</td>
                    <td className="num">{formatPct(r.accuracy.accuracy.exactRecall)}</td>
                    <td className="num">{formatPct(r.accuracy.accuracy.nearRecall)}</td>
                    <td className="num">{formatMs(r.accuracy.scanMs)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
