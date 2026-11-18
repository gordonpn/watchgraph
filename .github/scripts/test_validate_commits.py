import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("validate_commits", Path(__file__).with_name("validate_commits.py"))
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class CommitPolicyTests(unittest.TestCase):
    def test_valid_messages(self):
        for message in ["fix: handle missing cache", "feat(cli): add --json output", "refactor!: remove old adapter", "feat(core)!: add api\n\nBREAKING CHANGE: old api removed", "docs: clarify setup\n\nCo-authored-by: Human Contributor <human@example.com>"]:
            with self.subTest(message=message):
                self.assertEqual([], validator.validate_message(message))

    def test_invalid_messages(self):
        for message in ["", "WIP changes", "fix: ", "Fix: update cache", "feat: Add cache", "feat(): add cache", "fix: update cache.", "fix: " + "x" * 100, "chore: update cache\n\nGenerated with Claude Code", "chore: update cache\n\n🤖 Generated with Claude Code", "chore: update cache\n\nCo-authored-by: OpenAI Codex <codex@openai.com>", "chore: update cache\n\nCo-authored-by: Copilot <copilot@users.noreply.github.com>"]:
            with self.subTest(message=message):
                self.assertTrue(validator.validate_message(message))

    def test_range_checks_every_commit_and_reports_offending_sha(self):
        with tempfile.TemporaryDirectory() as directory:
            def git(*args):
                return subprocess.check_output(["git", "-C", directory, *args], text=True).strip()
            git("init", "-q")
            git("config", "user.name", "Test")
            git("config", "user.email", "test@example.com")
            git("commit", "--allow-empty", "-qm", "chore: baseline")
            base = git("rev-parse", "HEAD")
            git("commit", "--allow-empty", "-qm", "plain invalid message")
            bad = git("rev-parse", "HEAD")
            git("commit", "--allow-empty", "-qm", "fix: valid latest commit")
            result = subprocess.run(["python3", str(Path(validator.__file__).resolve()), base, "HEAD"], cwd=directory, text=True, capture_output=True)
            self.assertEqual(1, result.returncode)
            self.assertIn(bad[:12], result.stdout)
            self.assertIn("Checked 2 commits", result.stdout)


if __name__ == "__main__":
    unittest.main()
