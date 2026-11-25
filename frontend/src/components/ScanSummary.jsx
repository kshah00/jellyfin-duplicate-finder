import { formatBytes, formatInt, formatMs, formatPct } from '../format.js';

function Stat({ label, value, sub, accent }) {
  return (
    <div className={`stat ${accent ? 'accent' : ''}`}>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      {sub && <span className="stat-sub">{sub}</span>}
    </div>
  );
}

export default function ScanSummary({ result }) {
  const { summary: s, screening: b, accuracy: a } = result;
  const screenedShare = b.offered ? b.screenedOut / b.offered : 0;
  const reclaimShare = s.totalBytes ? s.reclaimableBytes / s.totalBytes : 0;

  return (
    <section className="panel">
      <h2>
        <span className="step">✓</span> {s.fileName}
      </h2>
      <div className="stats">
        <Stat
          accent
          label="Reclaimable storage"
          value={formatBytes(s.reclaimableBytes)}
          sub={`${formatPct(reclaimShare, 1)} of ${formatBytes(s.totalBytes)}`}
        />
        <Stat label="Duplicate groups" value={formatInt(s.duplicateGroups)} sub={`${s.exactGroups} exact · ${s.nearGroups} near`} />
        <Stat label="Records" value={formatInt(s.records)} sub={`${formatInt(s.uniqueKeys)} unique keys`} />
        <Stat label="Scan time" value={formatMs(s.scanMillis)} sub={`parse ${formatMs(s.parseMillis)}`} />
      </div>

      <div className="two-col">
        <div className="card">
          <h3>Bloom filter screening</h3>
          <div className="funnel">
            <div className="funnel-bar">
              <span className="seg screened" style={{ width: `${screenedShare * 100}%` }} />
              <span className="seg confirmed" style={{ width: `${(b.confirmedDuplicates / b.offered) * 100}%` }} />
              <span className="seg fp" style={{ width: `${Math.max((b.falsePositives / b.offered) * 100, b.falsePositives ? 0.6 : 0)}%` }} />
            </div>
            <div className="legend">
              <span><i className="screened" /> screened out {formatInt(b.screenedOut)}</span>
              <span><i className="confirmed" /> confirmed {formatInt(b.confirmedDuplicates)}</span>
              <span><i className="fp" /> false positives {formatInt(b.falsePositives)}</span>
            </div>
          </div>
          <dl className="kv">
            <dt>Possibly present → exact check</dt>
            <dd>{formatInt(b.possiblyPresent)}</dd>
            <dt>Bits m / hashes k</dt>
            <dd>
              {formatInt(b.bits)} / {b.hashes}
            </dd>
            <dt>Filter memory</dt>
            <dd>{formatBytes(b.bloomBytes)}</dd>
            <dt>Target p / theoretical / observed FP rate</dt>
            <dd>
              {formatPct(b.targetFpp, 2)} / {formatPct(b.theoreticalFpp, 2)} / {formatPct(b.observedFpRate, 2)}
            </dd>
            <dt>Seeds</dt>
            <dd className="mono small">
              {b.seed1} · {b.seed2}
            </dd>
          </dl>
        </div>

        <div className="card">
          <h3>Near-duplicate search</h3>
          <dl className="kv">
            <dt>Distinct titles in trie</dt>
            <dd>{formatInt(b.distinctTitles)}</dd>
            <dt>Key groups merged by fuzzy match</dt>
            <dd>{formatInt(b.nearPairsMerged)}</dd>
            <dt>Max edit distance</dt>
            <dd>{s.maxDistance} (capped by title length)</dd>
            <dt>Bloom + exact stage</dt>
            <dd>{formatMs(b.bloomMillis)}</dd>
            <dt>Trie fuzzy stage</dt>
            <dd>{formatMs(b.nearMillis)}</dd>
          </dl>
          {a ? (
            <>
              <h3 className="mt">Accuracy vs planted labels</h3>
              <dl className="kv">
                <dt>Pairwise precision / recall / F1</dt>
                <dd>
                  {formatPct(a.precision)} / {formatPct(a.recall)} / {formatPct(a.f1)}
                </dd>
                <dt>Exact variants found</dt>
                <dd>
                  {a.exactFound} / {a.exactVariants} ({formatPct(a.exactRecall, 1)})
                </dd>
                <dt>Near variants found</dt>
                <dd>
                  {a.nearFound} / {a.nearVariants} ({formatPct(a.nearRecall, 1)})
                </dd>
              </dl>
            </>
          ) : (
            <p className="muted small mt">
              Upload a synthetic library (it has <code>dup_group</code> labels) to see precision and recall.
            </p>
          )}
        </div>
      </div>
    </section>
  );
}
