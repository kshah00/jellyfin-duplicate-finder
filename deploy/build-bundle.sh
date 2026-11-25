#!/usr/bin/env bash
# Builds one Elastic Beanstalk bundle: the React UI is copied into the Spring Boot jar's
# static resources, so a single Java process serves the UI and /api on the same origin.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/deploy/out"

static="$root/backend/src/main/resources/static"   # git-ignored, generated here
(cd "$root/frontend" && npm ci --no-audit --no-fund && npx vite build --outDir "$static" --emptyOutDir)
(cd "$root/backend" && mvn -q -B clean package -DskipTests)

rm -rf "$out" && mkdir -p "$out"
cp "$root/backend/target/jellyfin-duplicate-finder-1.0.0.jar" "$out/app.jar"
cp "$root/deploy/Procfile" "$out/"
cp -R "$root/deploy/.platform" "$out/"
(cd "$out" && zip -qr ../bundle.zip app.jar Procfile .platform)
echo "Bundle: $root/deploy/bundle.zip"
