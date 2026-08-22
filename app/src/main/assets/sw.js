self.addEventListener('install', event => self.skipWaiting());
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()));
self.addEventListener('message', event => {
  const data = event.data || {};
  if (data.type !== 'SHOW_NOTIFICATION' || !self.registration.showNotification) return;
  event.waitUntil(self.registration.showNotification(data.title || 'MaoozOS', {
    body: data.message || 'MaoozOS reminder',
    tag: data.tag || 'maoozos-alert',
    icon: data.icon || './favicon.png',
    data: { url: data.url || './index.html' }
  }));
});
self.addEventListener('notificationclick', event => {
  event.notification.close();
  const url = event.notification?.data?.url || './index.html';
  event.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then(list => {
    for (const client of list) {
      if ('focus' in client) return client.focus();
    }
    return clients.openWindow ? clients.openWindow(url) : undefined;
  }));
});
