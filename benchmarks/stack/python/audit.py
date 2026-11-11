"""Measure full compiler/type-check invocations and retain deliberate-error diagnostics."""
import os
from pathlib import Path
import subprocess
import time
from measure import ROOT

def main() -> None:
    os.chdir(ROOT)
    Path('results').mkdir(exist_ok=True)
    jdk = Path(os.environ['JDK25_HOME'])
    kotlin = [str(jdk / 'bin/java'), '-cp', '.tools/jars/*',
              'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
              '-Werror', '-jvm-target', '25', '-classpath',
              '.tools/jars/kotlin-stdlib-2.3.20.jar:.tools/jars/picocli-4.7.7.jar']
    checks: list[tuple[str, list[str], Path, str, list[str]]] = [
        ('Java', [str(jdk / 'bin/javac'), '-Xlint:all', '-Werror', '-d', 'build/java', 'java/Rank.java'],
         Path('build/Probe.java'), 'class Probe { String score = 1; }\n',
         [str(jdk / 'bin/javac'), '-d', 'build/java', 'build/Probe.java']),
        ('Kotlin', [*kotlin, '-d', 'build/kotlin.jar', 'kotlin/Rank.kt'],
         Path('build/Probe.kt'), 'val score: String = 1\n',
         [*kotlin, '-d', 'build/probe.jar', 'build/Probe.kt']),
        ('Python', ['.tools/venv/bin/pyright', '--project', 'pyrightconfig.json'],
         Path('build/probe.py'), 'score: str = 1\n',
         ['.tools/venv/bin/pyright', '--project', 'pyrightconfig.json', 'build/probe.py']),
    ]
    rows = ['| Candidate | full check / compile ms | deliberate error check ms | rejected |',
            '|---|---:|---:|---|']
    for name, command, probe, source, negative in checks:
        start = time.perf_counter_ns()
        valid = subprocess.run(command, capture_output=True, text=True)
        valid_ms = (time.perf_counter_ns() - start) / 1e6
        if valid.returncode:
            raise RuntimeError(valid.stdout + valid.stderr)
        probe.write_text(source)
        start = time.perf_counter_ns()
        invalid = subprocess.run(negative, capture_output=True, text=True)
        invalid_ms = (time.perf_counter_ns() - start) / 1e6
        probe.unlink()
        if invalid.returncode == 0:
            raise AssertionError(f'{name}: missed incompatible assignment')
        Path(f'results/{name.lower()}-diagnostics.txt').write_text(invalid.stdout + invalid.stderr)
        rows.append(f'| {name} | {valid_ms:.3f} | {invalid_ms:.3f} | yes |')
    Path('results/types.md').write_text('\n'.join(rows) + '\n')
    print('\n'.join(rows))

if __name__ == '__main__':
    main()
