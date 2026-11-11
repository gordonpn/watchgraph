"""POSIX fresh-process runner; startup includes shell and first stdout byte."""
from dataclasses import dataclass
import hashlib
import math
import os
from pathlib import Path
import platform
import shlex
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]

@dataclass(frozen=True)
class Trial:
    candidate: str
    trial: int
    first_ms: float
    total_ms: float
    rss_mib: float

def measure(command: list[str]) -> tuple[float, float, float, bytes]:
    start = time.perf_counter_ns()
    process = subprocess.Popen(['/bin/sh', '-c', 'exec ' + shlex.join(command)],
                               stdout=subprocess.PIPE, cwd=ROOT)
    assert process.stdout is not None
    first = process.stdout.read(1)
    first_ms = (time.perf_counter_ns() - start) / 1e6
    output = first + process.stdout.read()
    _, status, usage = os.wait4(process.pid, 0)
    process.returncode = os.waitstatus_to_exitcode(status)
    process.stdout.close()
    if process.returncode != 0:
        raise RuntimeError(f'Failed command: {command}')
    total_ms = (time.perf_counter_ns() - start) / 1e6
    divisor = 1024 * 1024 if platform.system() == 'Darwin' else 1024
    return first_ms, total_ms, usage.ru_maxrss / divisor, output

def percentile(values: list[float], fraction: float) -> float:
    return sorted(values)[math.ceil(len(values) * fraction) - 1]

def main() -> None:
    os.chdir(ROOT)
    Path('results').mkdir(exist_ok=True)
    jdk = Path(os.environ['JDK25_HOME'])
    java = str(jdk / 'bin/java')
    candidates: list[tuple[str, list[str], list[Path]]] = [
        ('Java', [java, '-jar', 'build/java.jar'], [Path('build/java.jar')]),
        ('Kotlin', [java, '-cp', 'build/kotlin.jar:.tools/jars/kotlin-stdlib-2.3.20.jar:.tools/jars/picocli-4.7.7.jar', 'RankKt'],
         [Path('build/kotlin.jar'), Path('.tools/jars/kotlin-stdlib-2.3.20.jar'), Path('.tools/jars/picocli-4.7.7.jar')]),
        ('Python', [str(ROOT / '.tools/venv/bin/python'), 'python/rank.py'],
         [Path('python/rank.py'), *[p for p in Path('.tools/venv/lib').rglob('*')
          if p.is_file() and '__pycache__' not in p.parts and any(
              part.startswith(('pydantic', 'annotated_types', 'typing_extensions', 'typing_inspection'))
              and not part.endswith('.dist-info') for part in p.parts)
          and not any(part.endswith('.dist-info') for part in p.parts)]]),
    ]
    trials: list[Trial] = []
    golden = Path('results/output.csv')
    reference: bytes | None = golden.read_bytes() if golden.exists() else None
    # Interleave candidates to reduce ordering/host load bias. No warmup runs.
    for trial in range(1, 11):
        for name, command, _ in candidates:
            first_ms, total_ms, rss_mib, output = measure(command)
            if reference is None:
                reference = output
            if output != reference or len(output.splitlines()) != 1000:
                raise AssertionError(f'{name} trial {trial}: output mismatch')
            trials.append(Trial(name, trial, first_ms, total_ms, rss_mib))
    assert reference is not None
    Path('results/output.csv').write_bytes(reference)
    rows = ['candidate,trial,first_ms,total_ms,peak_rss_mib']
    rows.extend(f'{t.candidate},{t.trial},{t.first_ms:.6f},{t.total_ms:.6f},{t.rss_mib:.6f}' for t in trials)
    Path('results/trials.csv').write_text('\n'.join(rows) + '\n')
    report = [f'Host: {platform.platform()}; {platform.machine()}; {os.cpu_count()} logical CPUs.',
              f'Output SHA-256: `{hashlib.sha256(reference).hexdigest()}`.', '',
              '| Candidate | first stdout p50 ms | p95 ms | p99 ms | peak RSS p50 MiB | peak RSS max MiB | runtime payload bytes |',
              '|---|---:|---:|---:|---:|---:|---:|']
    for name, _, payload in candidates:
        subset = [t for t in trials if t.candidate == name]
        latency = [t.first_ms for t in subset]
        memory = [t.rss_mib for t in subset]
        size = sum(path.stat().st_size for path in payload if path.is_file())
        report.append(f'| {name} | {percentile(latency, .5):.3f} | {percentile(latency, .95):.3f} | {percentile(latency, .99):.3f} | {percentile(memory, .5):.3f} | {max(memory):.3f} | {size} |')
    Path('results/metrics.md').write_text('\n'.join(report) + '\n')
    print('\n'.join(report))

if __name__ == '__main__':
    main()
