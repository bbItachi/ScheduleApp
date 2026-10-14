// ═══════════════════════════════════════════════════════════
//  SERVICE WORKER — офлайн-режим
// ═══════════════════════════════════════════════════════════

const CACHE_NAME = "spk-schedule-v2";
const ASSETS = [
  "./",
  "./index.html",
  "./style.css",
  "./app.js",
  "./manifest.json",
  "./schedule.xlsx",
  "./lib/xlsx.full.min.js",
  "./icon-192.png",
  "./icon-512.png"
];

// Установка — кэшируем основные файлы
self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(ASSETS).catch(() => {}))
  );
  self.skipWaiting();
});

// Активация — удаляем старые кэши
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// Запросы — отдаём из кэша, если нет сети
self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);

  // Запросы к внешним доменам — только из сети
  if (url.origin !== self.location.origin) return;

  event.respondWith(
    fetch(event.request)
      .then(response => {
        if (event.request.method === "GET" && response.ok) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        }
        return response;
      })
      .catch(() => caches.match(event.request).then(c => c || caches.match("./index.html")))
  );
});
