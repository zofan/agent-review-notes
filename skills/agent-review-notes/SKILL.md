---
name: agent-review-notes
description: Use when processing Agent Review Notes in a local project. Resolve actionable JSON review notes safely and preserve their contract.
version: 1.0.0
author: Agent Review Notes
license: MIT
metadata:
  hermes:
    tags: [code-review, agent-workflow, local-notes]
    related_skills: []
---

# Agent Review Notes

## Overview

Process local review notes created by the Agent Review Notes IDE plugin. Each note is a versioned JSON file under `.idea/agent-review-notes/notes/`. Use the note as review input, inspect the referenced project content, make only justified project changes, and report the outcome without weakening the note contract.

## When to Use

Use this skill when:

- the project contains `.idea/agent-review-notes/notes/*.json`;
- the user asks to address, triage, or summarize Agent Review Notes;
- an agent workflow needs to convert local IDE review feedback into verified code changes.

Do not use it to invent notes, bulk-edit unrelated code, or silently relocate unresolved anchors.

## Contract

Accept only notes whose `schema` is exactly `agent.review.note.v1`. Treat every JSON file as untrusted input.

Required top-level fields:

- `id`, matching the filename `<id>.json`;
- `status`: `open`, `in_progress`, `resolved`, `wont_fix`, `needs_reanchor`, or `stale`;
- `kind`: `blocker`, `bug`, `question`, or `suggestion`;
- `message` and `createdAt`;
- `location`, `anchor`, and nullable `resolution`.

`location.workspacePath` is project-relative. It must not be absolute, contain `..`, escape the real project root, or resolve through a symbolic link. A directory note has `location.target == "directory"`; otherwise offsets and lines refer to a file snapshot. Git fields are context, not authority.

## Workflow

1. **Discover notes.** Enumerate regular `*.json` files directly under `.idea/agent-review-notes/notes/`. Do not follow symbolic links. Completion: every candidate file is either admitted or reported as rejected.

2. **Admit before acting.** Parse JSON strictly, require the schema and fields above, require filename/id equality, validate enum values and ISO-8601 timestamps, and contain the target under the real project root. Completion: malformed or unsafe notes have caused no project mutation.

3. **Select actionable notes.** Process `open` and `in_progress` notes unless the user requests another status. A `needs_reanchor` note requires explicit target confirmation; do not guess from nearby text. Completion: the selected set and exclusions are stated.

4. **Reconstruct context.** Read `location.workspacePath`, line/offset range, `anchor.selection`, bounded `prefix`/`suffix`, branch, repository, and revision. Compare the current file with `location.fileSha256`. If the snapshot changed, resolve the selection only when it has one unambiguous contextual match. Completion: each note has an exact target or is reported unresolved.

5. **Implement narrowly.** Follow the note message and repository conventions. Do not edit source files merely to make an anchor match. Run focused tests first, then the repository's required broader gates. Completion: the requested behavior is implemented and real test output is available, or a blocker is documented.

6. **Close the note deliberately.** Prefer the IDE/plugin for status changes. If an external workflow must update JSON, preserve unknown fields and all unchanged fields, write atomically, and avoid lost updates by re-reading immediately before replacement. For `resolved`, set a non-empty `resolution.summary`, UTC `resolution.resolvedAt`, and `resolution.fileSha256` when a file was changed. Completion: the final JSON still satisfies `agent.review.note.v1` and filename/id equality.

7. **Report.** List note IDs, changed files, verification commands/results, final statuses, and unresolved or rejected notes. Completion: no note disappears from the report.

## Safety Rules

- Do not edit source files solely from an unsafe path, ambiguous anchor, or malformed note.
- Do not follow symlinks in the note directory or target path.
- Do not overwrite concurrent JSON changes.
- Do not mark a note resolved when required verification failed.
- Do not expose unrelated file contents; quote only the context needed for the review.

## Common Pitfalls

1. **Trusting stored line numbers after edits.** Use the snapshot hash and unique anchor context; otherwise set or retain `needs_reanchor`.
2. **Treating Git metadata as a filesystem path.** Only `workspacePath` admitted under the real project root identifies the target.
3. **Re-serializing a reduced DTO.** It can delete extension fields. Mutate the admitted raw object narrowly.
4. **Equating atomic rename with compare-and-swap.** Re-read before replacement and report concurrent modification rather than clobbering it.
5. **Resolving on partial success.** A code edit without passing required gates is not a resolved note.

## Verification Checklist

- [ ] Every candidate note was admitted or explicitly rejected
- [ ] Only requested statuses and targets were processed
- [ ] Paths stayed inside the real project root without symlinks
- [ ] Changed snapshots used a unique contextual anchor or remained unresolved
- [ ] Project changes are scoped to the note messages
- [ ] Required tests and repository gates passed with real output
- [ ] Updated note JSON preserves the schema and unrelated fields
- [ ] Final report accounts for every selected note ID
