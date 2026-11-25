// Thin wrappers around the Spring Boot REST API (proxied to :8080 by Vite in development).

async function handle(res) {
  const isJson = (res.headers.get('content-type') || '').includes('application/json');
  const body = isJson ? await res.json() : await res.text();
  if (!res.ok) {
    const err = new Error(isJson && body.error ? body.error : `Request failed (${res.status})`);
    err.details = isJson && body.details ? body.details : [];
    throw err;
  }
  return body;
}

export async function scanLibrary(file, { fpRate, maxDistance, seed }) {
  const form = new FormData();
  form.append('file', file);
  form.append('fpRate', fpRate);
  form.append('maxDistance', maxDistance);
  if (seed !== '' && seed != null) form.append('seed', seed);
  return handle(await fetch('/api/scan', { method: 'POST', body: form }));
}

export async function fetchSample() {
  return handle(await fetch('/api/sample'));
}

export async function fetchGenerated(size, seed = 42) {
  return handle(await fetch(`/api/sample/generated?size=${size}&seed=${seed}`));
}

export async function search(q, { limit = 8, maxDistance = 2, signal } = {}) {
  const params = new URLSearchParams({ q, limit, maxDistance });
  return handle(await fetch(`/api/search?${params}`, { signal }));
}

export async function runBenchmark(config) {
  return handle(
    await fetch('/api/benchmark', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config),
    }),
  );
}
