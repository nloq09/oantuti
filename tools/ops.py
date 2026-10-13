"""Compose operations. No shell interpolation, binary dumps, empty-target restore only."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import tarfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parent.parent
FILES = ("database.dump", "artifacts.tar")


def run(args, **kwargs):
    result = subprocess.run(args, stderr=subprocess.PIPE, **kwargs)
    if result.returncode:
        # Docker/SQL stderr can contain sensitive config. Do not print it automatically.
        raise RuntimeError("Operation failed; check the service logs (stderr omitted).")
    return result


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def validate_backup(directory):
    directory = Path(directory).resolve()
    manifest_path = directory / "manifest.json"
    if manifest_path.is_symlink() or manifest_path.stat().st_size > 65536:
        raise ValueError("Invalid manifest")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if (not isinstance(manifest, dict) or manifest.get("version") != 1
            or not isinstance(manifest.get("files"), dict) or set(manifest["files"]) != set(FILES)):
        raise ValueError("Unsupported or incomplete backup")
    for name in FILES:
        path = directory / name
        entry = manifest["files"][name]
        if (not isinstance(entry, dict) or type(entry.get("bytes")) is not int or entry["bytes"] < 0
                or not isinstance(entry.get("sha256"), str) or not re.fullmatch(r"[a-f0-9]{64}", entry["sha256"])):
            raise ValueError("Invalid file manifest: " + name)
        if path.is_symlink() or not path.is_file() or path.stat().st_size != entry.get("bytes") or sha256(path) != entry.get("sha256"):
            raise ValueError("Backup integrity check failed: " + name)
    with tarfile.open(directory / "artifacts.tar", "r:") as archive:
        count = 0
        for member in archive:
            parts = PurePosixPath(member.name).parts
            count += 1
            if (count > 100000 or not parts or parts[0] not in {"bots", "replays"}
                    or ".." in parts or "\\" in member.name or member.name.startswith("/")
                    or not (member.isfile() or member.isdir())):
                raise ValueError("Unsafe artifact archive")
    return manifest


def orphan(container, scope, include_running=False):
    labels = container.get("Config", {}).get("Labels") or {}
    uuid = r"[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"
    return (labels.get("rps.managed") == "true" and labels.get("rps.scope") == scope
            and re.fullmatch(r"/rps-bot-" + uuid, container.get("Name", "")) is not None
            and all(re.fullmatch(uuid, labels.get(key, "")) for key in ("rps.boot", "botId", "matchId"))
            and (include_running or not container.get("State", {}).get("Running", False)))


class Stack:
    def __init__(self, args):
        env = Path(args.env_file).resolve()
        if not env.is_file():
            raise ValueError("Environment file does not exist")
        self.command = ["docker", "compose", "--env-file", str(env)]
        if args.project_name:
            if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,59}", args.project_name):
                raise ValueError("Invalid project name")
            # Same variable drives explicit bot volume names and container labels.
            self.env = dict(os.environ, COMPOSE_PROJECT_NAME=args.project_name)
            self.command += ["--project-name", args.project_name]
        else:
            self.env = os.environ.copy()
        self.command += ["-f", str(ROOT / "infra/docker-compose.yml")]
        if args.bots:
            self.command += ["-f", str(ROOT / "infra/docker-compose.bots.yml")]
        for file in args.compose_file:
            self.command += ["-f", str(Path(file).resolve())]
        config = self.json("config", "--format", "json")
        self.scope = config["name"]
        pg = config["services"]["postgres"]["environment"]
        self.user, self.database, self.app_user = pg["POSTGRES_USER"], pg["POSTGRES_DB"], pg["APP_DB_USERNAME"]
        if not all(re.fullmatch(r"[a-zA-Z_][a-zA-Z0-9_]{0,62}", value) for value in (self.user, self.database, self.app_user)):
            raise ValueError("Use simple PostgreSQL identifiers for operations")

    def call(self, *args, **kwargs):
        return run(self.command + list(args), env=self.env, **kwargs)

    def json(self, *args):
        return json.loads(self.call(*args, stdout=subprocess.PIPE).stdout)

    def running(self):
        ids = self.call("ps", "-a", "-q", "rps-server", stdout=subprocess.PIPE).stdout.decode().split()
        if not ids:
            return False
        containers = json.loads(run(["docker", "inspect", *ids], stdout=subprocess.PIPE).stdout)
        return any(container["State"]["Running"] for container in containers)

    def backup(self, output):
        destination = Path(output).resolve()
        destination.mkdir(parents=True, exist_ok=False, mode=0o700)
        was_running = self.running()
        try:
            if was_running:
                self.call("stop", "--timeout", "60", "rps-server", stdout=subprocess.DEVNULL)
            with (destination / "database.dump").open("xb") as stream:
                self.call("exec", "-T", "postgres", "pg_dump", "--format=custom", "--no-owner", "--no-acl", "-U", self.user, "-d", self.database, stdout=stream)
            with (destination / "artifacts.tar").open("xb") as stream:
                self.call("run", "--rm", "-T", "--no-deps", "--entrypoint", "tar", "maintenance", "-cf", "-", "-C", "/data", "bots", "replays", stdout=stream)
            manifest = {"version": 1, "createdAt": datetime.now(timezone.utc).isoformat(), "project": self.scope,
                        "files": {name: {"bytes": (destination / name).stat().st_size, "sha256": sha256(destination / name)} for name in FILES}}
            (destination / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
            for path in destination.iterdir():
                path.chmod(0o600)
            validate_backup(destination)
            print("Backup complete:", destination)
        finally:
            if was_running:
                self.call("up", "-d", "--no-deps", "--wait", "--wait-timeout", "180", "rps-server", stdout=subprocess.DEVNULL)

    def restore(self, source):
        source = Path(source).resolve()
        validate_backup(source)  # Complete preflight before writing any target data.
        if self.running():
            raise ValueError("Stop the target server before restore")
        self.call("up", "-d", "--wait", "postgres", stdout=subprocess.DEVNULL)
        tables = self.call("exec", "-T", "postgres", "psql", "-U", self.user, "-d", self.database, "-Atc",
                           "select count(*) from pg_catalog.pg_tables where schemaname='public'", stdout=subprocess.PIPE).stdout.decode().strip()
        if tables != "0":
            raise ValueError("Restore requires an empty database; use a NEW project/volumes")
        entries = self.call("run", "--rm", "-T", "--no-deps", "--entrypoint", "find", "maintenance", "/data/bots", "/data/replays", "-mindepth", "1", "-print", "-quit", stdout=subprocess.PIPE).stdout
        if entries.strip():
            raise ValueError("Restore requires empty bot/replay volumes")
        with (source / "database.dump").open("rb") as stream:
            self.call("exec", "-T", "postgres", "pg_restore", "--list", stdin=stream, stdout=subprocess.DEVNULL)
        with (source / "artifacts.tar").open("rb") as stream:
            self.call("run", "--rm", "-T", "--no-deps", "--entrypoint", "tar", "maintenance", "-xf", "-", "-C", "/data", stdin=stream, stdout=subprocess.DEVNULL)
        self.call("run", "--rm", "-T", "--no-deps", "--entrypoint", "chown", "maintenance", "-R", "10001:10001", "/data/bots", "/data/replays", stdout=subprocess.DEVNULL)
        with (source / "database.dump").open("rb") as stream:
            self.call("exec", "-T", "postgres", "pg_restore", "--exit-on-error", "--single-transaction", "--no-owner", "--no-acl", "--role", self.app_user, "-U", self.user, "-d", self.database, stdin=stream, stdout=subprocess.DEVNULL)
        print("Restore complete. Start the target server and verify login/history/replay before switching traffic.")

    def cleanup(self, apply, include_running):
        if self.running():
            raise ValueError("Stop this project's server before orphan cleanup")
        ids = run(["docker", "ps", "-aq", "--filter", "label=rps.managed=true", "--filter", "label=rps.scope=" + self.scope], stdout=subprocess.PIPE).stdout.decode().split()
        containers = json.loads(run(["docker", "inspect", *ids], stdout=subprocess.PIPE).stdout) if ids else []
        candidates = [c for c in containers if orphan(c, self.scope, include_running)]
        for container in candidates:
            print(("Remove: " if apply else "Would remove: ") + container["Id"][:12])
            if apply:
                # Re-inspect immediately before removal; never delete by a recycled name.
                current = json.loads(run(["docker", "inspect", container["Id"]], stdout=subprocess.PIPE).stdout)[0]
                if orphan(current, self.scope, include_running):
                    run(["docker", "rm", "--force", container["Id"]], stdout=subprocess.DEVNULL)
        print(str(len(candidates)) + " managed bot container(s); " + ("applied" if apply else "dry run"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", default=str(ROOT / ".env.production"))
    parser.add_argument("--project-name")
    parser.add_argument("--bots", action="store_true")
    parser.add_argument("--compose-file", action="append", default=[])
    commands = parser.add_subparsers(dest="action", required=True)
    backup = commands.add_parser("backup"); backup.add_argument("--output", required=True)
    restore = commands.add_parser("restore"); restore.add_argument("--source", required=True)
    verify = commands.add_parser("verify"); verify.add_argument("--source", required=True)
    cleanup = commands.add_parser("cleanup"); cleanup.add_argument("--apply", action="store_true"); cleanup.add_argument("--include-running", action="store_true")
    args = parser.parse_args()
    try:
        if args.action == "verify":
            validate_backup(args.source); print("Backup integrity verified."); return
        stack = Stack(args)
        if args.action == "backup": stack.backup(args.output)
        elif args.action == "restore": stack.restore(args.source)
        else: stack.cleanup(args.apply, args.include_running)
    except (RuntimeError, ValueError, OSError, tarfile.TarError, KeyError) as error:
        print("ERROR:", str(error), file=sys.stderr); sys.exit(1)


if __name__ == "__main__":
    main()
