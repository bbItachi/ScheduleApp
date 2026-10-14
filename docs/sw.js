// ═══════════════════════════════════════════════════════════
//  SERVICE WORKER — офлайн-режим
// ═══════════════════════════════════════════════════════════

const CACHE_NAME = "spk-schedule-v1";
const ASSETS = [
  "./",
  "./index.html",
  "./style.css",
  "./app.js",
  "./manifest.json",
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

  // SheetJS и расписание с сайта — только из сети
  if (url.hostname.includes("cdn.sheetjs.com") || url.hostname.includes("xn--")) {
    return;
  }

  event.respondWith(
    caches.match(event.request).then(cached => {
      if (cached) return cached;
      return fetch(event.request).then(response => {
        // Кэшируем только успешные GET-запросы своих файлов
        if (event.request.method === "GET" && response.ok && url.origin === self.location.origin) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        }
        return response;
      }).catch(() => caches.match("./index.html"));
    })
  );
});
