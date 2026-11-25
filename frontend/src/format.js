export function formatBytes(bytes) {
  if (bytes == null) return '—';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let v = bytes;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(i === 0 ? 0 : v >= 100 ? 0 : 1)} ${units[i]}`;
}

export const formatInt = (n) => (n == null ? '—' : n.toLocaleString('en-US'));

export const formatMs = (ms) =>
  ms == null ? '—' : ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : ms >= 10 ? `${ms.toFixed(1)} ms` : `${ms.toFixed(3)} ms`;

export const formatPct = (x, digits = 2) => (x == null ? '—' : `${(x * 100).toFixed(digits)}%`);
