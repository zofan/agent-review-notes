#!/usr/bin/env python3
"""Bounded, metadata-first access to Agent Review Notes."""

from __future__ import annotations

import argparse
import base64
import json
import math
import os
import re
import stat
import subprocess
import sys
import tempfile
from contextlib import ExitStack, contextmanager
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path, PurePosixPath
from typing import Any, Callable, NoReturn

SCHEMA_V1 = "agent.review.note.v1"
SCHEMA_V2 = "agent.review.note.v2"
STATUSES = {"open", "in_progress", "resolved", "wont_fix", "needs_reanchor", "stale"}
ACTIONABLE = {"open", "in_progress"}
KINDS_BY_SCHEMA = {
    SCHEMA_V1: {"blocker", "bug", "question", "suggestion"},
    SCHEMA_V2: {"blocker", "bug", "feature", "question", "suggestion"},
}
KINDS = set().union(*KINDS_BY_SCHEMA.values())
ID_PATTERN = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")
MAX_NOTE_BYTES = 1_048_576
MAX_SHOW = 20
MAX_LIST = 100
MAX_OUTPUT_BYTES = 262_144
MAX_ERROR_CHARACTERS = 4_096
INT_MIN = -(2**31)
INT_MAX = 2**31 - 1
INSTANT_PATTERN = re.compile(
    r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})",
)


class CliError(Exception):
    pass


class JsonArgumentParser(argparse.ArgumentParser):
    def error(self, message: str) -> NoReturn:
        raise CliError(message)


def _json_text(value: Any, level: int = 0) -> str:
    if value is None:
        return "null"
    if value is True:
        return "true"
    if value is False:
        return "false"
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, int):
        return str(value)
    if isinstance(value, Decimal):
        if not value.is_finite():
            raise ValueError("non-finite decimal cannot be serialized")
        return str(value)
    if isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("non-finite float cannot be serialized")
        return repr(value)
    indent = "  " * level
    child_indent = "  " * (level + 1)
    if isinstance(value, list):
        if not value:
            return "[]"
        return "[\n" + ",\n".join(child_indent + _json_text(item, level + 1) for item in value) + "\n" + indent + "]"
    if isinstance(value, dict):
        if not value:
            return "{}"
        fields = (
            child_indent + json.dumps(key, ensure_ascii=False) + ": " + _json_text(item, level + 1)
            for key, item in value.items()
        )
        return "{\n" + ",\n".join(fields) + "\n" + indent + "}"
    raise TypeError(f"unsupported JSON value: {type(value).__name__}")


def _error_json(error: BaseException) -> str:
    message = str(error)
    if len(message) > MAX_ERROR_CHARACTERS:
        message = message[:MAX_ERROR_CHARACTERS] + "..."
    return json.dumps({"error": message}, ensure_ascii=True, separators=(",", ":"))


def _object_without_duplicates(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON field: {key}")
        result[key] = value
    return result


def _reject_json_constant(value: str) -> None:
    raise ValueError(f"non-standard JSON number: {value}")


def _parse_json(raw: bytes) -> dict[str, Any]:
    if len(raw) > MAX_NOTE_BYTES:
        raise ValueError("review note exceeds the size limit")
    try:
        value = json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=_object_without_duplicates,
            parse_constant=_reject_json_constant,
            parse_float=Decimal,
        )
    except RecursionError as error:
        raise ValueError("review note JSON nesting is excessive") from error
    if not isinstance(value, dict):
        raise ValueError("review note root must be an object")
    stack: list[tuple[Any, int]] = [(value, 0)]
    while stack:
        current, depth = stack.pop()
        if depth > 64:
            raise ValueError("review note JSON exceeds nesting limit 64")
        if isinstance(current, dict):
            children = list(current.items())
            if any(any(0xD800 <= ord(character) <= 0xDFFF for character in key) for key, _ in children):
                raise ValueError("review note JSON contains an unpaired Unicode surrogate")
            stack.extend((child, depth + 1) for _, child in children)
        elif isinstance(current, list):
            stack.extend((child, depth + 1) for child in current)
        elif isinstance(current, str) and any(0xD800 <= ord(character) <= 0xDFFF for character in current):
            raise ValueError("review note JSON contains an unpaired Unicode surrogate")
        elif isinstance(current, Decimal) and not current.is_finite():
            raise ValueError("review note JSON contains a non-finite number")
        elif isinstance(current, float) and not math.isfinite(current):
            raise ValueError("review note JSON contains a non-finite number")
    return value


def _required_string(value: dict[str, Any], name: str, *, nonempty: bool = False) -> str:
    field = value.get(name)
    if not isinstance(field, str) or nonempty and not field.strip():
        raise ValueError(f"{name} must be a{' non-empty' if nonempty else ''} string")
    return field


def _optional_string(value: dict[str, Any], name: str) -> str | None:
    field = value.get(name)
    if field is not None and not isinstance(field, str):
        raise ValueError(f"{name} must be a string or null")
    return field


def _required_int(value: dict[str, Any], name: str) -> int:
    field = value.get(name)
    if isinstance(field, bool) or not isinstance(field, int) or not INT_MIN <= field <= INT_MAX:
        raise ValueError(f"{name} must be a 32-bit integer")
    return field


def _parse_instant(value: str) -> datetime:
    if INSTANT_PATTERN.fullmatch(value) is None:
        raise ValueError("timestamp must use ISO-8601 instant syntax")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise ValueError("timestamp must include a timezone")
        return parsed.astimezone(timezone.utc)
    except (ValueError, OverflowError) as error:
        raise ValueError("timestamp is outside the supported instant range") from error


def _safe_workspace_path(value: str) -> bool:
    path = PurePosixPath(value)
    return (
        value not in {"", "."}
        and "\\" not in value
        and not path.is_absolute()
        and ".." not in path.parts
        and path.as_posix() == value
    )


def _validate_note(note: dict[str, Any], expected_id: str) -> dict[str, Any]:
    schema = _required_string(note, "schema")
    allowed_kinds = KINDS_BY_SCHEMA.get(schema)
    if allowed_kinds is None:
        raise ValueError("unsupported review note schema")
    note_id = _required_string(note, "id")
    if note_id != expected_id or ID_PATTERN.fullmatch(note_id) is None:
        raise ValueError("filename and review note id do not match")
    status_value = _required_string(note, "status")
    kind = _required_string(note, "kind")
    if status_value not in STATUSES:
        raise ValueError("invalid review note status")
    if kind not in allowed_kinds:
        raise ValueError("invalid review note kind for schema")
    _required_string(note, "message", nonempty=True)
    created_at = _required_string(note, "createdAt")
    _parse_instant(created_at)

    location = note.get("location")
    anchor = note.get("anchor")
    if not isinstance(location, dict) or not isinstance(anchor, dict):
        raise ValueError("location and anchor must be objects")
    workspace_path = _required_string(location, "workspacePath")
    if not _safe_workspace_path(workspace_path):
        raise ValueError("workspacePath escapes the project")
    vcs_root = _optional_string(location, "vcsRoot")
    vcs_path = _optional_string(location, "vcsPath")
    _optional_string(location, "head")
    branch = _optional_string(location, "branch")
    _optional_string(location, "target")
    file_sha = _required_string(location, "fileSha256")
    start_offset = _required_int(location, "startOffset")
    end_offset = _required_int(location, "endOffset")
    start_line = _required_int(location, "startLine")
    end_line = _required_int(location, "endLine")
    for name in ("selection", "prefix", "suffix"):
        _required_string(anchor, name)
    _optional_string(anchor, "symbol")

    if location.get("target") == "directory":
        if (
            file_sha
            or any((start_offset, end_offset, start_line, end_line))
            or anchor["selection"]
            or anchor["prefix"]
            or anchor["suffix"]
            or anchor.get("symbol") is not None
        ):
            raise ValueError("directory note contains file coordinates or anchor")
    elif (
        location.get("target") is not None
        or SHA256_PATTERN.fullmatch(file_sha) is None
        or start_offset < 0
        or end_offset < start_offset
        or start_line < 1
        or end_line < start_line
    ):
        raise ValueError("invalid file location")

    if (vcs_root is None) != (vcs_path is None):
        raise ValueError("incomplete VCS location")
    if vcs_root is not None and vcs_path is not None:
        if (vcs_root and not _safe_workspace_path(vcs_root)) or (
            not _safe_workspace_path(vcs_path) and not (location.get("target") == "directory" and not vcs_path)
        ):
            raise ValueError("invalid VCS location")
        reconstructed = PurePosixPath(vcs_root).joinpath(vcs_path).as_posix()
        if reconstructed != workspace_path:
            raise ValueError("VCS location does not match workspacePath")
    if branch is not None and (not branch.strip() or vcs_root is None):
        raise ValueError("branch requires a non-empty value and vcsRoot")

    resolution = note.get("resolution")
    if resolution is not None:
        if not isinstance(resolution, dict):
            raise ValueError("resolution must be an object or null")
        _required_string(resolution, "summary", nonempty=True)
        _parse_instant(_required_string(resolution, "resolvedAt"))
        resolved_sha = _optional_string(resolution, "fileSha256")
        if resolved_sha is not None and SHA256_PATTERN.fullmatch(resolved_sha) is None:
            raise ValueError("invalid resolution fileSha256")
    return note


def _admit_workspace_target(note: dict[str, Any], project: Path) -> None:
    project_real = project.resolve(strict=True)
    location = note["location"]
    workspace_path = location["workspacePath"]
    logical_target = project.joinpath(*PurePosixPath(workspace_path).parts)
    real_target = logical_target.resolve(strict=False)
    if real_target == project_real or project_real in real_target.parents:
        return

    vcs_root = location.get("vcsRoot")
    if not isinstance(vcs_root, str):
        raise ValueError("workspacePath escapes through an unregistered repository projection")
    logical_repository = project.joinpath(*PurePosixPath(vcs_root).parts)
    try:
        repository_real = logical_repository.resolve(strict=True)
    except OSError as error:
        raise ValueError("projected repository root is unavailable") from error
    if logical_repository != logical_target and logical_repository not in logical_target.parents:
        raise ValueError("workspacePath is outside its projected repository")
    if repository_real != real_target and repository_real not in real_target.parents:
        raise ValueError("workspacePath escapes its projected repository")
    git_marker = repository_real / ".git"
    if not git_marker.exists() or git_marker.is_symlink() or not (git_marker.is_dir() or git_marker.is_file()):
        raise ValueError("projected repository has no safe Git metadata marker")
    if not _is_verified_git_top_level(repository_real):
        raise ValueError("projected repository is not a verified Git top-level")


def _is_verified_git_top_level(repository: Path) -> bool:
    environment = {
        name: value
        for name, value in os.environ.items()
        if not name.upper().startswith("GIT_")
    }
    environment["GIT_CONFIG_NOSYSTEM"] = "1"
    environment["GIT_CONFIG_GLOBAL"] = os.devnull
    try:
        completed = subprocess.run(
            ["git", "-C", os.fspath(repository), "rev-parse", "--show-toplevel"],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=5,
            check=False,
            env=environment,
        )
        if completed.returncode != 0:
            return False
        reported = Path(completed.stdout.strip()).resolve(strict=True)
    except (OSError, subprocess.SubprocessError):
        return False
    return reported == repository


def _read_regular_file(path: Path) -> bytes:
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(path, flags)
    try:
        info = os.fstat(descriptor)
        if not stat.S_ISREG(info.st_mode):
            raise ValueError("review note is not a regular file")
        if info.st_size > MAX_NOTE_BYTES:
            raise ValueError("review note exceeds the size limit")
        with os.fdopen(descriptor, "rb", closefd=False) as stream:
            return stream.read(MAX_NOTE_BYTES + 1)
    finally:
        os.close(descriptor)


def _notes_directory(project_argument: str) -> Path:
    project = Path(project_argument).resolve(strict=True)
    notes = project / ".idea" / "agent-review-notes" / "notes"
    if not notes.exists():
        return notes
    resolved = notes.resolve(strict=True)
    if resolved != notes or project not in resolved.parents or not resolved.is_dir():
        raise CliError("review notes directory is unsafe")
    return resolved


def _load_path(path: Path) -> tuple[dict[str, Any], bytes]:
    expected_id = path.name.removesuffix(".json")
    if ID_PATTERN.fullmatch(expected_id) is None:
        raise ValueError("invalid review note filename")
    raw = _read_regular_file(path)
    note = _validate_note(_parse_json(raw), expected_id)
    _admit_workspace_target(note, path.parents[3])
    return note, raw


def _scan(notes_directory: Path) -> tuple[list[dict[str, Any]], int]:
    if not notes_directory.exists():
        return [], 0
    admitted: list[dict[str, Any]] = []
    rejected = 0
    for path in notes_directory.iterdir():
        if path.suffix != ".json":
            continue
        try:
            note, _ = _load_path(path)
            admitted.append(note)
        except (OSError, UnicodeError, ValueError, json.JSONDecodeError):
            rejected += 1
    admitted.sort(key=lambda value: (_parse_instant(value["createdAt"]), value["id"]))
    return admitted, rejected


def _split_values(raw: str, allowed: set[str], name: str) -> set[str]:
    values = {value for value in raw.split(",") if value}
    if not values or not values <= allowed:
        raise CliError(f"invalid {name}: {raw}")
    return values


def _metadata(note: dict[str, Any]) -> dict[str, Any]:
    message = " ".join(note["message"].split())
    location = note["location"]
    return {
        "id": note["id"],
        "status": note["status"],
        "kind": note["kind"],
        "workspacePath": location["workspacePath"],
        "branch": location.get("branch"),
        "createdAt": note["createdAt"],
        "messagePreview": message[:120],
    }


def _encode_cursor(note: dict[str, Any]) -> str:
    raw = json.dumps([note["createdAt"], note["id"]], separators=(",", ":")).encode("utf-8")
    return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")


def _decode_cursor(raw: str) -> tuple[datetime, str]:
    try:
        padding = "=" * (-len(raw) % 4)
        value = json.loads(base64.b64decode(raw + padding, altchars=b"-_", validate=True))
        if not isinstance(value, list) or len(value) != 2 or not all(isinstance(item, str) for item in value):
            raise ValueError("cursor payload must contain timestamp and id")
        created_at, note_id = value
        if ID_PATTERN.fullmatch(note_id) is None:
            raise ValueError("cursor id is invalid")
        return _parse_instant(created_at), note_id
    except (ValueError, UnicodeError, json.JSONDecodeError) as error:
        raise CliError("invalid list cursor") from error


def _command_stats(_args: argparse.Namespace, notes_directory: Path) -> dict[str, Any]:
    admitted, rejected = _scan(notes_directory)
    by_status = Counter(note["status"] for note in admitted)
    by_kind = Counter(note["kind"] for note in admitted)
    return {
        "total": len(admitted),
        "actionable": sum(by_status[status] for status in ACTIONABLE),
        "rejected_count": rejected,
        "by_status": dict(sorted(by_status.items())),
        "by_kind": dict(sorted(by_kind.items())),
    }


def _command_list(args: argparse.Namespace, notes_directory: Path) -> dict[str, Any]:
    statuses = _split_values(args.status, STATUSES, "status")
    kinds = _split_values(args.kind, KINDS, "kind") if args.kind else None
    try:
        created_after = _parse_instant(args.created_after) if args.created_after else None
    except ValueError as error:
        raise CliError("invalid created-after timestamp") from error
    admitted, rejected = _scan(notes_directory)
    selected = [
        note for note in admitted
        if note["status"] in statuses
        and (kinds is None or note["kind"] in kinds)
        and (args.path_prefix is None or note["location"]["workspacePath"].startswith(args.path_prefix))
        and (args.branch is None or note["location"].get("branch") == args.branch)
        and (created_after is None or _parse_instant(note["createdAt"]) > created_after)
    ]
    if args.cursor and args.offset:
        raise CliError("cursor and non-zero offset cannot be combined")
    cursor_key = _decode_cursor(args.cursor) if args.cursor else None
    candidates = [
        note for note in selected
        if cursor_key is None or (_parse_instant(note["createdAt"]), note["id"]) > cursor_key
    ]
    page = candidates[args.offset:args.offset + args.limit]
    has_more = args.offset + len(page) < len(candidates)
    next_offset = args.offset + len(page)
    response: dict[str, Any] = {
        "total": len(selected),
        "returned": len(page),
        "next_offset": next_offset if cursor_key is None and next_offset < len(selected) else None,
        "next_cursor": _encode_cursor(page[-1]) if page and has_more else None,
        "rejected_count": rejected,
    }
    metadata = [_metadata(note) for note in page]
    if not args.group_by_file:
        response["notes"] = metadata
        return response
    groups: dict[str, list[dict[str, Any]]] = {}
    for item in metadata:
        groups.setdefault(item["workspacePath"], []).append(item)
    response["groups"] = [
        {"workspacePath": workspace_path, "notes": notes}
        for workspace_path, notes in groups.items()
    ]
    return response


def _path_for_id(notes_directory: Path, note_id: str) -> Path:
    if ID_PATTERN.fullmatch(note_id) is None:
        raise CliError(f"invalid review note id: {note_id}")
    return notes_directory / f"{note_id}.json"


def _command_show(args: argparse.Namespace, notes_directory: Path) -> dict[str, Any]:
    if len(args.ids) > MAX_SHOW:
        raise CliError(f"show accepts at most {MAX_SHOW} note ids")
    result = []
    for note_id in args.ids:
        try:
            note, _ = _load_path(_path_for_id(notes_directory, note_id))
        except (OSError, ValueError, UnicodeError, json.JSONDecodeError) as error:
            raise CliError(f"cannot admit review note {note_id}: {error}") from error
        result.append(note)
    response = {"returned": len(result), "notes": result}
    if len(_json_text(response).encode("utf-8")) > MAX_OUTPUT_BYTES:
        raise CliError("selected notes exceed the bounded output; request fewer or smaller note ids")
    return response


@contextmanager
def _note_lock(notes_directory: Path, note_id: str):
    lock_directory = notes_directory / ".locks"
    try:
        lock_directory.mkdir(mode=0o700)
    except FileExistsError:
        metadata = lock_directory.lstat()
        if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISDIR(metadata.st_mode):
            raise CliError("review note lock directory is unsafe")
    if lock_directory.resolve() != lock_directory:
        raise CliError("review note lock directory escapes through a symlink")

    lock_path = lock_directory / f"{note_id}.lock"
    flags = os.O_CREAT | os.O_RDWR
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(lock_path, flags, 0o600)
    except OSError as error:
        raise CliError(f"cannot open review note lock: {error}") from error
    try:
        if not stat.S_ISREG(os.fstat(descriptor).st_mode):
            raise CliError("review note lock file is unsafe")
        if os.name == "nt":
            import msvcrt

            if os.fstat(descriptor).st_size == 0:
                os.write(descriptor, b"0")
            os.lseek(descriptor, 0, os.SEEK_SET)
            msvcrt.locking(descriptor, msvcrt.LK_LOCK, 1)
            try:
                yield
            finally:
                os.lseek(descriptor, 0, os.SEEK_SET)
                msvcrt.locking(descriptor, msvcrt.LK_UNLCK, 1)
        else:
            import fcntl

            fcntl.lockf(descriptor, fcntl.LOCK_EX)
            try:
                yield
            finally:
                fcntl.lockf(descriptor, fcntl.LOCK_UN)
    finally:
        os.close(descriptor)


def _atomic_transform(
    notes_directory: Path,
    note_id: str,
    transform: Callable[[dict[str, Any]], None],
) -> dict[str, Any]:
    path = _path_for_id(notes_directory, note_id)
    with _note_lock(notes_directory, note_id):
        try:
            note, original = _load_path(path)
            transform(note)
            _validate_note(note, note_id)
        except (OSError, ValueError, UnicodeError, json.JSONDecodeError) as error:
            raise CliError(f"cannot update review note {note_id}: {error}") from error

        encoded = _encode_updated_note(note, note_id)
        _replace_bytes(notes_directory, path, note_id, original, encoded)
        return note


def _encode_updated_note(note: dict[str, Any], note_id: str) -> bytes:
    encoded = (_json_text(note) + "\n").encode("utf-8")
    if len(encoded) > MAX_NOTE_BYTES:
        raise CliError(f"updated review note {note_id} exceeds the size limit")
    return encoded


def _replace_bytes(notes_directory: Path, path: Path, note_id: str, expected: bytes, replacement: bytes) -> None:
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{note_id}-", suffix=".tmp", dir=notes_directory)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(replacement)
            stream.flush()
            os.fsync(stream.fileno())
        if _read_regular_file(path) != expected:
            raise CliError(f"review note {note_id} changed concurrently")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def _command_claim(args: argparse.Namespace, notes_directory: Path) -> dict[str, Any]:
    if len(args.ids) > MAX_SHOW:
        raise CliError(f"claim accepts at most {MAX_SHOW} note ids")
    if len(set(args.ids)) != len(args.ids):
        raise CliError("claim note ids must be unique")

    def claim(note: dict[str, Any]) -> None:
        if note["status"] not in {"open", "in_progress"}:
            raise ValueError(f"status {note['status']} is not actionable")
        note["status"] = "in_progress"

    with ExitStack() as locks:
        for note_id in sorted(args.ids):
            locks.enter_context(_note_lock(notes_directory, note_id))
        prepared: list[tuple[str, Path, dict[str, Any], bytes, bytes]] = []
        for note_id in args.ids:
            path = _path_for_id(notes_directory, note_id)
            try:
                note, original = _load_path(path)
                claim(note)
                _validate_note(note, note_id)
                encoded = _encode_updated_note(note, note_id)
            except (OSError, ValueError, UnicodeError, json.JSONDecodeError) as error:
                raise CliError(f"cannot claim review note {note_id}: {error}") from error
            prepared.append((note_id, path, note, original, encoded))

        replaced: list[tuple[str, Path, bytes, bytes]] = []
        try:
            for note_id, path, _, original, encoded in prepared:
                _replace_bytes(notes_directory, path, note_id, original, encoded)
                replaced.append((note_id, path, original, encoded))
        except Exception as error:
            rollback_errors = []
            for note_id, path, original, encoded in reversed(replaced):
                try:
                    _replace_bytes(notes_directory, path, note_id, encoded, original)
                except Exception as rollback_error:
                    rollback_errors.append(f"{note_id}: {rollback_error}")
            if rollback_errors:
                raise CliError("claim failed and rollback was incomplete: " + "; ".join(rollback_errors)) from error
            raise CliError(f"claim failed and was rolled back: {error}") from error
        changed = [note for _, _, note, _, _ in prepared]
    return {"updated": len(changed), "notes": [_metadata(note) for note in changed]}


def _command_resolve(args: argparse.Namespace, notes_directory: Path) -> dict[str, Any]:
    if not args.summary.strip():
        raise CliError("resolution summary must not be blank")
    if args.file_sha256 is not None and SHA256_PATTERN.fullmatch(args.file_sha256) is None:
        raise CliError("file-sha256 must contain 64 lowercase hexadecimal characters")

    def resolve(note: dict[str, Any]) -> None:
        if note["status"] not in ACTIONABLE:
            raise ValueError(f"status {note['status']} is not actionable")
        note["status"] = "resolved"
        resolution = dict(note.get("resolution") or {})
        resolution.update(
            summary=args.summary.strip(),
            resolvedAt=datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            fileSha256=args.file_sha256,
        )
        note["resolution"] = resolution

    changed = _atomic_transform(notes_directory, args.id, resolve)
    return {"updated": 1, "notes": [_metadata(changed)]}


def _parser() -> argparse.ArgumentParser:
    parser = JsonArgumentParser(description=__doc__)
    parser.add_argument("--project", default=".", help="project root (default: current directory)")
    commands = parser.add_subparsers(dest="command", required=True)

    commands.add_parser("stats", help="return aggregate admitted/rejected note counts")

    listing = commands.add_parser("list", help="return bounded metadata, not complete note bodies")
    listing.add_argument("--status", default="open,in_progress")
    listing.add_argument("--kind")
    listing.add_argument("--path-prefix")
    listing.add_argument("--branch")
    listing.add_argument("--created-after")
    listing.add_argument("--limit", type=int, default=20, choices=range(1, MAX_LIST + 1), metavar="1..100")
    listing.add_argument("--offset", type=int, default=0)
    listing.add_argument("--cursor")
    listing.add_argument("--group-by-file", action="store_true")

    show = commands.add_parser("show", help="return complete admitted notes for selected ids")
    show.add_argument("ids", nargs="+")

    claim = commands.add_parser("claim", help="mark selected actionable notes in_progress")
    claim.add_argument("ids", nargs="+")

    resolve = commands.add_parser("resolve", help="resolve one actionable note")
    resolve.add_argument("id")
    resolve.add_argument("--summary", required=True)
    resolve.add_argument("--file-sha256")
    return parser


def main() -> int:
    try:
        parser = _parser()
        args = parser.parse_args()
        if getattr(args, "offset", 0) < 0:
            parser.error("--offset must be non-negative")
        notes_directory = _notes_directory(args.project)
        handlers = {
            "stats": _command_stats,
            "list": _command_list,
            "show": _command_show,
            "claim": _command_claim,
            "resolve": _command_resolve,
        }
        result = handlers[args.command](args, notes_directory)
        encoded = _json_text(result)
        if len(encoded.encode("utf-8")) > MAX_OUTPUT_BYTES:
            raise CliError("command response exceeds the bounded output; narrow the query")
    except (CliError, OSError, UnicodeError, ArithmeticError, ValueError, json.JSONDecodeError) as error:
        print(_error_json(error))
        return 2
    print(encoded)
    return 0


if __name__ == "__main__":
    sys.exit(main())
