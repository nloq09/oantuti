import { ClientState } from "./game-state.js?v=stage3";
import { renderBoard } from "./board-renderer.js?v=strongholds";
import { boardClick } from "./input-controller.js";
import { AnimationController } from "./animation-controller.js";
import { GameSocket } from "../socket/game-socket.js?v=stage3";
import { TurnTimer } from "../ui/timer.js";
import { toast } from "../ui/toast.js";
import { modal, confirmAction } from "../ui/modal.js";
import { sound, openSettings } from "../ui/sound.js";
import { labels, seatKey, icon } from "../config.js";
import { createMatch } from "../api/match-api.js";
export class GameController {
  constructor(seat) {
    this.state = new ClientState();
    this.state.role = seat.role || "PLAYER";
    this.seat = seat;
    this.board = document.querySelector("#board");
    this.animator = new AnimationController(this.board);
    this.timer = new TurnTimer(document.querySelector("#timer"));
    this.queue = Promise.resolve();
    this.finished = false;
    this.seen = new Set();
    this.socket = new GameSocket(
      {
        matchId: seat.matchId,
        playerId: seat.playerId,
        token: seat.token,
        ...(seat.role ? { role: seat.role } : {}),
      },
      (message) => this.receive(message),
      (status) => this.connection(status),
    );
    this.bind();
    window.addEventListener("pagehide", () => {
      this.socket.close();
      this.timer.stop();
    });
  }
  send(type, payload, sequence) {
    if (!this.state.interactive) return;
    if (this.socket.send(type, payload, sequence)) {
      this.state.pending = true;
      this.render();
    }
  }
  connection(status) {
    this.state.connected = status === "connected";
    const node = document.querySelector("#connection");
    node.textContent = {
      connected: "● Đã kết nối",
      connecting: "◌ Đang kết nối…",
      disconnected: "○ Mất kết nối — đang thử lại",
      error: "Lỗi dữ liệu",
    }[status];
    node.className = status;
    if (status === "disconnected" && !this.disconnectDialog)
      this.disconnectDialog = modal(
        "Mất kết nối",
        "Đang kết nối lại. Bạn sẽ nhận bàn cờ mới nhất khi kết nối thành công.",
        [{ label: "Đóng" }],
      );
    if (status === "connected") {
      this.disconnectDialog?.close();
      this.disconnectDialog = null;
    }
    this.render();
  }
  receive(message) {
    if (message.type === "ACTION_REJECTED") {
      this.state.pending = false;
      toast(message.payload.error, true);
      this.render();
      return;
    }
    if (message.type === "LEFT_ROOM") {
      this.socket.close();
      sessionStorage.removeItem(seatKey);
      location.href = "/";
      return;
    }
    if (message.type !== "GAME_STATE") return;
    const snapshot = message.payload.snapshot;
    this.timer.update(snapshot);
    this.state.pending = false;
    this.queue = this.queue
      .then(async () => {
        if (
          this.state.game &&
          snapshot.gameState.sequence < this.state.game.sequence
        )
          return;
        const events = snapshot.gameState.events.filter((e) => {
          const key =
            e.sequence + ":" + e.type + ":" + JSON.stringify(e.payload);
          if (this.seen.has(key)) return false;
          this.seen.add(key);
          return true;
        });
        this.state.busy = true;
        this.render();
        if (this.state.snapshot && !message.resync)
          await this.animator.play(
            events,
            this.state.role === "PLAYER" &&
              this.state.snapshot.players.find(
                (p) => p.playerId === this.state.game.currentPlayer,
              )?.bot &&
              events.length > 0,
          );
        this.state.snapshot = snapshot;
        this.state.selected = null;
        this.state.busy = false;
        this.render();
        this.log(events);
        if (snapshot.botError && !this.botErrorShown) {
          this.botErrorShown = true;
          toast(snapshot.botError, true);
        }
        if (snapshot.status === "FINISHED") this.result(snapshot);
      })
      .catch(() => {
        this.state.busy = false;
        toast("Không thể hiển thị hiệu ứng. Đang tải lại bàn cờ.", true);
        this.socket.send(
          this.state.role === "SPECTATOR" ? "SPECTATE_MATCH" : "STATE_REQUEST",
        );
      });
  }
  bind() {
    document.querySelector("#clear-log").onclick = () =>
      document.querySelector("#event-log").replaceChildren();
    document.querySelector(".brand").onclick = (event) => {
      event.preventDefault();
      if (this.state.connected) document.querySelector("#leave").click();
      else location.href = "/";
    };
    document.querySelector("#settings").onclick = openSettings;
    document.querySelector("#random").onclick = () =>
      this.send("RANDOM_PLACEMENT");
    document.querySelector("#reset").onclick = () =>
      this.send("RESET_PLACEMENT");
    document.querySelector("#ready").onclick = () =>
      this.send(
        this.state.snapshot.players.find(
          (p) => p.playerId === this.seat.playerId,
        )?.ready
          ? "UNREADY"
          : "READY",
      );
    document.querySelector("#pass").onclick = () => {
      const action = this.state.game.legalActions.find(
        (a) => a.type === "PASS",
      );
      if (action) this.send("ACTION", action, this.state.game.sequence);
    };
    document.querySelector("#surrender").onclick = () =>
      confirmAction("Đầu hàng?", "Đối thủ sẽ thắng ván này.", () =>
        this.send("ACTION", { type: "SURRENDER" }, this.state.game.sequence),
      );
    document.querySelector("#leave").onclick = () =>
      confirmAction(
        "Rời phòng?",
        this.state.snapshot?.status === "PLAYING"
          ? "Rời ván đang chơi được tính là đầu hàng."
          : "Bạn sẽ quay về trang chủ.",
        () => this.send("LEAVE"),
      );
    document.querySelector("#copy").onclick = async () => {
      try {
        await navigator.clipboard.writeText(this.seat.matchId);
        toast("Đã sao chép mã phòng.");
      } catch {
        toast("Mã phòng: " + this.seat.matchId);
      }
    };
    document.querySelectorAll("[data-type]").forEach(
      (button) =>
        (button.onclick = () => {
          this.state.type = button.dataset.type;
          this.state.selected = null;
          sound("select");
          this.render();
        }),
    );
  }
  render() {
    const s = this.state.snapshot;
    if (!s) return;
    const g = s.gameState,
      me = s.players.find((p) => p.playerId === s.playerId),
      placement = s.status === "PLACEMENT";
    const ownArmy = document.querySelector("#army-" + s.playerId),
      opponentArmy = document.querySelector(
        "#army-" + (s.playerId === "P1" ? "P2" : "P1"),
      );
    if (ownArmy.parentElement.id !== "own-army")
      document.querySelector("#own-army").append(ownArmy);
    if (opponentArmy.parentElement.id !== "opponent-army")
      document.querySelector("#opponent-army").append(opponentArmy);
    document.querySelector("#room-code").textContent = this.seat.matchId;
    document.querySelector("#identity").textContent =
      `Bạn: ${s.playerId === "P1" ? "Xanh" : "Đỏ"} · ${s.players.every((player) => player.bot) ? "Bot đấu bot" : s.mode === "HUMAN_VS_BOT" ? "Đấu với bot" : "Hai người"}`;
    document.querySelector("#phase").textContent =
      s.status === "WAITING"
        ? "Đang chờ người thứ hai"
        : placement
          ? me.ready
            ? "Đã sẵn sàng — chờ đối thủ"
            : "Đặt đội hình của bạn"
          : s.status === "FINISHED"
            ? "Ván đã kết thúc"
            : this.state.busy
              ? "Đang diễn hoạt lượt chơi…"
              : this.state.ownTurn
                ? "Đến lượt bạn"
                : s.players.find((p) => p.playerId === g.currentPlayer)?.bot
                  ? "Bot đang suy nghĩ…"
                  : "Đến lượt đối thủ";
    document.querySelector("#turn").textContent = "Lượt " + g.turn;
    document.querySelector("#placement-panel").hidden = !placement;
    document.querySelector("#playing-controls").hidden = s.status !== "PLAYING";
    document.querySelector("#waiting").hidden = s.status !== "WAITING";
    document.querySelector("#players").replaceChildren(
      ...s.players.map((p) => {
        const item = document.createElement("div");
        item.className = "player " + p.playerId.toLowerCase();
        item.textContent = `${p.playerId === "P1" ? "Xanh" : "Đỏ"} · ${p.joined ? (p.bot ? p.botName || "Bot chiến thuật" : p.connected ? "Online" : "Offline") : "Chưa tham gia"}${p.ready ? " · Sẵn sàng" : ""}`;
        return item;
      }),
    );
    for (const owner of ["P1", "P2"]) {
      const living = g.pieces.filter(
        (p) => p.owner === owner && p.status === "ALIVE",
      );
      document.querySelector("#count-" + owner).textContent = living.length;
      document
        .querySelectorAll(`[data-army-owner="${owner}"]`)
        .forEach(
          (node) =>
            (node.textContent = living.filter(
              (piece) => piece.type === node.dataset.armyType,
            ).length),
        );
      document.querySelector("#types-" + owner).textContent = Object.entries(
        labels,
      )
        .map(
          ([type, label]) =>
            label + ": " + living.filter((p) => p.type === type).length,
        )
        .join(" · ");
    }
    document.querySelectorAll("[data-type]").forEach((button) => {
      const type = button.dataset.type;
      button.classList.toggle("chosen", type === this.state.type);
      button.querySelector("img").src = icon(type, s.playerId);
      button.querySelector("span").textContent =
        `${labels[type]} · ${s.remaining[type]}/6`;
      button.disabled = !this.state.interactive || me.ready;
    });
    for (const id of ["random", "reset"])
      document.querySelector("#" + id).disabled =
        !this.state.interactive || me.ready;
    const ready = document.querySelector("#ready");
    ready.textContent = me.ready ? "Hủy sẵn sàng" : "Sẵn sàng";
    ready.disabled =
      me.bot ||
      !this.state.interactive ||
      (!me.ready && Object.values(s.remaining).some((n) => n !== 0));
    document.querySelector("#pass").disabled =
      !this.state.interactive || !this.state.ownTurn;
    document.querySelector("#surrender").disabled = !this.state.interactive;
    document.querySelector("#leave").disabled =
      !this.state.connected || this.state.pending;
    renderBoard(this.board, this.state, (position, piece, action) => {
      boardClick(
        this.state,
        (...args) => this.send(...args),
        position,
        piece,
        action,
      );
      this.render();
    });
  }
  log(events) {
    const list = document.querySelector("#event-log");
    for (const e of events) {
      let text;
      const p = e.payload;
      if (e.type === "PIECE_MOVED")
        text = `${p.pieceId.split("-")[0]} đi ${String.fromCharCode(65 + p.from.col)}${p.from.row + 1} → ${String.fromCharCode(65 + p.to.col)}${p.to.row + 1}`;
      else if (e.type === "COMBAT_OCCURRED")
        text = `${labels[p.attacker.type]} đấu ${labels[p.defender.type]} · ${{ ATTACKER_WIN: "Tấn công thắng", DEFENDER_WIN: "Phòng thủ thắng", BOTH_DIE: "Cả hai bị loại", DRAW: "Hòa" }[p.result] || p.result}`;
      else if (e.type === "PIECE_REMOVED")
        text = `${labels[p.piece.type]} ${p.piece.owner} bị loại`;
      else if (e.type === "TURN_CHANGED") text = `Đến lượt ${p.currentPlayer}`;
      else if (e.type === "MATCH_STARTED") text = "Ván chơi bắt đầu";
      else if (e.type === "GAME_FINISHED") text = "Ván chơi kết thúc";
      if (text) {
        const li = document.createElement("li");
        li.textContent = text;
        list.prepend(li);
      }
    }
    while (list.children.length > 20) list.lastChild.remove();
  }
  result(snapshot) {
    sessionStorage.setItem("rps-result", JSON.stringify(snapshot));
    if (this.finished) return;
    this.finished = true;
    const won = snapshot.gameState.winner === snapshot.playerId,
      draw = !snapshot.gameState.winner;
    sound(won ? "victory" : "defeat");
    modal(
      draw ? "Hòa!" : won ? "Bạn thắng!" : "Bạn thua",
      `Ván kết thúc sau ${snapshot.gameState.turn} lượt. Xanh còn ${document.querySelector("#count-P1").textContent} quân, Đỏ còn ${document.querySelector("#count-P2").textContent} quân.`,
      [
        { label: "Chi tiết", run: () => (location.href = "/result.html") },
        { label: "Trang chủ", run: () => (location.href = "/") },
        {
          label:
            snapshot.mode === "HUMAN_VS_BOT"
              ? "Chơi lại với bot"
              : "Chơi ván mới",
          primary: true,
          run: async () => {
            if (snapshot.mode === "HUMAN_VS_BOT") {
              try {
                sessionStorage.setItem(
                  seatKey,
                  JSON.stringify(
                    await createMatch("HUMAN_VS_BOT", this.seat.bot),
                  ),
                );
                location.reload();
              } catch (error) {
                toast(error.message, true);
              }
            } else location.href = "/lobby.html";
          },
        },
      ],
    );
  }
}
