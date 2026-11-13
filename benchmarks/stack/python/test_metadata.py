"""Reproducibility metadata without running performance trials."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from measure import cpu_model, source_revision


class MetadataTests(unittest.TestCase):
    def test_linux_cpu_model(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            cpuinfo = Path(directory) / 'cpuinfo'
            cpuinfo.write_text('processor : 0\nmodel name : Example CPU 9000\n')
            self.assertEqual(cpu_model(cpuinfo), 'Example CPU 9000')

    def test_unavailable_cpu_model(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with patch('measure.platform.system', return_value='Linux'):
                self.assertEqual(cpu_model(Path(directory) / 'missing'), 'unavailable')

    def test_macos_cpu_model(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with patch('measure.platform.system', return_value='Darwin'):
                with patch('measure.subprocess.check_output', return_value='Example Apple CPU\n'):
                    self.assertEqual(cpu_model(Path(directory) / 'missing'), 'Example Apple CPU')

    def test_git_revision_clean_and_dirty(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            source = root / 'source.txt'
            source.write_text('original\n')
            subprocess.run(['git', '-C', str(root), 'add', '.'], check=True)
            subprocess.run(['git', '-C', str(root), '-c', 'user.name=Test',
                            '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'fixture'], check=True)
            revision = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            self.assertEqual(source_revision(root), revision + ' (clean)')
            subprocess.run(['git', '-C', str(root), 'config', 'status.showUntrackedFiles', 'no'], check=True)
            untracked = root / 'new-source.txt'
            untracked.write_text('new\n')
            self.assertEqual(source_revision(root), revision + ' (dirty)')
            untracked.unlink()
            source.write_text('changed\n')
            self.assertEqual(source_revision(root), revision + ' (dirty)')

    def test_source_revision_outside_git(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(source_revision(Path(directory)), 'unavailable (not a Git checkout)')


if __name__ == '__main__':
    unittest.main()
