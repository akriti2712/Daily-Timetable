// Elements
const registerScreen = document.getElementById("register-screen");
const modeScreen = document.getElementById("mode-screen");
const gameScreen = document.getElementById("game-screen");

const startGameBtn = document.getElementById("startGame");
const playerNameInput = document.getElementById("playerName");
const displayName = document.getElementById("displayName");

const vsComputerBtn = document.getElementById("vsComputer");
const vsPlayerBtn = document.getElementById("vsPlayer");

const boardEl = document.getElementById("board");
const statusText = document.getElementById("status");
const resetBtn = document.getElementById("resetBtn");
const backToMenuBtn = document.getElementById("backToMenu");
const gameTitle = document.getElementById("gameTitle");
const playersInfo = document.getElementById("players");

const scoreXEl = document.getElementById("scoreX");
const scoreOEl = document.getElementById("scoreO");
const scoreDrawEl = document.getElementById("scoreDraw");
const nameXEl = document.getElementById("nameX");
const nameOEl = document.getElementById("nameO");

// Congrats modal
const congratsModal = document.getElementById("congratsModal");
const congratsTitle = document.getElementById("congratsTitle");
const congratsMsg = document.getElementById("congratsMsg");
const closeCongrats = document.getElementById("closeCongrats");
const playAgain = document.getElementById("playAgain");
const menuFromCongrats = document.getElementById("menuFromCongrats");
const sessionWinsEl = document.getElementById("sessionWins");
const highScoreEl = document.getElementById("highScore");
const confettiArea = document.getElementById("confettiArea");

// Game state
let playerName = "";
let player2Name = "Player 2";
let gameMode = "player"; // "computer" or "player"

let cells = Array(9).fill("");
let currentPlayer = "X";
let gameActive = false;
let lastWinningCombo = null;

// Score state (persistent)
const STORAGE_KEY = "tictactoe_scores_v1"; // { playerName: { wins: n }, other: ... , highScore: n }
let scoreStore = JSON.parse(localStorage.getItem(STORAGE_KEY)) || {
    highScore: 0,
    players: {}
};

// Session counters
let sessionWins = {
    X: 0,
    O: 0,
    draw: 0
};

// winning combos
const winningCombos = [
    [0, 1, 2],
    [3, 4, 5],
    [6, 7, 8],
    [0, 3, 6],
    [1, 4, 7],
    [2, 5, 8],
    [0, 4, 8],
    [2, 4, 6]
];

// -------------------------------------------
// Utility: show/hide screens
function showScreen(screenEl) {
    [registerScreen, modeScreen, gameScreen].forEach(s => {
        s.classList.add("hidden");
        s.classList.remove("active-screen");
    });
    screenEl.classList.remove("hidden");
    screenEl.classList.add("active-screen");
}

// -------------------------------------------
// Sound generation using WebAudio API (no external files)
const audioCtx = new(window.AudioContext || window.webkitAudioContext)();

function playTone(freq = 440, duration = 150, type = "sine", volume = 0.12) {
    try {
        const o = audioCtx.createOscillator();
        const g = audioCtx.createGain();
        o.type = type;
        o.frequency.setValueAtTime(freq, audioCtx.currentTime);
        g.gain.setValueAtTime(volume, audioCtx.currentTime);
        o.connect(g);
        g.connect(audioCtx.destination);
        o.start();
        setTimeout(() => {
            o.stop();
        }, duration);
    } catch (e) {
        // silent fallback if audio blocked
        console.warn("Audio error", e);
    }
}

function playMoveSound() {
    playTone(550, 80, "sine", 0.06);
}

function playWinSound() {
    // short celebratory arpeggio
    playTone(880, 140, "sine", 0.08);
    setTimeout(() => playTone(660, 120, "sine", 0.07), 140);
    setTimeout(() => playTone(990, 160, "sine", 0.08), 260);
}

function playDrawSound() {
    playTone(300, 180, "sawtooth", 0.06);
}

// -------------------------------------------
// Registration
startGameBtn.addEventListener("click", () => {
    const name = playerNameInput.value.trim();
    if (!name) {
        alert("Please enter your name.");
        return;
    }
    playerName = name;
    displayName.textContent = playerName;
    showScreen(modeScreen);
});

// Mode selection
vsComputerBtn.addEventListener("click", () => startMatch("computer"));
vsPlayerBtn.addEventListener("click", () => startMatch("player"));

function startMatch(mode) {
    gameMode = mode;
    player2Name = mode === "computer" ? "Computer" : "Player 2";
    showScreen(gameScreen);
    gameTitle.textContent = mode === "computer" ? "You vs Computer 🤖" : "2 Player Mode 👥";
    playersInfo.textContent = `${playerName} (X)  —  ${player2Name} (O)`;
    nameXEl.textContent = `${playerName} (X)`;
    nameOEl.textContent = `${player2Name} (O)`;
    initBoard();
    refreshScoreboard();
}

// -------------------------------------------
// Board init
function initBoard() {
    boardEl.innerHTML = "";
    cells = Array(9).fill("");
    currentPlayer = "X";
    gameActive = true;
    lastWinningCombo = null;
    statusText.textContent = `${getCurrentPlayerName()}'s Turn (${currentPlayer})`;
    for (let i = 0; i < 9; i++) {
        const cell = document.createElement("div");
        cell.classList.add("cell");
        cell.dataset.index = i;
        cell.setAttribute("role", "button");
        cell.addEventListener("click", onCellClick);
        boardEl.appendChild(cell);
    }
    // reset session highlights
    boardEl.querySelectorAll(".cell").forEach(c => c.style.background = "");
}

// -------------------------------------------
function getCurrentPlayerName() {
    if (gameMode === "computer") return currentPlayer === "X" ? playerName : "Computer";
    return currentPlayer === "X" ? playerName : player2Name;
}

// -------------------------------------------
// Cell click handler
function onCellClick(e) {
    if (!gameActive) return;
    const idx = Number(e.currentTarget.dataset.index);
    if (cells[idx] !== "") return;

    makeMove(idx, currentPlayer);
    playMoveSound();

    if (checkWinner()) {
        gameActive = false;
        statusText.textContent = `${getCurrentPlayerName()} Wins 🎉`;
        playWinSound();
        sessionWins[currentPlayer] += 1;
        updatePersistentScore(currentPlayer);
        highlightWinningCombo();
        showCongrats(getCurrentPlayerName());
        refreshScoreboard();
        return;
    }

    if (!cells.includes("")) {
        gameActive = false;
        statusText.textContent = "It's a Draw! 🤝";
        playDrawSound();
        sessionWins.draw += 1;
        persistDraw();
        refreshScoreboard();
        showDrawPopup();
        return;
    }

    // switch player
    currentPlayer = currentPlayer === "X" ? "O" : "X";
    statusText.textContent = `${getCurrentPlayerName()}'s Turn (${currentPlayer})`;

    // computer move if applicable
    if (gameMode === "computer" && currentPlayer === "O" && gameActive) {
        setTimeout(computerMove, 500);
    }
}

// write a move
function makeMove(index, player) {
    cells[index] = player;
    const el = boardEl.querySelector(`.cell[data-index='${index}']`);
    if (el) el.textContent = player;
}

// check winner
function checkWinner() {
    lastWinningCombo = null;
    for (const combo of winningCombos) {
        const [a, b, c] = combo;
        if (cells[a] && cells[a] === cells[b] && cells[a] === cells[c]) {
            lastWinningCombo = combo;
            return true;
        }
    }
    return false;
}

// highlight winning combo
function highlightWinningCombo() {
    if (!lastWinningCombo) return;
    lastWinningCombo.forEach(i => {
        const el = boardEl.querySelector(`.cell[data-index='${i}']`);
        if (el) {
            el.classList.add("win-animate");
        }
    });
}

// -------------------------------------------
// Computer move (random fallback). Could upgrade to Minimax later.
function computerMove() {
    if (!gameActive) return;
    const available = cells.map((v, i) => v === "" ? i : null).filter(i => i !== null);
    if (available.length === 0) return;

    // simple "smart-ish" pick: if computer can win this move, pick it; else block player if possible; else random.
    // try win
    for (const i of available) {
        const copy = [...cells];
        copy[i] = "O";
        if (isWinningBoard(copy, "O")) {
            makeMove(i, "O");
            playMoveSound();
            afterComputerMove();
            return;
        }
    }
    // try block
    for (const i of available) {
        const copy = [...cells];
        copy[i] = "X";
        if (isWinningBoard(copy, "X")) {
            makeMove(i, "O");
            playMoveSound();
            afterComputerMove();
            return;
        }
    }
    // else random or center preference
    if (available.includes(4)) {
        makeMove(4, "O");
        playMoveSound();
        afterComputerMove();
        return;
    }
    const choice = available[Math.floor(Math.random() * available.length)];
    makeMove(choice, "O");
    playMoveSound();
    afterComputerMove();
}

function afterComputerMove() {
    if (checkWinner()) {
        gameActive = false;
        statusText.textContent = `${getCurrentPlayerName()} Wins 🎉`;
        playWinSound();
        sessionWins[currentPlayer] += 1;
        updatePersistentScore(currentPlayer);
        highlightWinningCombo();
        showCongrats(getCurrentPlayerName());
        refreshScoreboard();
        return;
    }
    if (!cells.includes("")) {
        gameActive = false;
        statusText.textContent = "It's a Draw! 🤝";
        playDrawSound();
        sessionWins.draw += 1;
        persistDraw();
        refreshScoreboard();
        showDrawPopup();
        return;
    }
    currentPlayer = "X";
    statusText.textContent = `${getCurrentPlayerName()}'s Turn (${currentPlayer})`;
}

// helper to test win for hypothetical board
function isWinningBoard(boardArr, player) {
    return winningCombos.some(c => boardArr[c[0]] === player && boardArr[c[1]] === player && boardArr[c[2]] === player);
}

// -------------------------------------------
// Reset & Back
resetBtn.addEventListener("click", () => {
    initBoard();
    // clear animations
    boardEl.querySelectorAll(".cell").forEach(c => {
        c.classList.remove("win-animate");
        c.style.background = "";
    });
});

backToMenuBtn.addEventListener("click", () => {
    initBoard();
    showScreen(modeScreen);
});

// -------------------------------------------
// Score persistence logic
function updatePersistentScore(winnerPlayer) {
    // winnerPlayer is 'X' or 'O'
    // we attribute wins to names: X -> playerName, O -> player2Name (or Computer)
    const name = winnerPlayer === "X" ? playerName : player2Name;
    if (!scoreStore.players[name]) scoreStore.players[name] = {
        wins: 0
    };
    scoreStore.players[name].wins += 1;

    // update high score if needed (max wins among all players)
    const allWins = Object.values(scoreStore.players).map(p => p.wins);
    const maxWins = allWins.length ? Math.max(...allWins) : 0;
    if (maxWins > (scoreStore.highScore || 0)) scoreStore.highScore = maxWins;

    // persist
    localStorage.setItem(STORAGE_KEY, JSON.stringify(scoreStore));
}

function persistDraw() {
    // track draws aggregated separately on session only; draws not recorded into high score
    // optionally could store draws per user --> omitted for simplicity
    localStorage.setItem(STORAGE_KEY, JSON.stringify(scoreStore));
}

// refresh scoreboard area with session counts + persistent high score
function refreshScoreboard() {
    scoreXEl.textContent = sessionWins.X;
    scoreOEl.textContent = sessionWins.O;
    scoreDrawEl.textContent = sessionWins.draw;
    highScoreEl && (highScoreEl.textContent = scoreStore.highScore || 0);
}

// -------------------------------------------
// Congrats / Draw popups + confetti
function showCongrats(winnerName) {
    congratsTitle.textContent = "Congratulations!";
    congratsMsg.textContent = `${winnerName} wins! 🎉`;
    sessionWinsEl.textContent = currentPlayer === "X" ? sessionWins.X : sessionWins.O;
    highScoreEl.textContent = scoreStore.highScore || 0;
    spawnConfetti();
    congratsModal.classList.remove("hidden");
    // stop accepting clicks on board
    gameActive = false;
}

function showDrawPopup() {
    congratsTitle.textContent = "It's a Draw!";
    congratsMsg.textContent = `Nobody wins — nice match!`;
    sessionWinsEl.textContent = sessionWins.draw;
    highScoreEl.textContent = scoreStore.highScore || 0;
    // small confetti but different style
    spawnConfetti(40);
    congratsModal.classList.remove("hidden");
    gameActive = false;
}

closeCongrats.addEventListener("click", () => {
    congratsModal.classList.add("hidden");
    clearConfetti();
});

playAgain.addEventListener("click", () => {
    congratsModal.classList.add("hidden");
    clearConfetti();
    initBoard();
});

menuFromCongrats.addEventListener("click", () => {
    congratsModal.classList.add("hidden");
    clearConfetti();
    showScreen(modeScreen);
});

// spawn confetti: creates small colored divs that fall
function spawnConfetti(count = 80) {
    clearConfetti();
    const colors = ["#f44336", "#ff9800", "#ffc107", "#8bc34a", "#00bcd4", "#3f51b5", "#e91e63"];
    const w = confettiArea.clientWidth || window.innerWidth;
    for (let i = 0; i < count; i++) {
        const el = document.createElement("div");
        el.classList.add("confetti");
        el.style.background = colors[Math.floor(Math.random() * colors.length)];
        el.style.left = (Math.random() * 100) + "%";
        el.style.top = (-10 - Math.random() * 20) + "%";
        el.style.width = (8 + Math.random() * 10) + "px";
        el.style.height = (12 + Math.random() * 18) + "px";
        el.style.opacity = 0.9;
        el.style.animationDuration = (2 + Math.random() * 2) + "s";
        el.style.transform = `rotate(${Math.random()*360}deg)`;
        confettiArea.appendChild(el);
    }
    // remove confetti after 3.5s
    setTimeout(clearConfetti, 3800);
}

function clearConfetti() {
    confettiArea.innerHTML = "";
}

// -------------------------------------------
// On load, try to resume stored highscore and show if needed
(function onLoad() {
    // ensure audio context is resumed on first user gesture
    document.addEventListener("click", () => {
        if (audioCtx.state === "suspended") audioCtx.resume();
    }, {
        once: true
    });

    // if stored players exist, we can show top/highscore in console or later in UI
    refreshScoreboard();
})();

// -------------------------------------------
// If user closes tab or reloads, we keep scoreStore persistent; sessionWins reset happens on reload (intended)