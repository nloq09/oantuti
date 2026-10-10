"""Smoke-test repository-owned starter bots, never uploaded player code."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

examples = Path(__file__).resolve().parents[1] / "client" / "assets" / "bot-examples"
java_home = Path(os.environ["JAVA_HOME"]) / "bin" if "JAVA_HOME" in os.environ else None
extension = ".exe" if os.name == "nt" else ""


def java_tool(name):
    return str(java_home / (name + extension)) if java_home else shutil.which(name)


requests = []
for sequence, action in enumerate([
    {"type": "MOVE", "pieceId": 'P1-"quoted\\id"', "from": {"row": 6, "col": 1}, "to": {"row": 5, "col": 1}},
    {"type": "PASS"},
    {"type": "MOVE", "pieceId": "P1-ROCK-1", "from": {"row": 5, "col": 1}, "to": {"row": 4, "col": 1}},
], 42):
    pieces = [] if action["type"] == "PASS" else [{"id": action["pieceId"], "ownerId": "P1", "type": "ROCK", "status": "ALIVE", "position": action["from"]}]
    requests.append({"type": "YOUR_TURN", "matchId": "EXAMPLES", "turn": sequence - 42,
                     "sequence": sequence, "player": "P1", "state": {"board": {"pieces": pieces}},
                     "legalActions": [action]})


def verify(label, command, turns=requests, choices=None):
    payload = "".join(json.dumps(turn, separators=(",", ":")) + "\n" for turn in turns)
    result = subprocess.run(command, input=payload, text=True, encoding="utf-8", capture_output=True, timeout=15, check=True)
    lines = result.stdout.splitlines()
    assert len(lines) == len(turns), (label, result.stdout)
    for index, (turn, line) in enumerate(zip(turns, lines)):
        expected = turn["legalActions"][0] if choices is None else choices[index]
        assert json.loads(line) == {"type": "ACTION", "sequence": turn["sequence"], "action": expected}, (label, line)
    print(f"{label}: PASS ({len(lines)} consecutive JSON Lines turns)")


with tempfile.TemporaryDirectory(prefix="rps-owned-examples-") as directory:
    build = Path(directory)
    verify("Python", [sys.executable, "-I", "-u", str(examples / "main.py")])
    verify("Python strategy", [sys.executable, "-I", "-u", str(examples / "tactical.py")])
    turns, choices = [], []
    for player in ["P1", "P2"]:
        opponent = "P2" if player == "P1" else "P1"
        for own_type, enemy_type in [("ROCK", "SCISSORS"), ("SCISSORS", "ROCK")]:
            actions = [{"type": "MOVE", "pieceId": "own", "from": {"row": 4, "col": 4}, "to": pos}
                       for pos in [{"row": 3, "col": 4}, {"row": 4, "col": 5}]]
            pieces = [{"id": name, "ownerId": owner, "type": kind, "status": "ALIVE", "position": pos}
                      for name, owner, kind, pos in [
                          ("own", player, own_type, {"row": 4, "col": 4}),
                          ("target", opponent, enemy_type, {"row": 3, "col": 4}),
                          ("danger", opponent, "PAPER", {"row": 4, "col": 6})]]
            turns.append({"type": "YOUR_TURN", "sequence": 100 + len(turns), "player": player,
                          "state": {"board": {"rows": 9, "cols": 9, "pieces": pieces}}, "legalActions": actions})
            choices.append(actions[0 if own_type == "ROCK" else 1])
    verify("Python strategy combat/safety for P1 and P2", [sys.executable, "-I", "-u", str(examples / "tactical.py")], turns, choices)
    compiler = shutil.which("g++")
    if not compiler:
        raise SystemExit("g++ is required to verify the C++ starter")
    binary = build / ("bot" + extension)
    subprocess.run([compiler, "-std=c++17", "-O2", str(examples / "main.cpp"), "-o", str(binary)], check=True, timeout=30)
    verify("C++17", [str(binary)])
    subprocess.run([java_tool("javac"), "--release", "21", "-d", str(build), str(examples / "Main.java")], check=True, timeout=30)
    verify("Java 21", [java_tool("java"), "-cp", str(build), "Main"])
    jar = build / "bot.jar"
    subprocess.run([java_tool("jar"), "--create", "--file", str(jar), "--main-class", "Main", "-C", str(build), "Main.class"], check=True, timeout=15)
    verify("Executable JAR", [java_tool("java"), "-jar", str(jar)])
