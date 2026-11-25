import { Fragment, useMemo, useState } from 'react';
import { formatBytes, formatInt } from '../format.js';

const PAGE = 50;

export default function GroupsTable({ groups, truncated, total }) {
  const [kind, setKind] = useState('ALL');
  const [filter, setFilter] = useState('');
  const [open, setOpen] = useState(() => new Set());
  const [page, setPage] = useState(0);

  const rows = useMemo(() => {
    const f = filter.trim().toLowerCase();
    return groups.filter(
      (g) =>
        (kind === 'ALL' || g.kind === kind) &&
        (!f || g.normalizedTitle.includes(f) || g.items.some((i) => i.title.toLowerCase().includes(f))),
    );
  }, [groups, kind, filter]);

  const pages = Math.max(1, Math.ceil(rows.length / PAGE));
  const visible = rows.slice(page * PAGE, page * PAGE + PAGE);

  function toggle(id) {
    setOpen((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  if (groups.length === 0) {
    return <p className="muted">No duplicates found. Every title is unique at these settings.</p>;
  }

  return (
    <div>
      <div className="toolbar">
        <div className="segmented">
          {['ALL', 'EXACT', 'NEAR'].map((k) => (
            <button
              key={k}
              type="button"
              className={kind === k ? 'active' : ''}
              onClick={() => {
                setKind(k);
                setPage(0);
              }}
            >
              {k === 'ALL' ? 'All' : k === 'EXACT' ? 'Exact' : 'Near'}
            </button>
          ))}
        </div>
        <input
          className="filter"
          placeholder="Filter groups…"
          value={filter}
          onChange={(e) => {
            setFilter(e.target.value);
            setPage(0);
          }}
        />
        <span className="muted small">
          {formatInt(rows.length)} shown{truncated ? ` · top ${formatInt(groups.length)} of ${formatInt(total)} by reclaimable size` : ''}
        </span>
      </div>

      <table className="groups">
        <thead>
          <tr>
            <th />
            <th>Title</th>
            <th>Year</th>
            <th>Kind</th>
            <th className="num">Copies</th>
            <th className="num">Total</th>
            <th className="num">Reclaimable</th>
          </tr>
        </thead>
        <tbody>
          {visible.map((g) => (
            <Fragment key={g.id}>
              <tr className="group-row" onClick={() => toggle(g.id)}>
                <td className="chev">{open.has(g.id) ? '▾' : '▸'}</td>
                <td>
                  <div className="title">{g.title}</div>
                  <div className="muted mono small">
                    {g.normalizedTitle}
                    {g.season != null && ` · S${String(g.season).padStart(2, '0')}E${String(g.episode).padStart(2, '0')}`}
                  </div>
                </td>
                <td>{g.year ?? '—'}</td>
                <td>
                  <span className={`badge ${g.kind.toLowerCase()}`}>{g.kind.toLowerCase()}</span>
                </td>
                <td className="num">{g.itemCount}</td>
                <td className="num">{formatBytes(g.totalBytes)}</td>
                <td className="num strong">{formatBytes(g.reclaimableBytes)}</td>
              </tr>
              {open.has(g.id) &&
                g.items.map((i) => (
                  <tr key={`${g.id}-${i.line}`} className={`item-row ${i.keep ? 'keep' : ''}`}>
                    <td />
                    <td colSpan={3}>
                      <div>{i.title}</div>
                      <div className="muted mono small">{i.path}</div>
                    </td>
                    <td className="num small">line {i.line}</td>
                    <td className="num">{formatBytes(i.sizeBytes)}</td>
                    <td className="num">{i.keep ? <span className="badge keep">keep</span> : <span className="muted">remove</span>}</td>
                  </tr>
                ))}
            </Fragment>
          ))}
        </tbody>
      </table>

      {pages > 1 && (
        <div className="pager">
          <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>
            ← Prev
          </button>
          <span className="muted small">
            Page {page + 1} of {pages}
          </span>
          <button type="button" disabled={page >= pages - 1} onClick={() => setPage(page + 1)}>
            Next →
          </button>
        </div>
      )}
    </div>
  );
}
