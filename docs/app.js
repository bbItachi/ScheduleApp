// ═══════════════════════════════════════════════════════════
//  КОНСТАНТЫ
// ═══════════════════════════════════════════════════════════

const SCHEDULE_URL = "https://api.allorigins.win/raw?url=http://xn--j1ahcbhc.xn--p1ai/rasp.xlsx";
const APK_URL = "https://github.com/bbItachi/ScheduleApp/releases/latest";
const CHANNEL_URL = "https://max.ru/channel_rasp_spk";
const STORAGE_KEY = "spk_schedule";
const SETTINGS_KEY = "spk_settings";
const BASE_WEEK = new Date(2026, 9, 5); // 5 октября 2026 (месяц 0-indexed)
const DAYS_RU = ["ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА", "ВОСКРЕСЕНЬЕ"];

// ═══════════════════════════════════════════════════════════
//  ГЛОБАЛЬНОЕ СОСТОЯНИЕ
// ═══════════════════════════════════════════════════════════

let lessons = [];
let settings = {
  role: "",
  group: "",
  teacherName: "",
  theme: "AUTO",
  lastUpdate: 0,
  morningHour: 7,
  morningMinute: 0,
  beforeMinutes: 15,
  favorites: [],
  skipped: [],
  notes: {}
};

// ═══════════════════════════════════════════════════════════
//  ХРАНИЛИЩЕ
// ═══════════════════════════════════════════════════════════

function loadFromStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) lessons = JSON.parse(raw);
    const s = localStorage.getItem(SETTINGS_KEY);
    if (s) settings = { ...settings, ...JSON.parse(s) };
  } catch (e) { console.warn("load error", e); }
}

function saveSchedule(data) {
  lessons = data;
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(data)); } catch (e) {}
}

function saveSettings() {
  try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings)); } catch (e) {}
}

// ═══════════════════════════════════════════════════════════
//  ПАРСИНГ XLSX (через SheetJS)
// ═══════════════════════════════════════════════════════════

function parseXlsx(arrayBuffer) {
  if (typeof XLSX === "undefined") {
    throw new Error("Библиотека XLSX не загрузилась. Обновите страницу.");
  }
  const wb = XLSX.read(arrayBuffer, { type: "array" });
  const ws = wb.Sheets[wb.SheetNames[0]];
  const rows = XLSX.utils.sheet_to_json(ws, { header: 1, raw: false, defval: "" });

  let groupRow = -1;
  for (let r = 0; r < rows.length; r++) {
    for (let c = 0; c < (rows[r] || []).length; c++) {
      const v = String(rows[r][c] || "").trim().toLowerCase();
      if (v === "группа:" || v === "группа") { groupRow = r; break; }
    }
    if (groupRow >= 0) break;
  }
  if (groupRow < 0) throw new Error("Не найдена строка с группами");

  const groups = [];
  const groupRowData = rows[groupRow] || [];
  for (let c = 0; c < groupRowData.length; c++) {
    const v = String(groupRowData[c] || "").trim();
    if (!v) continue;
    if (/^группа:?$/i.test(v)) continue;
    if (/^подгруппа:?$/i.test(v)) continue;
    if (!/[А-Яа-яA-Za-z]/.test(v)) continue;
    if (v.length < 3 || v.length > 40) continue;
    groups.push({ col: c, name: v });
  }
  if (groups.length === 0) throw new Error("Группы не найдены");

  const result = [];
  let currentDay = null;
  let week = "ODD";

  for (let r = groupRow + 1; r < rows.length; r++) {
    const row = rows[r] || [];

    for (let c = 0; c < row.length; c++) {
      const v = String(row[c] || "");
      if (/чётная|четная/i.test(v)) { week = "EVEN"; break; }
    }

    for (let c = 0; c < 3; c++) {
      const v = String(row[c] || "").trim().toUpperCase();
      if (DAYS_RU.includes(v)) { currentDay = v; break; }
    }

    const pairNum = parseInt(String(row[2] || "").trim(), 10);
    if (!pairNum || !currentDay) continue;

    const time = String(row[4] || "").trim();
    if (!time) continue;

    for (let i = 0; i < groups.length; i++) {
      const { col: colStart, name: groupName } = groups[i];
      const rowCells = [0, 1, 2, 3].map(k => String(row[colStart + k] || "").trim());
      const teachRow = rows[r + 1] || [];
      const teachCells = [0, 1, 2, 3].map(k => String(teachRow[colStart + k] || "").trim());

      const subjectPos = [];
      const roomPos = [];
      for (let k = 0; k < 4; k++) {
        const v = rowCells[k];
        if (!v) continue;
        if (looksLikeRoom(v)) roomPos.push(k); else subjectPos.push(k);
      }

      const subs = [];

      if (subjectPos.length >= 2 && roomPos.length >= 2) {
        for (let k = 0; k < 2; k++) {
          const si = subjectPos[k], ri = roomPos[k];
          const teacher = teachCells[si] || teachCells.find(x => x) || "";
          const { t, r } = cleanOnline(teacher, rowCells[ri]);
          subs.push({ index: k + 1, subject: rowCells[si], teacher: t, room: r });
        }
      } else if (subjectPos.length === 1) {
        const si = subjectPos[0];
        const ri = roomPos[0];
        const teacher = teachCells[si] || teachCells.find(x => x) || "";
        const { t, r } = cleanOnline(teacher, ri != null ? rowCells[ri] : "");
        subs.push({ index: 1, subject: rowCells[si], teacher: t, room: r });
      } else if (subjectPos.length >= 2) {
        const rawRoom = roomPos[0] != null ? rowCells[roomPos[0]] : "";
        for (let k = 0; k < 2; k++) {
          const si = subjectPos[k];
          const teacher = teachCells[si] || "";
          const { t, r } = cleanOnline(teacher, rawRoom);
          subs.push({ index: k + 1, subject: rowCells[si], teacher: t, room: r });
        }
      }

      if (subs.length > 0) {
        result.push({
          group: groupName,
          number: pairNum,
          time,
          day: currentDay,
          week,
          subgroups: subs
        });
      }
    }
  }
  return result;
}

function looksLikeRoom(v) {
  if (!v) return false;
  const t = v.trim();
  if (/онлайн/i.test(t)) {
    const w = t.replace(/онлайн/gi, "").trim().replace(/[,.;\-_ ]+$/g, "");
    return !/[А-Яа-яA-Za-z]/.test(w);
  }
  const upper = t.toUpperCase();
  if (["ЦРК", "М", "СЗ", "СЗ/1", "СЗ/2", "М/1", "М/2"].includes(upper)) return true;
  if (upper.startsWith("СЗ/") || upper.startsWith("М/")) return true;
  if (upper.startsWith("ЦРК")) return true;
  const digits = (t.match(/\d/g) || []).length;
  const letters = (t.match(/[А-Яа-яA-Za-z]/g) || []).length;
  return digits >= 1 && letters <= 2 && t.length <= 10 && !t.includes(" ");
}

function cleanOnline(teacher, room) {
  const t = (teacher || "").trim();
  const r = (room || "").trim();
  const hasOnline = /онлайн/i.test(t) || /онлайн/i.test(r);
  if (!hasOnline) return { t, r };
  const cleanT = t.replace(/\s*онлайн\s*/gi, " ").trim().replace(/[,.;\-_ ]+$/g, "");
  return { t: cleanT, r: "Онлайн" };
}

// ═══════════════════════════════════════════════════════════
//  ЛОГИКА НЕДЕЛИ
// ═══════════════════════════════════════════════════════════

function getCurrentWeek() {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const dow = today.getDay();
  const daysFromMonday = dow === 0 ? 6 : dow - 1;
  const monday = new Date(today);
  monday.setDate(today.getDate() - daysFromMonday);
  const diffDays = Math.floor((monday - BASE_WEEK) / 86400000);
  const weekNum = Math.floor(diffDays / 7);
  return (weekNum % 2 === 0) ? "EVEN" : "ODD";
}

function getDayName() {
  const dow = new Date().getDay();
  const map = ["ВОСКРЕСЕНЬЕ", "ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА"];
  return map[dow];
}

function weekLabel(w) { return w === "EVEN" ? "чётная" : "нечётная"; }

// ═══════════════════════════════════════════════════════════
//  ФИЛЬТРАЦИЯ ПАР
// ═══════════════════════════════════════════════════════════

function pairsForToday() {
  return filterPairs(getDayName(), getCurrentWeek());
}

function filterPairs(day, week) {
  if (settings.role === "teacher") {
    return lessons
      .filter(l => l.day === day && l.week === week &&
        l.subgroups.some(s => s.teacher && s.teacher.toLowerCase().includes(settings.teacherName.toLowerCase())))
      .sort((a, b) => a.number - b.number);
  }
  return lessons
    .filter(l => l.group === settings.group && l.day === day && l.week === week)
    .sort((a, b) => a.number - b.number);
}

// ═══════════════════════════════════════════════════════════
//  ТЕМА
// ═══════════════════════════════════════════════════════════

function applyTheme() {
  const hour = new Date().getHours();
  let isLight = false;
  if (settings.theme === "LIGHT") isLight = true;
  else if (settings.theme === "DARK") isLight = false;
  else isLight = hour >= 8 && hour < 20;

  document.body.classList.toggle("light", isLight);
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute("content", isLight ? "#F5F7FA" : "#0A0E14");
}

// ═══════════════════════════════════════════════════════════
//  РЕНДЕР
// ═══════════════════════════════════════════════════════════

function renderHeader() {
  const now = new Date();
  const dateStr = now.toLocaleDateString("ru-RU", { weekday: "long", day: "numeric", month: "long" });
  document.getElementById("date-text").textContent = dateStr.charAt(0).toUpperCase() + dateStr.slice(1);
  document.getElementById("time-text").textContent = now.toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" });
  document.getElementById("week-badge").textContent = weekLabel(getCurrentWeek());
  document.getElementById("week-badge-2").textContent = weekLabel(getCurrentWeek());

  const g = settings.role === "teacher" ? settings.teacherName : settings.group;
  document.getElementById("group-text").textContent = g || "не выбрана";
}

function renderPairStatus() {
  const pairs = pairsForToday();
  const el = document.getElementById("pair-status");
  if (pairs.length === 0) { el.classList.add("hidden"); return; }

  const now = new Date();
  const nowMin = now.getHours() * 60 + now.getMinutes();
  for (const p of pairs) {
    const [s, e] = p.time.split("-").map(t => {
      const [h, m] = t.trim().replace(".", ":").split(":").map(Number);
      return h * 60 + (m || 0);
    });
    if (nowMin >= s && nowMin < e) {
      el.textContent = `⏱ идёт ${p.number}-я пара · до конца ${e - nowMin} мин`;
      el.classList.remove("hidden");
      return;
    }
  }
  for (const p of pairs) {
    const [h, m] = p.time.split("-")[0].trim().replace(".", ":").split(":").map(Number);
    const s = h * 60 + (m || 0);
    if (s > nowMin) {
      const diff = s - nowMin;
      const hh = Math.floor(diff / 60);
      const mm = diff % 60;
      const t = hh > 0 ? `${hh} ч ${mm} мин` : `${mm} мин`;
      el.textContent = `⏱ через ${t} до ${p.number}-й пары`;
      el.classList.remove("hidden");
      return;
    }
  }
  el.classList.add("hidden");
}

function renderToday() {
  document.getElementById("day-header").textContent = `${getDayName()} (${weekLabel(getCurrentWeek())})`;
  const pairs = pairsForToday();
  const list = document.getElementById("pairs-list");

  if (pairs.length === 0) {
    list.innerHTML = `<div class="pair-empty"><div class="pair-empty-icon">🎉</div>Пар нет</div>`;
    return;
  }
  list.innerHTML = pairs.map(p => pairCardHtml(p)).join("");
  attachPairHandlers();
}

function pairCardHtml(p) {
  const key = `${p.group}|${p.day}|${p.week}|${p.number}`;
  const isFav = settings.favorites.includes(key);
  const isSkip = settings.skipped.includes(key);

  let subsHtml = "";
  for (const sg of p.subgroups) {
    const prefix = p.subgroups.length > 1 ? `Подгр. ${sg.index}: ` : "";
    const online = sg.room.toLowerCase().includes("онлайн");
    let lines = "";
    if (online) {
      lines += `<div class="pair-line online">🌐 Онлайн</div>`;
    } else {
      lines += `<div class="pair-line room">🏛 Ауд. ${escapeHtml(sg.room || "—")}</div>`;
    }
    if (settings.role === "teacher") {
      lines += `<div class="pair-line">👥 Группа ${escapeHtml(p.group)}</div>`;
    } else {
      lines += `<div class="pair-line">👤 ${escapeHtml(sg.teacher || "—")}</div>`;
    }
    subsHtml += `<div class="pair-sub">
      <div class="pair-subject ${isSkip ? 'skipped' : ''}">${escapeHtml(prefix + sg.subject)}</div>
      ${lines}
    </div>`;
  }

  return `<div class="pair-card ${isSkip ? 'skipped' : ''}" data-key="${escapeHtml(key)}">
    <div class="pair-head">
      <div class="pair-number">${p.number}</div>
      <div class="pair-time">${escapeHtml(p.time)}</div>
      <div class="pair-actions">
        <button class="pair-action ${isSkip ? 'active' : ''}" data-act="skip">${isSkip ? '▶️' : '✕'}</button>
        <button class="pair-action ${isFav ? 'active' : ''}" data-act="fav">${isFav ? '⭐️' : '☆'}</button>
      </div>
    </div>
    ${subsHtml}
    ${isSkip ? '<div class="pair-skipped-label">⏭ Пропущена</div>' : ''}
  </div>`;
}

function attachPairHandlers() {
  document.querySelectorAll(".pair-card").forEach(card => {
    const key = card.dataset.key;
    card.querySelectorAll("[data-act]").forEach(btn => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        const act = btn.dataset.act;
        if (act === "fav") {
          const i = settings.favorites.indexOf(key);
          if (i >= 0) settings.favorites.splice(i, 1); else settings.favorites.push(key);
        } else if (act === "skip") {
          const i = settings.skipped.indexOf(key);
          if (i >= 0) settings.skipped.splice(i, 1); else settings.skipped.push(key);
        }
        saveSettings();
        renderToday();
      });
    });
  });
}

function renderWeek() {
  const week = getCurrentWeek();
  const search = (document.getElementById("search-input").value || "").toLowerCase();
  const list = document.getElementById("week-list");

  const days = ["ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА"];
  let html = "";
  for (const day of days) {
    let dp = lessons.filter(l => l.week === week && l.day === day);
    if (settings.role === "teacher") {
      dp = dp.filter(l => l.subgroups.some(s => s.teacher && s.teacher.toLowerCase().includes(settings.teacherName.toLowerCase())));
    } else {
      dp = dp.filter(l => l.group === settings.group);
    }
    dp.sort((a, b) => a.number - b.number);

    if (search) {
      dp = dp.filter(p => p.subgroups.some(s =>
        (s.subject || "").toLowerCase().includes(search) ||
        (s.teacher || "").toLowerCase().includes(search) ||
        (s.room || "").toLowerCase().includes(search)));
    }

    const open = dp.length > 0;
    html += `<button class="day-header-btn" data-day="${day}">
      <span class="chev">${open ? "▼" : "▶"}</span>
      <span>${day}</span>
      <span class="count">${dp.length}</span>
    </button>`;
    if (open) {
      html += dp.map(p => pairCardHtml(p)).join("");
    }
  }
  list.innerHTML = html;
  attachPairHandlers();
}

function escapeHtml(s) {
  return String(s || "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// ═══════════════════════════════════════════════════════════
//  РЕНДЕР НАСТРОЕК
// ═══════════════════════════════════════════════════════════

function renderSettings() {
  document.getElementById("settings-group-value").textContent =
    settings.role === "teacher" ? (settings.teacherName || "—") : (settings.group || "не выбрана");
  document.getElementById("settings-morning-value").textContent =
    `${String(settings.morningHour).padStart(2, "0")}:${String(settings.morningMinute).padStart(2, "0")}`;
  document.getElementById("settings-before-value").textContent = `${settings.beforeMinutes} мин`;
  document.getElementById("settings-theme-value").textContent =
    settings.theme === "LIGHT" ? "Светлая" : settings.theme === "DARK" ? "Тёмная" : "Авто";
  document.getElementById("settings-last-value").textContent = formatLastUpdate(settings.lastUpdate);
}

function formatLastUpdate(ts) {
  if (!ts) return "никогда";
  const diff = Date.now() - ts;
  const min = Math.floor(diff / 60000);
  const hr = Math.floor(diff / 3600000);
  const day = Math.floor(diff / 86400000);
  if (min < 1) return "только что";
  if (min < 60) return `${min} мин назад`;
  if (hr < 24) return `${hr} ч назад`;
  if (day < 7) return `${day} дн назад`;
  return new Date(ts).toLocaleDateString("ru-RU", { day: "numeric", month: "long" });
}

// ═══════════════════════════════════════════════════════════
//  ЗАГРУЗКА РАСПИСАНИЯ
// ═══════════════════════════════════════════════════════════

async function downloadFromSite(statusEl) {
  if (statusEl) statusEl.textContent = "Скачиваем…";
  try {
    const res = await fetch(SCHEDULE_URL, { cache: "no-store" });
    if (!res.ok) throw new Error("HTTP " + res.status);
    const buf = await res.arrayBuffer();
    const parsed = parseXlsx(buf);
    if (parsed.length === 0) throw new Error("Пустой файл");
    saveSchedule(parsed);
    settings.lastUpdate = Date.now();
    saveSettings();
    if (statusEl) statusEl.textContent = `Загружено пар: ${parsed.length}`;
    return true;
  } catch (e) {
    if (statusEl) statusEl.textContent = "Ошибка: " + (e.message || "сеть");
    return false;
  }
}

// ═══════════════════════════════════════════════════════════
//  НАВИГАЦИЯ ВКЛАДОК
// ═══════════════════════════════════════════════════════════

function showTab(name) {
  document.querySelectorAll(".tab-content").forEach(el => el.classList.add("hidden"));
  document.getElementById("tab-" + name).classList.remove("hidden");
  document.querySelectorAll(".nav-btn").forEach(b => b.classList.toggle("active", b.dataset.tab === name));

  if (name === "today") renderToday();
  if (name === "week") renderWeek();
  if (name === "settings") renderSettings();
}

// ═══════════════════════════════════════════════════════════
//  ОНБОРДИНГ
// ═══════════════════════════════════════════════════════════

function setupOnboarding() {
  let role = null;
  let selectedGroup = null;
  let parsedGroups = [];
  let selectedTeacherName = "";
  let downloadOk = false;

  document.querySelectorAll(".role-card").forEach(card => {
    card.addEventListener("click", () => {
      document.querySelectorAll(".role-card").forEach(c => c.classList.remove("selected"));
      card.classList.add("selected");
      role = card.dataset.role;

      document.getElementById("onboarding-student").classList.toggle("hidden", role !== "student");
      document.getElementById("onboarding-teacher").classList.toggle("hidden", role !== "teacher");

      if (!downloadOk && lessons.length === 0) {
        autoDownload();
      }
      updateStartBtn();
    });
  });

  function updateStartBtn() {
    const btn = document.getElementById("btn-start");
    if (role === "student") btn.disabled = !(selectedGroup && parsedGroups.length > 0);
    else if (role === "teacher") btn.disabled = !(selectedTeacherName.length > 0 && lessons.length > 0);
    else btn.disabled = true;
  }

  async function autoDownload() {
    const studentStatus = document.getElementById("onboard-status");
    const teacherStatus = document.getElementById("onboard-status-teacher");
    const ok = await downloadFromSite(studentStatus);
    if (teacherStatus) teacherStatus.textContent = studentStatus.textContent;
    if (ok) {
      parsedGroups = [...new Set(lessons.map(l => l.group))].sort();
      renderGroups();
      downloadOk = true;
    }
    updateStartBtn();
  }

  function renderGroups() {
    const cont = document.getElementById("onboard-groups");
    cont.innerHTML = parsedGroups.map(g =>
      `<div class="group-item" data-group="${escapeHtml(g)}">
        <div class="group-check"></div>
        <div class="group-name">${escapeHtml(g)}</div>
      </div>`).join("");
    cont.querySelectorAll(".group-item").forEach(el => {
      el.addEventListener("click", () => {
        cont.querySelectorAll(".group-item").forEach(x => x.classList.remove("selected"));
        el.classList.add("selected");
        selectedGroup = el.dataset.group;
        updateStartBtn();
      });
    });
  }

  document.getElementById("btn-download-site").addEventListener("click", autoDownload);
  document.getElementById("btn-download-site-teacher").addEventListener("click", autoDownload);

  document.getElementById("file-input").addEventListener("change", async (e) => {
    const f = e.target.files[0];
    if (!f) return;
    const statusEl = role === "teacher"
      ? document.getElementById("onboard-status-teacher")
      : document.getElementById("onboard-status");
    statusEl.textContent = "Обрабатываем…";
    try {
      const buf = await f.arrayBuffer();
      const parsed = parseXlsx(buf);
      saveSchedule(parsed);
      settings.lastUpdate = Date.now();
      saveSettings();
      parsedGroups = [...new Set(parsed.map(l => l.group))].sort();
      renderGroups();
      downloadOk = true;
      statusEl.textContent = `Загружено пар: ${parsed.length}`;
    } catch (err) {
      statusEl.textContent = "Ошибка: " + err.message;
    }
    updateStartBtn();
  });

  document.getElementById("teacher-name-input").addEventListener("input", (e) => {
    selectedTeacherName = e.target.value.trim();
    updateStartBtn();
  });

  document.getElementById("btn-start").addEventListener("click", () => {
    if (role === "student") {
      settings.role = "student";
      settings.group = selectedGroup;
    } else {
      settings.role = "teacher";
      settings.teacherName = selectedTeacherName;
    }
    saveSettings();
    startApp();
  });
}

// ═══════════════════════════════════════════════════════════
//  СТАРТ ПРИЛОЖЕНИЯ
// ═══════════════════════════════════════════════════════════

function startApp() {
  document.getElementById("onboarding-screen").classList.add("hidden");
  document.getElementById("loading-screen").classList.add("hidden");
  document.getElementById("app").classList.remove("hidden");

  applyTheme();
  renderHeader();
  renderPairStatus();
  renderToday();

  setInterval(() => {
    renderHeader();
    renderPairStatus();
  }, 30000);

  document.querySelectorAll(".nav-btn").forEach(b => {
    b.addEventListener("click", () => showTab(b.dataset.tab));
  });

  document.getElementById("btn-refresh").addEventListener("click", async () => {
    const ok = await downloadFromSite(null);
    if (ok) { renderToday(); renderHeader(); }
    else alert("Не удалось скачать расписание");
  });

  document.getElementById("btn-upload").addEventListener("click", () => {
    document.getElementById("file-input-main").click();
  });

  document.getElementById("file-input-main").addEventListener("change", async (e) => {
    const f = e.target.files[0];
    if (!f) return;
    try {
      const buf = await f.arrayBuffer();
      const parsed = parseXlsx(buf);
      saveSchedule(parsed);
      settings.lastUpdate = Date.now();
      saveSettings();
      renderToday(); renderHeader();
      alert(`Загружено пар: ${parsed.length}`);
    } catch (err) { alert("Ошибка: " + err.message); }
  });

  document.getElementById("settings-theme").addEventListener("click", () => {
    settings.theme = settings.theme === "AUTO" ? "LIGHT"
      : settings.theme === "LIGHT" ? "DARK" : "AUTO";
    saveSettings(); applyTheme(); renderSettings();
  });

  document.getElementById("settings-before").addEventListener("click", () => {
    const arr = [5, 10, 15, 20, 30];
    const i = arr.indexOf(settings.beforeMinutes);
    settings.beforeMinutes = arr[(i + 1) % arr.length];
    saveSettings(); renderSettings();
  });

  document.getElementById("settings-morning").addEventListener("click", () => {
    settings.morningHour = (settings.morningHour + 1) % 24;
    saveSettings(); renderSettings();
  });

  document.getElementById("btn-update-now").addEventListener("click", async () => {
    const ok = await downloadFromSite(null);
    renderSettings(); renderToday(); renderHeader();
    if (!ok) alert("Не удалось скачать");
  });

  document.getElementById("settings-apk").addEventListener("click", () => window.open(APK_URL, "_blank"));

  const allSettings = document.querySelectorAll(".setting-row");
  if (allSettings.length > 0) {
    allSettings[allSettings.length - 1].addEventListener("click", () => window.open(CHANNEL_URL, "_blank"));
  }

  document.getElementById("search-input").addEventListener("input", renderWeek);
}

// ═══════════════════════════════════════════════════════════
//  ИНИЦИАЛИЗАЦИЯ
// ═══════════════════════════════════════════════════════════

window.addEventListener("DOMContentLoaded", () => {
  loadFromStorage();
  applyTheme();

  setTimeout(() => {
    document.getElementById("loading-screen").classList.add("hidden");

    if (settings.role) {
      startApp();
    } else {
      document.getElementById("onboarding-screen").classList.remove("hidden");
      setupOnboarding();
    }
  }, 400);

  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }
});
