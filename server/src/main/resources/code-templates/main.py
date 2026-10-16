"""JSON Lines bot. Replace choose_action with your strategy; stdout is protocol only."""
import json
import sys


def choose_action(turn):
    return turn["legalActions"][0]


for line in sys.stdin:
    turn = json.loads(line)
    response = {"type": "ACTION", "sequence": turn["sequence"], "action": choose_action(turn)}
    print(json.dumps(response, separators=(",", ":")), flush=True)
