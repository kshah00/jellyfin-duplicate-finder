import { useRef, useState } from 'react';
import { fetchGenerated, fetchSample, scanLibrary } from '../api.js';

export default function ScanForm({ onResult }) {
  const [file, setFile] = useState(null);
  const [fpRate, setFpRate] = useState('0.01');
  const [maxDistance, setMaxDistance] = useState('2');
  const [seed, setSeed] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const inputRef = useRef(null);

  async function run(target) {
    setBusy(true);
    setError(null);
    try {
      onResult(await scanLibrary(target, { fpRate, maxDistance, seed }));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  async function loadAndScan(loader, name) {
    setBusy(true);
    setError(null);
    try {
      const csv = await loader();
      const f = new File([csv], name, { type: 'text/csv' });
      setFile(f);
      await run(f);
    } catch (e) {
      setError(e);
      setBusy(false);
    }
  }

  function submit(e) {
    e.preventDefault();
    if (!file) {
      setError(new Error('Choose a CSV file first, or use one of the sample libraries.'));
      return;
    }
    run(file);
  }

  return (
    <form className="scan-form" onSubmit={submit}>
      <label
        className={`dropzone ${file ? 'has-file' : ''}`}
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          if (e.dataTransfer.files[0]) setFile(e.dataTransfer.files[0]);
        }}
      >
        <input ref={inputRef} type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.target.files[0] || null)} />
        <span className="dropzone-title">{file ? file.name : 'Drop a Jellyfin export CSV here or click to browse'}</span>
        <span className="dropzone-hint">
          {file
            ? `${(file.size / 1024).toFixed(1)} KB`
            : 'Columns: type, title, year, season, episode, path, size, runtime'}
        </span>
      </label>

      <div className="params">
        <label>
          Bloom false-positive rate
          <input type="number" step="any" min="0" max="1" value={fpRate} onChange={(e) => setFpRate(e.target.value)} />
        </label>
        <label>
          Max edit distance
          <select value={maxDistance} onChange={(e) => setMaxDistance(e.target.value)}>
            {[0, 1, 2, 3].map((d) => (
              <option key={d} value={d}>
                {d}
                {d === 0 ? ' (exact only)' : d === 2 ? ' (default)' : ''}
              </option>
            ))}
          </select>
        </label>
        <label>
          Hash seed <em>(optional)</em>
          <input type="number" placeholder="random" value={seed} onChange={(e) => setSeed(e.target.value)} />
        </label>
      </div>

      <div className="actions">
        <button type="submit" className="primary" disabled={busy}>
          {busy ? 'Scanning…' : 'Scan library'}
        </button>
        <span className="or">or try</span>
        <button type="button" disabled={busy} onClick={() => loadAndScan(fetchSample, 'sample-library.csv')}>
          Sample library (41 rows)
        </button>
        {[1000, 10000, 100000].map((n) => (
          <button
            key={n}
            type="button"
            disabled={busy}
            onClick={() => loadAndScan(() => fetchGenerated(n), `synthetic-${n}.csv`)}
          >
            Synthetic {n.toLocaleString('en-US')}
          </button>
        ))}
      </div>

      {error && (
        <div className="error" role="alert">
          <strong>{error.message}</strong>
          {error.details?.length > 0 && (
            <ul>
              {error.details.map((d) => (
                <li key={d}>{d}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </form>
  );
}
