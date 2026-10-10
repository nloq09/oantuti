"""One-ply strategy example. All responses reuse an action supplied by the server."""
import json
import sys

BEATS = {"ROCK": "SCISSORS", "SCISSORS": "PAPER", "PAPER": "ROCK"}


def cell(position):
    return position["row"], position["col"]


def distance(a, b):
    return abs(a[0] - b[0]) + abs(a[1] - b[1])


def evaluate(action, request):
    if action["type"] != "MOVE":
        return 0
    pieces = [p for p in request["state"]["board"]["pieces"] if p["status"] == "ALIVE"]
    by_id = {p["id"]: p for p in pieces}
    board = {cell(p["position"]): p for p in pieces}
    attacker = by_id[action["pieceId"]]
    origin, target = cell(action["from"]), cell(action["to"])
    defender = board.get(target)
    score = 0
    del board[origin]

    if defender:
        if attacker["type"] == defender["type"]:
            del board[target]  # Same type: both pieces disappear.
            return 0
        if BEATS[attacker["type"]] != defender["type"]:
            return -100  # Attacker disappears; defender remains.
        score += 100
    board[target] = attacker  # Moving onto an empty cell or winning combat.

    enemies = [(pos, p) for pos, p in board.items() if p["ownerId"] != request["player"]]
    threats = sum(
        distance(pos, target) == 1
        and (BEATS[p["type"]] == attacker["type"] or p["type"] == attacker["type"])
        for pos, p in enemies
    )
    score -= 120 * threats
    prey = [pos for pos, p in enemies if p["type"] == BEATS[attacker["type"]]]
    if prey:
        score += 2 * (min(distance(origin, pos) for pos in prey) - min(distance(target, pos) for pos in prey))
    return score


def choose_action(request):
    return max(request["legalActions"], key=lambda action: evaluate(action, request))


def main():
    for line in sys.stdin:
        request = json.loads(line)
        print(json.dumps({"type": "ACTION", "sequence": request["sequence"],
                          "action": choose_action(request)}, separators=(",", ":")), flush=True)


if __name__ == "__main__":
    main()
