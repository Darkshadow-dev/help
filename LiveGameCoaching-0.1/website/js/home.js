const form = document.getElementById("join-form");
const codeInput = document.getElementById("room-code");
const statusEl = document.getElementById("landing-status");

codeInput.addEventListener("input", () => {
  codeInput.value = codeInput.value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 6);
});

form.addEventListener("submit", event => {
  event.preventDefault();
  const code = codeInput.value.trim();
  if (code.length !== 6) {
    statusEl.textContent = "Enter the 6-character room code.";
    return;
  }
  location.href = `/room.html?code=${encodeURIComponent(code)}`;
});
