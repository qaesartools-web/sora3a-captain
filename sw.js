// سرعة — الكابتن: Service Worker (تخزين مؤقت + إشعارات الخلفية)
const CACHE_VERSION = 'captain-v11';
const CACHE_NAME = `app-cache-${CACHE_VERSION}`;
const SCOPE = '/sora3a-captain/';
const PRECACHE = [SCOPE, SCOPE + 'captain.html', SCOPE + 'manifest.json', SCOPE + 'icon-192.png'];

// ── إشعارات Firebase بالخلفية (التطبيق مغلق) ──
importScripts('https://www.gstatic.com/firebasejs/10.7.1/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.7.1/firebase-messaging-compat.js');
firebase.initializeApp({
  apiKey: 'AIzaSyAwlFrbv-c6G0_K0-s0P1m1o_qD95aGGyQ',
  authDomain: 'sora3a-system.firebaseapp.com',
  projectId: 'sora3a-system',
  storageBucket: 'sora3a-system.firebasestorage.app',
  messagingSenderId: '516304449260',
  appId: '1:516304449260:web:e0d2e20d8964e4d6656733',
});
const messaging = firebase.messaging();
// رسائل بدون notification (data فقط) نعرضها بأنفسنا؛ الرسائل التي فيها notification يعرضها Firebase تلقائياً
messaging.onBackgroundMessage((payload) => {
  if (payload.notification) return;
  const d = payload.data || {};
  if (d.kind === 'order') return; // طلبات التوصيل يتولاها مستمع push تحت (يرن ويكرر)
  return self.registration.showNotification(d.title || '🛵 طلب توصيل جديد', {
    body: d.body || '', tag: d.tag || 'order', renotify: true, requireInteraction: true,
    icon: SCOPE + 'icon-192.png', badge: SCOPE + 'icon-192.png', vibrate: [300, 100, 300, 100, 400],
    dir: 'rtl', lang: 'ar', data: { url: d.link || SCOPE + 'captain.html' },
  });
});

// ── طلب توصيل جديد والتطبيق مسكّر: إشعار يرن ويتكرر كل 5 ثواني (حوالي نص دقيقة)
//    لحد ما الكابتن يضغط عليه أو يسحبه أو يفتح التطبيق (التطبيق نفسه يرن بالنغمة الطويلة)
const RING_REPEAT = 6, RING_GAP_MS = 5000;
self.addEventListener('push', (event) => {
  let p = null;
  try { p = event.data && event.data.json(); } catch (e) {}
  const d = (p && p.data) || {};
  if (d.kind !== 'order') return;
  event.waitUntil(ringOrder(d));
});
async function appVisible() {
  const list = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  return list.some((c) => c.url.includes(SCOPE) && c.visibilityState === 'visible');
}
async function ringOrder(d) {
  const tag = d.tag || 'order';
  const opts = {
    body: d.body || '', tag, renotify: true, requireInteraction: true, silent: false,
    icon: SCOPE + 'icon-192.png', badge: SCOPE + 'icon-192.png', vibrate: [600, 200, 600, 200, 900],
    dir: 'rtl', lang: 'ar', data: { url: d.link || SCOPE + 'captain.html', orderId: d.orderId || '' },
  };
  if (await appVisible()) return; // التطبيق مفتوح قدامه — هو يرن بنفسه
  for (let i = 0; i < RING_REPEAT; i++) {
    if (i) {
      await new Promise((r) => setTimeout(r, RING_GAP_MS));
      const still = await self.registration.getNotifications({ tag });
      if (!still.length || await appVisible()) return; // ضغطه أو سحبه أو فتح التطبيق
    }
    await self.registration.showNotification((i ? '🔔 ' : '') + (d.title || '🛵 طلب توصيل جديد'), opts);
  }
}

// الضغط على الإشعار يفتح التطبيق (أو يركّز عليه إن كان مفتوحاً)
self.addEventListener('notificationclick', (event) => {
  const data = event.notification.data || {};
  if (data.FCM_MSG) return; // يتولاه Firebase
  event.notification.close();
  const url = data.url || SCOPE + 'captain.html';
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
    for (const c of list) { if (c.url.includes(SCOPE) && 'focus' in c) return c.focus(); }
    return self.clients.openWindow(url);
  }));
});

// ── التخزين المؤقت ──
const RUNTIME_CACHE_PATTERNS = [
  /^https:\/\/www\.gstatic\.com\/firebasejs/,
  /^https:\/\/fonts\.(googleapis|gstatic)\.com/,
  /\.(?:png|jpg|jpeg|svg|woff2|woff|ttf)$/,
];
self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE_NAME).then((c) => c.addAll(PRECACHE).catch(() => {})).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (/firebaseio\.com|firebasedatabase\.app|firestore\.googleapis|identitytoolkit|securetoken|fcmregistrations|tile\.openstreetmap/.test(url.hostname)) return;
  // الصفحات: الشبكة أولاً ثم النسخة المخزنة
  if (req.mode === 'navigate') {
    event.respondWith(fetch(new Request(req.url, { cache: 'no-cache', credentials: 'same-origin' })).then((r) => {
      if (r.redirected) return Response.redirect(r.url, 302);
      const c = r.clone(); caches.open(CACHE_NAME).then((cc) => cc.put(req, c)).catch(() => {});
      return r;
    }).catch(() => caches.match(req).then((r) => r || caches.match(SCOPE + 'captain.html'))));
    return;
  }
  if (RUNTIME_CACHE_PATTERNS.some((p) => p.test(url.href))) {
    event.respondWith(caches.match(req).then((cached) => cached || fetch(req).then((r) => {
      if (r.ok) { const c = r.clone(); caches.open(CACHE_NAME).then((cc) => cc.put(req, c)).catch(() => {}); }
      return r;
    })));
  }
});
