const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  loadEvents: () => ipcRenderer.invoke('events:load'),
  saveEvents: (events) => ipcRenderer.invoke('events:save', events),
  loadCategories: () => ipcRenderer.invoke('categories:load'),
  saveCategories: (categories) => ipcRenderer.invoke('categories:save', categories),
  notify: (title, body) => ipcRenderer.invoke('notify:show', { title, body }),

  authStatus: () => ipcRenderer.invoke('auth:status'),
  authRegister: (email, password, displayName) =>
    ipcRenderer.invoke('auth:register', { email, password, displayName }),
  authLogin: (email, password) => ipcRenderer.invoke('auth:login', { email, password }),
  authLogout: () => ipcRenderer.invoke('auth:logout'),
  authDiscordLogin: () => ipcRenderer.invoke('auth:discordLogin'),
  authSetServerUrl: (url) => ipcRenderer.invoke('auth:setServerUrl', url),
  onAuthChanged: (callback) => {
    const listener = (_event, user) => callback(user);
    ipcRenderer.on('auth:changed', listener);
    return () => ipcRenderer.removeListener('auth:changed', listener);
  },

  syncPull: () => ipcRenderer.invoke('sync:pull'),
  syncPush: (events, categories) => ipcRenderer.invoke('sync:push', { events, categories }),

  pinStatus: () => ipcRenderer.invoke('pin:status'),
  pinSet: (pin) => ipcRenderer.invoke('pin:set', pin),
  pinClear: () => ipcRenderer.invoke('pin:clear'),
  pinVerify: (pin) => ipcRenderer.invoke('pin:verify', pin),
});
