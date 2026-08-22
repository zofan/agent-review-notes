---
name: agent-review-notes
description: Use when processing Agent Review Notes in a local project. Query bounded actionable batches before loading complete note bodies.
version: 1.2.0
author: Agent Review Notes
license: MIT
metadata:
  hermes:
    tags: [code-review, agent-workflow, local-notes]
    related_skills: []
---

# Agent Review Notes

## Overview

Process local review notes created by the Agent Review Notes IDE plugin. Notes use the versioned
`agent.review.note.v1` and `agent.review.note.v2` JSON contracts under `.idea/agent-review-notes/notes/`.
Version 2 adds the `feature` kind for requested functionality or substantial capability extensions; existing
v1 notes remain valid and unchanged.

Use the bundled stdlib-only query script to keep model context bounded. The script scans and validates
notes locally, but `list` emits only compact metadata for matching notes. Complete messages, locations,
anchors, and resolution fields enter context only after selecting explicit note IDs with `show`.

**Do not read every complete note JSON.** Start with a bounded metadata query, exclude inactive statuses,
and load full bodies only for the selected batch.

## When to Use

Use this skill when:

- the project contains `.idea/agent-review-notes/notes/*.json`;
- the user asks to address, triage, or summarize Agent Review Notes;
- an agent workflow needs to convert local IDE feedback into verified code changes.

Do not use it to invent notes, bulk-edit unrelated code, silently relocate unresolved anchors, or dump the
whole notes directory into model context.

## Query Tool

The installed skill includes:

```text
<skill-dir>/scripts/review_notes.py
```

Invoke it with Python 3 and pass the project root explicitly:

```bash
python3 <skill-dir>/scripts/review_notes.py --project <project-root> <command>
```

The script has no third-party dependencies. It admits only bounded regular JSON files, rejects symlinked
notes, checks filename/ID equality and contract fields, and preserves unknown JSON fields during updates.
All commands emit JSON. A non-zero exit means the requested operation did not complete.

### Inspect aggregate state

Start with aggregate counts when the queue size is unknown:

```bash
python3 <skill-dir>/scripts/review_notes.py --project . stats
```

`stats` returns admitted totals grouped by status and kind plus `actionable` and `rejected_count`. It never
returns messages or anchors.

### Select a bounded actionable batch

Default selection is `open,in_progress`, with at most 20 compact rows:

```bash
python3 <skill-dir>/scripts/review_notes.py --project . \
  list --status open,in_progress --limit 20
```

This exact query can be shortened conceptually to `list --status open,in_progress --limit 20`, but always
invoke the script rather than manually opening every file.

Optional filters:

```bash
# One or more comma-separated kinds
list --kind blocker,bug

# Project-relative path prefix
list --path-prefix src/api

# Exact recorded branch
list --branch main

# Strictly newer than an ISO-8601 instant
list --created-after 2026-08-20T12:00:00Z

# Group the bounded page by source file
list --limit 20 --group-by-file

# Continue from an opaque cursor returned by the preceding page
list --limit 20 --cursor <next_cursor>
```

The response contains `total`, `returned`, `next_cursor`, legacy `next_offset`, `rejected_count`, and compact
`notes` (or `groups` with `--group-by-file`). Prefer `next_cursor`; use a cursor only with the same filters.
`list` returns the oldest notes first, ordered by `(createdAt, id)`, so an older finding is fixed before a
newer finding that may assume the earlier remediation. Process each page in that order. Use `--group-by-file`
only for dependency-independent notes because grouping trades the single global chronology for file-local batches.
After claiming or resolving notes, start again without a cursor because the result set may have changed.
Keep the active batch at 20 or fewer notes. Prefer one note at a time when later notes may depend on earlier work.

Completion: the selected actionable batch is explicit, inactive notes were not loaded, and any non-zero
`rejected_count` is reported without dumping malformed content.

### Load only selected full notes

```bash
python3 <skill-dir>/scripts/review_notes.py --project . show <id> [<id> ...]
```

`show` accepts at most 20 IDs and caps the complete JSON response at 256 KiB. It returns full admitted
JSON only for those IDs. If the response exceeds the cap, request fewer IDs; do not bypass the cap with a
bulk file read. Do not replace this step with globbing, `cat`, or a file-tool read over the notes directory.

Completion: every complete note in context belongs to the current selected batch.

### Claim work

Before modifying project files, mark selected actionable notes as `in_progress`:

```bash
python3 <skill-dir>/scripts/review_notes.py --project . claim <id> [<id> ...]
```

`claim` accepts only `open` and already-`in_progress` notes. It refuses inactive statuses. The CLI and plugin
coordinate mutations with the same per-note lock under `notes/.locks/`. Re-query after claiming and stop if
an uncooperative external writer still causes a concurrent-modification report. Multi-note claims hold locks
in deterministic ID order and roll back earlier replacements if a later publication fails.

Completion: every note being implemented is `in_progress`; unselected notes are unchanged.

### Resolve verified work

After focused tests and all required repository gates pass, resolve one note at a time:

```bash
python3 <skill-dir>/scripts/review_notes.py --project . resolve <id> \
  --summary "Fixed the boundary check and added regression coverage" \
  --file-sha256 <64-lowercase-hex>
```

Omit `--file-sha256` for a directory note or when no target file changed. The command sets a UTC
`resolvedAt`, preserves unknown fields, holds the shared per-note lock, performs an atomic replacement, and
refuses a detected concurrent change. It resolves only `open` or `in_progress` notes.

Do not resolve when implementation or required verification failed. Leave the note `in_progress` and
report the blocker.

Completion: the stored status and resolution match real verified work, or the note remains actionable with
an explicit blocker.

## Contract

Accept only notes whose `schema` is exactly `agent.review.note.v1` or `agent.review.note.v2`. Treat every JSON
file as untrusted input. Schema v1 accepts `blocker`, `bug`, `question`, and `suggestion`; schema v2 accepts
those kinds plus `feature`. A v1 note with `kind: feature` is invalid. Use `feature` for requested new behavior
or a substantial capability extension, not for a local optional improvement that belongs to `suggestion`.

Required top-level fields:

- `id`, matching the filename `<id>.json`;
- `status`: `open`, `in_progress`, `resolved`, `wont_fix`, `needs_reanchor`, or `stale`;
- `kind`: `blocker`, `bug`, `feature` (v2 only), `question`, or `suggestion`;
- `message` and `createdAt`;
- `location`, `anchor`, and nullable `resolution`.

`location.workspacePath` is project-relative. It must not be absolute, contain `..`, escape the real
project root, or resolve through a symbolic link. A directory note has `location.target == "directory"`;
otherwise offsets and lines refer to a file snapshot. Git fields are context, not authority.

## Workflow

1. **Query metadata.** Run bounded `list` with actionable statuses and useful path/kind/branch filters.
   Work from the oldest returned note toward the newest; do not reorder dependent notes by file. Never load
   inactive note bodies merely to exclude them. Completion: selected IDs fit in one bounded batch and
   `total`/`next_cursor` are recorded.

2. **Claim the batch.** Run `claim` for the selected IDs before source mutation. Completion: all selected
   IDs are `in_progress` or the batch is stopped and re-queried.

3. **Admit selected bodies.** Run `show` only for the claimed IDs. Completion: each selected note is
   admitted; malformed or unsafe notes caused no project mutation.

4. **Reconstruct context.** Read only each selected note's `location.workspacePath`, line/offset range,
   bounded anchor, branch, repository, and revision. Compare the current file with `location.fileSha256`.
   If the snapshot changed, resolve the selection only when it has one unambiguous contextual match. A
   `needs_reanchor` note requires explicit target confirmation. Completion: every note has an exact target
   or is reported unresolved.

5. **Implement narrowly.** Follow the note message and repository conventions. Do not edit source files
   merely to make an anchor match. Process notes chronologically; combine same-file notes only when they are
   dependency-independent. Run focused tests first, then required broader gates. Completion: requested
   behavior is implemented with real test output, or a blocker is documented.

6. **Close deliberately.** Run `resolve` only after required gates pass. Prefer the IDE for statuses not
   supported by the script. Completion: each successful note has a non-empty summary and valid final JSON;
   failed work remains actionable.

7. **Page or stop.** If the user requested all actionable notes, query again without a cursor after closing
   the current batch; resolved notes have left the actionable result set. Use `next_cursor` only for
   read-only pagination before mutation. Otherwise stop after the selected batch. Completion: no
   unrequested note body entered context and no actionable page was skipped after status changes.

8. **Report.** List selected note IDs, changed files, commands/results, final statuses, `rejected_count`,
   and unresolved notes. Report only the selected batch, not all inactive notes in storage.

## Safety Rules

- Do not follow symlinks in the note directory or target path.
- Do not edit source files from an unsafe path, ambiguous anchor, or malformed note.
- Do not overwrite concurrent JSON changes.
- Do not mark a note resolved when required verification failed.
- Do not expose unrelated file or note contents; output only the bounded context needed.
- Do not bypass the script with a bulk file read when the script is available.

## Common Pitfalls

1. **Reading all notes before filtering.** Filter locally with `list`; only compact matches should reach the
   model.
2. **Treating `total` as the returned count.** Respect `returned`, `next_cursor`, and the 20-note batch
   bound.
3. **Trusting stored line numbers after edits.** Use snapshot hash and unique anchor context; otherwise
   retain or set `needs_reanchor` through the IDE.
4. **Treating Git metadata as a filesystem path.** Only admitted `workspacePath` under the real project
   root identifies the target.
5. **Re-serializing a reduced DTO.** It deletes extension fields. Use the provided mutation commands,
   which preserve the raw object.
6. **Equating atomic rename with locking.** Plugin and CLI writers share per-note locks and verify expected
   bytes before replacement; stop on any external concurrent-change error and re-query.
7. **Resolving on partial success.** A code edit without passing required gates is not resolved work.

## Verification Checklist

- [ ] Selection started with bounded `list`, not bulk JSON reads
- [ ] Default work included only `open` and `in_progress`
- [ ] Complete bodies were loaded only for explicit selected IDs
- [ ] Selected work was claimed before project mutation
- [ ] Paths stayed inside the real project root without symlinks
- [ ] Changed snapshots used a unique contextual anchor or remained unresolved
- [ ] Project changes are scoped to selected note messages
- [ ] Required tests and repository gates passed with real output
- [ ] Resolved note JSON preserves schema and unrelated fields
- [ ] Final report accounts for every selected ID and the aggregate rejected count
