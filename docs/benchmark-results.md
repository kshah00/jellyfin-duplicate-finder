# Benchmark results

- Run at: 2026-11-25T10:59:31.376952Z (97.8 s total)
- Machine: Apple M4, Mac OS X 26.6.2, aarch64, 10 logical CPUs, max heap 4096 MB
- Java: 25.0.2 (Homebrew, OpenJDK 64-Bit Server VM 25.0.2)
- Config: seed 42, fpRate 0.0100, max edit distance 2, 200 fuzzy queries, 2 warm-up + 5 measured runs (median reported)

## Exact duplicates: Bloom filter + exact check vs HashSet only

| records | Bloom+check median ms | HashSet median ms | duplicates (Bloom / HashSet) | bits m | hashes k | Bloom bytes | screened out | possibly present | false positives | observed FP rate | theoretical FPP |
|---:|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1,000 | 0.602 | 0.053 | 111 / 111 (agree) | 9,586 | 7 | 1,200 | 889 | 111 | 0 | 0.0000 | 0.0100 |
| 10,000 | 2.886 | 0.565 | 1098 / 1098 (agree) | 95,851 | 7 | 11,984 | 8,897 | 1,103 | 5 | 0.0006 | 0.0100 |
| 100,000 | 30.858 | 3.823 | 10574 / 10574 (agree) | 958,506 | 7 | 119,816 | 89,347 | 10,653 | 79 | 0.0009 | 0.0100 |

## Fuzzy search: trie with pruning vs brute-force Levenshtein

| records | distinct titles | trie nodes | build ms | queries | trie median ms | brute force median ms | speed-up | results agree |
|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1,000 | 426 | 5,990 | 1.1 | 200 | 6.12 | 63.76 | 10.4x | yes |
| 10,000 | 4,266 | 59,160 | 1.5 | 200 | 11.58 | 859.08 | 74.2x | yes |
| 100,000 | 41,916 | 541,574 | 17.5 | 200 | 36.45 | 9516.21 | 261.1x | yes |

## Detection accuracy on planted duplicates (pairwise)

| records | planted exact | planted near | groups found | precision | recall | F1 | exact recall | near recall | full scan ms |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1,000 | 111 | 48 | 121 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 9.1 |
| 10,000 | 1,098 | 555 | 1,320 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 59.6 |
| 100,000 | 10,574 | 5,742 | 13,394 | 0.9995 | 1.0000 | 0.9998 | 1.0000 | 1.0000 | 1443.1 |
