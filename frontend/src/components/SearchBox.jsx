import { useEffect, useRef, useState } from 'react';
import { search } from '../api.js';

export default function SearchBox({ source }) {
  const [q, setQ] = useState('');
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [active, setActive] = useState(-1);
  const [open, setOpen] = useState(false);
  const boxRef = useRef(null);

  useEffect(() => {
    if (!q.trim()) {
      setData(null);
      return;
    }
    const ctrl = new AbortController();
    const t = setTimeout(async () => {
      try {
        setData(await search(q, { signal: ctrl.signal }));
        setError(null);
        setActive(-1);
      } catch (e) {
        if (e.name !== 'AbortError') setError(e.message);
      }
    }, 120);
    return () => {
      clearTimeout(t);
      ctrl.abort();
    };
  }, [q]);

  useEffect(() => {
    const close = (e) => boxRef.current && !boxRef.current.contains(e.target) && setOpen(false);
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const suggestions = data?.suggestions ?? [];

  function onKey(e) {
    if (!suggestions.length) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((a) => (a + 1) % suggestions.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((a) => (a <= 0 ? suggestions.length - 1 : a - 1));
    } else if (e.key === 'Enter' && active >= 0) {
      setQ(suggestions[active].displayTitle);
      setOpen(false);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  }

  return (
    <div className="search" ref={boxRef}>
      <label className="search-label" htmlFor="search">
        Search {source ? <em>{source}</em> : 'the sample library'}
      </label>
      <input
        id="search"
        type="search"
        autoComplete="off"
        placeholder="Try “the ma” or “interstelar”"
        value={q}
        onChange={(e) => {
          setQ(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={onKey}
        role="combobox"
        aria-expanded={open && suggestions.length > 0}
        aria-controls="search-list"
      />
      {open && q.trim() && (
        <ul className="suggestions" id="search-list" role="listbox">
          {error && <li className="error-inline">{error}</li>}
          {!error && data && suggestions.length === 0 && <li className="muted">No titles within 2 edits.</li>}
          {suggestions.map((s, i) => (
            <li
              key={s.title}
              role="option"
              aria-selected={i === active}
              className={i === active ? 'active' : ''}
              onMouseDown={() => {
                setQ(s.displayTitle);
                setOpen(false);
              }}
            >
              <span className="s-title">{s.displayTitle}</span>
              <span className="s-meta">
                {s.year && <span>{s.year}</span>}
                {s.copies > 1 && <span className="badge near">{s.copies} copies</span>}
                <span className={`badge ${s.matchType === 'PREFIX' ? 'prefix' : 'fuzzy'}`}>
                  {s.matchType === 'PREFIX' ? 'prefix' : `fuzzy · ${s.distance}`}
                </span>
              </span>
            </li>
          ))}
          {data && (
            <li className="s-footer">
              {data.indexedTitles.toLocaleString('en-US')} titles in trie · query normalized to “{data.normalizedQuery}”
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
