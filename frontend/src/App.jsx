import { useState } from 'react';
import ScanForm from './components/ScanForm.jsx';
import ScanSummary from './components/ScanSummary.jsx';
import GroupsTable from './components/GroupsTable.jsx';
import SearchBox from './components/SearchBox.jsx';
import BenchmarkPanel from './components/BenchmarkPanel.jsx';

export default function App() {
  const [result, setResult] = useState(null);
  const [scanVersion, setScanVersion] = useState(0);

  return (
    <div className="page">
      <header className="masthead">
        <div>
          <p className="eyebrow">Advanced Algorithms · Bloom filter · Trie · Levenshtein</p>
          <h1>Jellyfin Library Duplicate Finder</h1>
          <p className="lede">
            Upload a library export, find exact and near-duplicate titles, and see how much storage one copy per
            group would free. Read-only: nothing on disk is ever touched.
          </p>
        </div>
        <SearchBox key={scanVersion} source={result?.summary?.fileName} />
      </header>

      <section className="panel">
        <h2>
          <span className="step">1</span> Scan a library
        </h2>
        <ScanForm
          onResult={(r) => {
            setResult(r);
            setScanVersion((v) => v + 1);
          }}
        />
      </section>

      {result && (
        <>
          <ScanSummary result={result} />
          <section className="panel">
            <h2>
              <span className="step">2</span> Duplicate groups
            </h2>
            <GroupsTable groups={result.groups} truncated={result.groupsTruncated} total={result.summary.duplicateGroups} />
          </section>
        </>
      )}

      <section className="panel">
        <h2>
          <span className="step">3</span> Benchmarks
        </h2>
        <BenchmarkPanel />
      </section>

      <footer className="footer">Spring Boot + React · all algorithms implemented from scratch in Java</footer>
    </div>
  );
}
