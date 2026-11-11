# Commit message policy

Every commit introduced by a pull request must use:

```text
type(scope)!: lowercase description
```

The scope and breaking-change `!` are optional. Types are `feat`, `fix`, `docs`,
`style`, `refactor`, `test`, `chore`, and `ci`, following the repository's
[workflow guide](../GITHUB_ACTIONS.md). Scope names use lowercase letters,
digits, dots, underscores, slashes, or hyphens. Subjects must have a nonempty
lowercase description, be at most 100 characters, and have no final period or
trailing whitespace. A body can include `BREAKING CHANGE:` details.

WIP/plain messages fail. Generated-with/by attribution naming Claude, Codex,
ChatGPT, or Copilot, and co-author trailers naming those tools, are prohibited.
Human `Co-authored-by:` trailers remain allowed. This explicitly defines the
attribution rule requested in issue #34 without prohibiting human credit.
There are no merge or bot exemptions: use conventional merge subjects or rebase,
and configure bots to use allowed types. The new validator does not rewrite
historical commits already in the base branch.

The `conventional-commits` job checks **all** commits in the base-to-head range,
including intermediate commits and merge commits, and identifies failing SHAs.
It needs no Node dependencies or secrets. It checks out the PR head
and tests its validator under the ordinary `pull_request` event, with read-only
permissions, no secrets, and no persisted Git credentials. Validator changes
therefore require the same review as changes to build and test workflows.

Run the fixture tests locally:

```sh
python3 -m unittest discover -s .github/scripts -p 'test_*.py'
python3 .github/scripts/validate_commits.py origin/main HEAD
```

The validator tests include valid scopes and breaking syntax, invalid subjects
and attribution, human co-authors, and a real temporary Git history with an
invalid intermediate commit followed by a valid head commit. Branch protection
must be configured separately if this job should be required for merging.
