export const labels = { ROCK: "Búa", PAPER: "Bao", SCISSORS: "Kéo" };
export const icon = (type, owner = "P1") =>
  `/assets/pieces/${type.toLowerCase()}-${owner === "P1" ? "blue" : "red"}.svg`;
export const seatKey = "rps-seat";
