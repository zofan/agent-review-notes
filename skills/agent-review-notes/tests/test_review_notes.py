import argparse
import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).parents[1] / "scripts" / "review_notes.py"
SHA = "a" * 64


def note(
    status: str,
    *,
    path: str = "src/main.py",
    kind: str = "bug",
    message: str = "Fix the bug",
    schema: str = "agent.review.note.v1",
) -> dict:
    note_id = str(uuid.uuid4())
    return {
        "schema": schema,
        "id": note_id,
        "status": status,
        "kind": kind,
        "message": message,
        "location": {
            "workspacePath": path,
            "vcsRoot": None,
            "vcsPath": None,
            "head": None,
            "fileSha256": SHA,
            "startOffset": 0,
            "endOffset": 3,
            "startLine": 1,
            "endLine": 1,
            "branch": None,
            "target": None,
        },
        "anchor": {"selection": "bad", "prefix": "", "suffix": "", "symbol": None},
        "createdAt": "2026-08-22T12:00:00Z",
        "resolution": None,
        "extension": {"preserve": True},
    }


class ReviewNotesCliTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.project = Path(self.temp.name)
        self.notes = self.project / ".idea" / "agent-review-notes" / "notes"
        self.notes.mkdir(parents=True)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_note(self, value: dict) -> str:
        note_id = value["id"]
        (self.notes / f"{note_id}.json").write_text(json.dumps(value), encoding="utf-8")
        return note_id

    def load_cli_module(self):
        spec = importlib.util.spec_from_file_location("review_notes_cli", SCRIPT)
        assert spec is not None and spec.loader is not None
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def run_cli(self, *args: str, expect: int = 0) -> dict:
        completed = subprocess.run(
            [sys.executable, os.fspath(SCRIPT), "--project", os.fspath(self.project), *args],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(expect, completed.returncode, completed.stderr)
        return json.loads(completed.stdout)

    def test_feature_kind_requires_v2_schema(self) -> None:
        accepted = note("open", kind="feature", schema="agent.review.note.v2")
        rejected = note("open", kind="feature")
        self.write_note(accepted)
        self.write_note(rejected)

        result = self.run_cli("stats")

        self.assertEqual(1, result["total"])
        self.assertEqual({"feature": 1}, result["by_kind"])
        self.assertEqual(1, result["rejected_count"])

    def test_list_returns_bounded_actionable_metadata_without_note_bodies(self) -> None:
        open_note = note("open", message="x" * 300)
        resolved_note = note("resolved", path="src/done.py")
        self.write_note(open_note)
        self.write_note(resolved_note)

        result = self.run_cli("list", "--limit", "1")

        self.assertEqual(1, result["total"])
        self.assertEqual(1, result["returned"])
        self.assertIsNone(result["next_offset"])
        self.assertEqual(open_note["id"], result["notes"][0]["id"])
        self.assertEqual(120, len(result["notes"][0]["messagePreview"]))
        self.assertNotIn("anchor", result["notes"][0])
        self.assertNotIn(resolved_note["id"], json.dumps(result))

    def test_list_filters_by_status_kind_and_path_and_paginates(self) -> None:
        first = note("open", path="src/api/first.py", kind="bug")
        second = note("in_progress", path="src/api/second.py", kind="bug")
        ignored = note("open", path="docs/readme.md", kind="suggestion")
        for value in (first, second, ignored):
            self.write_note(value)

        page = self.run_cli(
            "list", "--status", "open,in_progress", "--kind", "bug",
            "--path-prefix", "src/api", "--limit", "1",
        )
        next_page = self.run_cli(
            "list", "--status", "open,in_progress", "--kind", "bug",
            "--path-prefix", "src/api", "--limit", "1", "--offset", str(page["next_offset"]),
        )

        self.assertEqual(2, page["total"])
        self.assertEqual(1, page["next_offset"])
        self.assertEqual(1, next_page["returned"])
        self.assertIsNone(next_page["next_offset"])
        self.assertNotEqual(page["notes"][0]["id"], next_page["notes"][0]["id"])

    def test_stats_aggregates_metadata_without_note_bodies(self) -> None:
        self.write_note(note("open"))
        self.write_note(note("in_progress", path="src/work.py"))
        self.write_note(note("resolved", path="src/done.py"))
        (self.notes / "broken.json").write_text("{", encoding="utf-8")

        result = self.run_cli("stats")

        self.assertEqual(3, result["total"])
        self.assertEqual(2, result["actionable"])
        self.assertEqual(1, result["rejected_count"])
        self.assertEqual({"in_progress": 1, "open": 1, "resolved": 1}, result["by_status"])
        self.assertNotIn("message", json.dumps(result))

    def test_cursor_date_filter_and_grouping_are_bounded(self) -> None:
        first = note("open", path="src/a.py")
        first["createdAt"] = "2026-08-20T12:00:00Z"
        second = note("open", path="src/a.py")
        second["createdAt"] = "2026-08-21T12:00:00Z"
        third = note("open", path="src/b.py")
        third["createdAt"] = "2026-08-22T12:00:00Z"
        self.write_note(first)
        self.write_note(second)
        self.write_note(third)

        page = self.run_cli("list", "--created-after", "2026-08-20T12:00:00Z", "--limit", "1")
        self.assertEqual(2, page["total"])
        self.assertEqual(second["id"], page["notes"][0]["id"])
        self.assertIsNotNone(page["next_cursor"])
        following = self.run_cli(
            "list", "--created-after", "2026-08-20T12:00:00Z", "--limit", "1",
            "--cursor", page["next_cursor"],
        )
        self.assertEqual(third["id"], following["notes"][0]["id"])
        grouped = self.run_cli("list", "--group-by-file", "--limit", "3")
        self.assertNotIn("notes", grouped)
        self.assertEqual(["src/a.py", "src/b.py"], [group["workspacePath"] for group in grouped["groups"]])

    def test_show_reads_only_selected_admitted_notes(self) -> None:
        selected = note("open")
        other = note("open", path="src/other.py")
        self.write_note(selected)
        self.write_note(other)

        result = self.run_cli("show", selected["id"])

        self.assertEqual([selected], result["notes"])
        self.assertNotIn(other["id"], json.dumps(result))

    def test_show_refuses_a_response_that_would_overflow_context_bound(self) -> None:
        oversized = note("open", message="x" * 300_000)
        note_id = self.write_note(oversized)

        result = self.run_cli("show", note_id, expect=2)

        self.assertIn("bounded output", result["error"])

    def test_claim_and_resolve_preserve_unknown_fields(self) -> None:
        value = note("open")
        note_id = self.write_note(value)

        claimed = self.run_cli("claim", note_id)
        resolved = self.run_cli(
            "resolve",
            note_id,
            "--summary",
            "Fixed with regression coverage",
            "--file-sha256",
            "b" * 64,
        )
        stored = json.loads((self.notes / f"{note_id}.json").read_text(encoding="utf-8"))

        self.assertEqual("in_progress", claimed["notes"][0]["status"])
        self.assertEqual("resolved", resolved["notes"][0]["status"])
        self.assertEqual("resolved", stored["status"])
        self.assertEqual("Fixed with regression coverage", stored["resolution"]["summary"])
        self.assertEqual("b" * 64, stored["resolution"]["fileSha256"])
        self.assertEqual({"preserve": True}, stored["extension"])

    def test_resolve_preserves_unknown_nested_resolution_fields(self) -> None:
        value = note("open")
        value["resolution"] = {
            "summary": "prior metadata",
            "resolvedAt": "2026-08-20T12:00:00Z",
            "fileSha256": None,
            "futureField": {"keep": True},
        }
        note_id = self.write_note(value)

        self.run_cli("resolve", note_id, "--summary", "done")

        stored = json.loads((self.notes / f"{note_id}.json").read_text(encoding="utf-8"))
        self.assertEqual({"keep": True}, stored["resolution"]["futureField"])

    def test_large_unknown_numbers_remain_valid_and_precise_after_mutation(self) -> None:
        value = note("open")
        note_id = self.write_note(value)
        path = self.notes / f"{note_id}.json"
        raw = path.read_text(encoding="utf-8").replace(
            '"extension": {"preserve": true}',
            '"extension": {"large": 1e400, "precise": 1.2300}',
        )
        path.write_text(raw, encoding="utf-8")

        self.run_cli("resolve", note_id, "--summary", "done")

        stored = path.read_text(encoding="utf-8")
        self.assertNotIn("Infinity", stored)
        self.assertIn('"large": 1E+400', stored)
        self.assertIn('"precise": 1.2300', stored)

    def test_errors_are_bounded_json_without_tracebacks(self) -> None:
        self.notes.rmdir()

        missing = self.run_cli("resolve", str(uuid.uuid4()), "--summary", "done", expect=2)
        invalid = self.run_cli("list", "--limit", "0", expect=2)
        module = self.load_cli_module()
        hostile = module._error_json(module.CliError("\udcff" + "x" * 300_000))

        self.assertIn("error", missing)
        self.assertIn("error", invalid)
        self.assertLess(len(hostile.encode("ascii")), module.MAX_OUTPUT_BYTES)
        self.assertTrue(json.loads(hostile)["error"].endswith("..."))

    def test_claim_preflights_the_whole_batch_before_updating(self) -> None:
        actionable = note("open")
        inactive = note("resolved", path="src/done.py")
        actionable_id = self.write_note(actionable)
        inactive_id = self.write_note(inactive)

        self.run_cli("claim", actionable_id, inactive_id, expect=2)

        stored = json.loads((self.notes / f"{actionable_id}.json").read_text(encoding="utf-8"))
        self.assertEqual("open", stored["status"])

    def test_claim_rolls_back_a_partially_applied_batch(self) -> None:
        first = note("open")
        second = note("open", path="src/second.py")
        first_id = self.write_note(first)
        second_id = self.write_note(second)
        module = self.load_cli_module()
        original_replace = module._replace_bytes
        calls = 0

        def fail_second(*args):
            nonlocal calls
            calls += 1
            if calls == 2:
                raise OSError("injected publication failure")
            return original_replace(*args)

        with patch.object(module, "_replace_bytes", side_effect=fail_second):
            with self.assertRaises(module.CliError):
                module._command_claim(argparse.Namespace(ids=[first_id, second_id]), self.notes)

        first_stored = json.loads((self.notes / f"{first_id}.json").read_text(encoding="utf-8"))
        second_stored = json.loads((self.notes / f"{second_id}.json").read_text(encoding="utf-8"))
        self.assertEqual("open", first_stored["status"])
        self.assertEqual("open", second_stored["status"])

    def test_invalid_vcs_location_is_rejected(self) -> None:
        value = note("open")
        value["location"]["vcsRoot"] = "src"
        value["location"]["vcsPath"] = "other.py"
        value["location"]["branch"] = "main"
        self.write_note(value)

        result = self.run_cli("list")

        self.assertEqual(0, result["returned"])
        self.assertEqual(1, result["rejected_count"])

    def test_directory_note_with_file_anchor_is_rejected(self) -> None:
        value = note("open")
        value["location"].update({
            "target": "directory",
            "fileSha256": "",
            "startOffset": 0,
            "endOffset": 0,
            "startLine": 0,
            "endLine": 0,
        })
        self.write_note(value)

        result = self.run_cli("list")

        self.assertEqual(0, result["returned"])
        self.assertEqual(1, result["rejected_count"])

    def test_noncanonical_timestamp_is_rejected(self) -> None:
        value = note("open")
        value["createdAt"] = "2026-08-22 12:00:00Z"
        self.write_note(value)

        result = self.run_cli("list")

        self.assertEqual(0, result["returned"])
        self.assertEqual(1, result["rejected_count"])

    def test_timestamp_utc_normalization_overflow_is_bounded(self) -> None:
        value = note("open")
        value["createdAt"] = "0001-01-01T00:00:00+23:59"
        self.write_note(value)

        result = self.run_cli("list")
        filter_error = self.run_cli("list", "--created-after", value["createdAt"], expect=2)

        self.assertEqual(0, result["returned"])
        self.assertEqual(1, result["rejected_count"])
        self.assertEqual("invalid created-after timestamp", filter_error["error"])

    def test_out_of_range_integer_is_rejected(self) -> None:
        value = note("open")
        value["location"]["endOffset"] = 2**40
        self.write_note(value)

        result = self.run_cli("list")

        self.assertEqual(0, result["returned"])
        self.assertEqual(1, result["rejected_count"])

    def test_dot_workspace_path_is_rejected(self) -> None:
        value = note("open", path=".")
        self.write_note(value)

        result = self.run_cli("list")

        self.assertEqual(0, result["returned"])
        self.assertEqual(1, result["rejected_count"])

    def test_backslash_workspace_path_is_rejected(self) -> None:
        value = note("open", path="src\\main.py")
        self.write_note(value)

        result = self.run_cli("list")

        self.assertEqual(0, result["returned"])
        self.assertEqual(1, result["rejected_count"])

    def test_list_refuses_unbounded_metadata_output(self) -> None:
        long_directory = "/".join(["p" * 250] * 12)
        for index in range(100):
            self.write_note(note("open", path=f"{long_directory}/{index}.py"))

        result = self.run_cli("list", "--limit", "100", expect=2)

        self.assertIn("bounded output", result["error"])

    def test_nonstandard_numbers_and_excessive_json_depth_are_rejected(self) -> None:
        nonstandard = note("open")
        nonstandard["extension"] = float("nan")
        self.write_note(nonstandard)
        nested: dict = {}
        cursor = nested
        for _ in range(70):
            child: dict = {}
            cursor["child"] = child
            cursor = child
        too_deep = note("open", path="src/deep.py")
        too_deep["extension"] = nested
        self.write_note(too_deep)

        result = self.run_cli("list")

        self.assertEqual(0, result["returned"])
        self.assertEqual(2, result["rejected_count"])

    def test_json_depth_matches_the_jvm_zero_based_limit(self) -> None:
        module = self.load_cli_module()

        def raw_with_nested_extension(layers: int) -> bytes:
            extension = 0
            for _ in range(layers):
                extension = {"child": extension}
            value = note("open")
            value["extension"] = extension
            return json.dumps(value).encode()

        module._parse_json(raw_with_nested_extension(63))
        with self.assertRaisesRegex(ValueError, "nesting limit 64"):
            module._parse_json(raw_with_nested_extension(64))

    def test_symlinked_note_is_rejected_without_reading_target(self) -> None:
        outside = self.project / "outside.json"
        value = note("open")
        outside.write_text(json.dumps(value), encoding="utf-8")
        (self.notes / f"{value['id']}.json").symlink_to(outside)

        result = self.run_cli("list")

        self.assertEqual(0, result["returned"])
        self.assertEqual(1, result["rejected_count"])

    def test_workspace_path_through_external_symlink_is_rejected(self) -> None:
        outside = self.project.parent / f"outside-{uuid.uuid4()}"
        outside.mkdir()
        self.addCleanup(lambda: outside.rmdir())
        (self.project / "link").symlink_to(outside, target_is_directory=True)
        self.write_note(note("open", path="link/secret.py"))

        result = self.run_cli("list")

        self.assertEqual(0, result["returned"])
        self.assertEqual(1, result["rejected_count"])

    def test_workspace_path_through_internal_symlink_is_rejected(self) -> None:
        real = self.project / "real"
        real.mkdir()
        (self.project / "link").symlink_to(real, target_is_directory=True)
        self.write_note(note("open", path="link/source.py"))

        result = self.run_cli("list")

        self.assertEqual(0, result["returned"])
        self.assertEqual(1, result["rejected_count"])

    def test_unpaired_unicode_surrogate_is_rejected_without_a_traceback(self) -> None:
        self.write_note(note("open", message="\ud800"))

        result = self.run_cli("list")

        self.assertEqual(0, result["returned"])
        self.assertEqual(1, result["rejected_count"])

    def test_mutation_rejects_a_symlinked_lock_directory(self) -> None:
        value = note("open")
        note_id = self.write_note(value)
        outside = self.project / "outside-locks"
        outside.mkdir()
        (self.notes / ".locks").symlink_to(outside, target_is_directory=True)

        self.run_cli("claim", note_id, expect=2)

        stored = json.loads((self.notes / f"{note_id}.json").read_text(encoding="utf-8"))
        self.assertEqual("open", stored["status"])
        self.assertEqual([], list(outside.iterdir()))


if __name__ == "__main__":
    unittest.main()
