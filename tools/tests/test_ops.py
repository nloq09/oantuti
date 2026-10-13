import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
import uuid

spec = importlib.util.spec_from_file_location("ops", Path(__file__).parents[1] / "ops.py")
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)


class BackupTests(unittest.TestCase):
    def backup(self, root, member="bots/id/main.py", kind=tarfile.REGTYPE):
        (root / "database.dump").write_bytes(b"test dump")
        with tarfile.open(root / "artifacts.tar", "w") as archive:
            entry = tarfile.TarInfo(member)
            entry.type = kind
            entry.linkname = "/outside" if kind == tarfile.SYMTYPE else ""
            entry.size = 4 if kind == tarfile.REGTYPE else 0
            archive.addfile(entry, io.BytesIO(b"test") if entry.size else None)
        manifest = {"version": 1, "files": {name: {"bytes": (root / name).stat().st_size, "sha256": ops.sha256(root / name)} for name in ops.FILES}}
        (root / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")

    def test_valid_complete_backup_and_corrupted_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.backup(root)
            self.assertEqual(ops.validate_backup(root)["version"], 1)
            (root / "database.dump").write_bytes(b"evil dump")
            with self.assertRaisesRegex(ValueError, "integrity"):
                ops.validate_backup(root)

    def test_missing_files_and_unknown_manifest_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.backup(root)
            manifest = json.loads((root / "manifest.json").read_text()); manifest["version"] = 2
            (root / "manifest.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "Unsupported"):
                ops.validate_backup(root)
            self.backup(root); (root / "database.dump").unlink()
            with self.assertRaisesRegex(ValueError, "integrity"):
                ops.validate_backup(root)

    def test_archive_traversal_links_devices_and_unrelated_roots_rejected(self):
        for name, kind in [("/bots/file", tarfile.REGTYPE), ("bots/../../file", tarfile.REGTYPE),
                           ("etc/passwd", tarfile.REGTYPE), ("bots/link", tarfile.SYMTYPE),
                           ("replays/device", tarfile.CHRTYPE), ("bots\\..\\file", tarfile.REGTYPE)]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory); self.backup(root, name, kind)
                with self.assertRaisesRegex(ValueError, "Unsafe"):
                    ops.validate_backup(root)

    def test_incomplete_or_altered_manifest_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.backup(root)
            (root / "manifest.json").write_text(json.dumps({"version": 1, "files": {"../../outside": {}}}))
            with self.assertRaisesRegex(ValueError, "incomplete"):
                ops.validate_backup(root)
            for malformed in [[], {"version": 1, "files": []}, {"version": 1, "files": {"database.dump": None, "artifacts.tar": None}}]:
                (root / "manifest.json").write_text(json.dumps(malformed))
                with self.assertRaises(ValueError):
                    ops.validate_backup(root)


class CleanupTests(unittest.TestCase):
    def container(self):
        return {"Name": "/rps-bot-" + str(uuid.uuid4()), "Config": {"Labels": {"rps.managed": "true", "rps.scope": "test",
                "rps.boot": str(uuid.uuid4()), "botId": str(uuid.uuid4()), "matchId": str(uuid.uuid4())}}, "State": {"Running": False}}

    def test_only_managed_bots_with_all_context_labels_in_exact_scope_are_candidates(self):
        container = self.container(); self.assertTrue(ops.orphan(container, "test"))
        self.assertFalse(ops.orphan(container, "other"))
        container["Config"]["Labels"]["rps.managed"] = "false"
        self.assertFalse(ops.orphan(container, "test"))
        container = self.container(); container["Name"] = "/postgres"
        self.assertFalse(ops.orphan(container, "test"))
        container = self.container(); del container["Config"]["Labels"]["botId"]
        self.assertFalse(ops.orphan(container, "test"))

    def test_live_bots_are_excluded_until_operator_explicitly_requests_them(self):
        container = self.container(); container["State"]["Running"] = True
        self.assertFalse(ops.orphan(container, "test"))
        self.assertTrue(ops.orphan(container, "test", True))


if __name__ == "__main__":
    unittest.main()
