#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${JDK25_HOME:?Set JDK25_HOME to a JDK 25 installation}"
mkdir -p .tools/jars build/java
export UV_CACHE_DIR="$PWD/.tools/uv-cache"
fetch() {
    local path="$1" file=".tools/jars/${1##*/}"
    if [[ ! -f "$file" ]]; then
        curl --fail --silent --show-error --location "https://repo.maven.apache.org/maven2/$path" -o "$file.tmp"
        mv "$file.tmp" "$file"
    fi
    local checksum
    checksum=$(awk -v file="$file" '$2 == file { print }' jars.sha256)
    [[ -n "$checksum" ]]
    printf '%s\n' "$checksum" | sha256sum --check --status
}
fetch org/jetbrains/kotlin/kotlin-compiler-embeddable/2.3.20/kotlin-compiler-embeddable-2.3.20.jar
fetch org/jetbrains/kotlin/kotlin-stdlib/2.3.20/kotlin-stdlib-2.3.20.jar
fetch org/jetbrains/kotlin/kotlin-script-runtime/2.3.20/kotlin-script-runtime-2.3.20.jar
fetch org/jetbrains/kotlin/kotlin-reflect/1.6.10/kotlin-reflect-1.6.10.jar
fetch org/jetbrains/kotlin/kotlin-daemon-embeddable/2.3.20/kotlin-daemon-embeddable-2.3.20.jar
fetch org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.0/kotlinx-coroutines-core-jvm-1.8.0.jar
fetch org/jetbrains/annotations/13.0/annotations-13.0.jar
fetch info/picocli/picocli/4.7.7/picocli-4.7.7.jar
[[ -d .tools/venv ]] || uv venv --python 3.12 .tools/venv
uv pip install --python .tools/venv/bin/python --require-hashes -r requirements.lock
"$JDK25_HOME/bin/javac" -Xlint:all -Werror -d build/java java/Rank.java
"$JDK25_HOME/bin/jar" --create --file build/java.jar --main-class Rank -C build/java .
"$JDK25_HOME/bin/java" -cp '.tools/jars/*' org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -Werror -jvm-target 25 \
    -classpath '.tools/jars/kotlin-stdlib-2.3.20.jar:.tools/jars/picocli-4.7.7.jar' \
    -d build/kotlin.jar kotlin/Rank.kt
.tools/venv/bin/pyright --project pyrightconfig.json
