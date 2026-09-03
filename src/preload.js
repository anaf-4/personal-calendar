const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  loadEvents: () => ipcRenderer.invoke('events:load'),
  saveEvents: (events) => ipcRenderer.invoke('events:save', events),
  loadCategories: () => ipcRenderer.invoke('categories:load'),
  saveCategories: (categories) => ipcRenderer.invoke('categories:save', categories),
  notify: (title, body) => ipcRenderer.invoke('notify:show', { title, body }),
});
