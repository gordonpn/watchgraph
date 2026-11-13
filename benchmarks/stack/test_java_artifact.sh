#!/usr/bin/env bash
# Integration regression: an edited Java source must invalidate golden output.
set -euo pipefail
cd "$(dirname "$0")"
: "${JDK25_HOME:?Set JDK25_HOME to a JDK 25 installation}"
root="$PWD"
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
cp -R java kotlin python results pyrightconfig.json "$scratch/"
ln -s "$root/.tools" "$scratch/.tools"
cd "$scratch"
mkdir -p build/java
"$JDK25_HOME/bin/javac" -Xlint:all -Werror -d build/java java/Rank.java
"$JDK25_HOME/bin/jar" --create --file build/java.jar --main-class Rank -C build/java .
"$JDK25_HOME/bin/java" -jar build/java.jar > baseline.csv
cmp baseline.csv results/output.csv
# Leave the golden JAR in place, then change a score in its source.
sed 's/0.0001 \* popularity/0.0002 * popularity/' java/Rank.java > java/Rank.java.tmp
mv java/Rank.java.tmp java/Rank.java
.tools/venv/bin/python python/audit.py
"$JDK25_HOME/bin/java" -jar build/java.jar > changed.csv
if cmp -s baseline.csv changed.csv; then
    echo 'audit left the Java artifact stale' >&2
    exit 1
fi
if .tools/venv/bin/python python/measure.py > measure.log 2>&1; then
    echo 'measurement accepted changed Java output against the golden file' >&2
    exit 1
fi
grep -q 'Java trial 1: output mismatch' measure.log
echo 'Java source edit rebuilt and rejected against golden output'
