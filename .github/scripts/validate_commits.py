"""Validate every commit in a PR range without executing contributor code."""
import re
import subprocess
import sys

HEADER = re.compile(r"^(feat|fix|docs|style|refactor|test|chore|ci)(\([a-z0-9][a-z0-9._/-]*\))?!?: (\S.*)$")
AI_ATTRIBUTION = re.compile(
    r"(?im)^(?:[^\w\n]*generated (?:with|by)\s+.*(?:claude|codex|chatgpt|copilot)|"
    r"co-authored-by:[^\n]*\b(?:claude|codex|chatgpt|copilot)\b)"
)


def validate_message(message: str) -> list[str]:
    subject = message.splitlines()[0] if message else ""
    errors = []
    match = HEADER.fullmatch(subject)
    if not match:
        errors.append("expected type(scope)!: lowercase description (scope and ! optional)")
    else:
        description = match.group(3)
        if description != description.lower():
            errors.append("subject description must be lowercase")
        if description.endswith(".") or description != description.rstrip():
            errors.append("subject must not end in a period or whitespace")
    if len(subject) > 100:
        errors.append("subject exceeds 100 characters")
    if AI_ATTRIBUTION.search(message):
        errors.append("remove generated-by AI attribution; human co-author trailers are allowed")
    return errors


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True)


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: validate_commits.py BASE HEAD", file=sys.stderr)
        return 2
    base = git("rev-parse", "--verify", sys.argv[1] + "^{commit}").strip()
    head = git("rev-parse", "--verify", sys.argv[2] + "^{commit}").strip()
    commits = git("rev-list", "--reverse", f"{base}..{head}").splitlines()
    failed = False
    for sha in commits:
        errors = validate_message(git("show", "-s", "--format=%B", sha).rstrip("\n"))
        for error in errors:
            # Escape control characters in contributor-supplied messages and never
            # emit their contents as GitHub workflow commands.
            print(f"{sha[:12]}: {error}")
            failed = True
    print(f"Checked {len(commits)} commits; {'FAILED' if failed else 'passed'}")
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
