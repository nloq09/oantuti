export class TurnTimer {
  constructor(element) {
    this.element = element;
    this.interval = setInterval(() => this.render(), 100);
  }
  update(snapshot) {
    this.deadline = snapshot.deadline ? Date.parse(snapshot.deadline) : null;
    this.offset = Date.parse(snapshot.serverTime) - Date.now();
    this.render();
  }
  render() {
    const seconds = this.deadline
      ? Math.max(
          0,
          Math.ceil((this.deadline - Date.now() - this.offset) / 1000),
        )
      : null;
    this.element.textContent = seconds === null ? "—" : seconds + "s";
    this.element.classList.toggle("urgent", seconds !== null && seconds <= 3);
  }
  stop() {
    clearInterval(this.interval);
  }
}
