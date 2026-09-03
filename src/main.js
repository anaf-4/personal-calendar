const { app, BrowserWindow, ipcMain, Notification, Tray, Menu, dialog, nativeImage, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const { autoUpdater } = require('electron-updater');
const crypto = require('crypto');

const EVENTS_FILE = path.join(app.getPath('userData'), 'events.json');
const CATEGORIES_FILE = path.join(app.getPath('userData'), 'categories.json');
const SETTINGS_FILE = path.join(app.getPath('userData'), 'settings.json');
const AUTH_FILE = path.join(app.getPath('userData'), 'auth.json');
const ICON_PATH = path.join(__dirname, '..', 'assets', 'icon.png');
const PROTOCOL_SCHEME = 'personalcalendar';
// Tried in order: LAN address first (faster, works at home), then the port-forwarded
// public address (works away from home). Whichever answers /health first is cached
// and reused for the rest of the session.
const SERVER_CANDIDATES = ['http://192.168.45.250:4000', 'http://211.208.252.113:4000'];
const DEFAULT_SERVER_URL = SERVER_CANDIDATES[0];

const DEFAULT_CATEGORIES = [
  { id: 'work', name: '업무', color: '#5b8def' },
  { id: 'personal', name: '개인', color: '#3ea36f' },
  { id: 'important', name: '중요', color: '#e0596b' },
  { id: 'etc', name: '기타', color: '#9b6ce0' },
];

const DEFAULT_SETTINGS = { closeBehavior: null, serverUrl: DEFAULT_SERVER_URL, pinHash: null }; // closeBehavior: null = ask every time, 'tray' | 'quit'
const DEFAULT_AUTH = { token: null, user: null };

function readJson(file, fallback) {
  try {
    const raw = fs.readFileSync(file, 'utf-8');
    return JSON.parse(raw);
  } catch (err) {
    return fallback;
  }
}

function writeJson(file, data) {
  fs.writeFileSync(file, JSON.stringify(data, null, 2), 'utf-8');
}

let mainWindow = null;
let tray = null;
let isQuitting = false;
let settings = { ...DEFAULT_SETTINGS, ...readJson(SETTINGS_FILE, DEFAULT_SETTINGS) };
// The server address is fixed by the app operator, not user-editable — always use the
// current constant rather than a value persisted from an older build.
settings.serverUrl = DEFAULT_SERVER_URL;
let auth = readJson(AUTH_FILE, DEFAULT_AUTH);

function saveSettings() {
  writeJson(SETTINGS_FILE, settings);
}

function saveAuth() {
  writeJson(AUTH_FILE, auth);
}

let resolvedServerUrl = null;

async function resolveServerUrl() {
  if (resolvedServerUrl) return resolvedServerUrl;
  for (const base of SERVER_CANDIDATES) {
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 2500);
      const resp = await fetch(`${base}/health`, { signal: controller.signal });
      clearTimeout(timer);
      if (resp.ok) {
        resolvedServerUrl = base;
        return base;
      }
    } catch (err) {
      // unreachable on this address; try the next one
    }
  }
  // Nothing answered — fall back to the first candidate so error messages are still meaningful.
  return SERVER_CANDIDATES[0];
}

async function apiFetch(pathName, options = {}) {
  const base = await resolveServerUrl();
  const url = `${base}${pathName}`;
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`;
  const resp = await fetch(url, { ...options, headers });
  let body = null;
  try {
    body = await resp.json();
  } catch (err) {
    body = null;
  }
  return { ok: resp.ok, status: resp.status, body };
}

function hashPin(pin) {
  return crypto.createHash('sha256').update(`pc-pin-salt:${pin}`).digest('hex');
}

async function fetchAndStoreProfile() {
  const { ok, body } = await apiFetch('/api/auth/me');
  if (ok && body && body.user) {
    auth.user = body.user;
    saveAuth();
  }
  return ok;
}

function handleAuthCallbackUrl(url) {
  let parsed;
  try {
    parsed = new URL(url);
  } catch (err) {
    return;
  }
  const token = parsed.searchParams.get('token');
  const error = parsed.searchParams.get('error');

  if (token) {
    auth.token = token;
    saveAuth();
    fetchAndStoreProfile().then(() => {
      if (mainWindow) mainWindow.webContents.send('auth:changed', auth.user);
    });
  } else if (mainWindow) {
    dialog.showMessageBox(mainWindow, {
      type: 'error',
      message: '디스코드 로그인에 실패했습니다.',
      detail: error || '',
    });
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 780,
    minWidth: 860,
    minHeight: 620,
    backgroundColor: '#1e1f24',
    icon: ICON_PATH,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  mainWindow.setMenuBarVisibility(false);
  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));

  mainWindow.on('close', (event) => {
    if (isQuitting) return;
    event.preventDefault();
    handleCloseRequest();
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

async function handleCloseRequest() {
  if (!mainWindow) return;

  if (settings.closeBehavior === 'tray') {
    mainWindow.hide();
    return;
  }
  if (settings.closeBehavior === 'quit') {
    isQuitting = true;
    app.quit();
    return;
  }

  const result = await dialog.showMessageBox(mainWindow, {
    type: 'question',
    buttons: ['백그라운드에서 계속 실행', '완전히 종료', '취소'],
    defaultId: 0,
    cancelId: 2,
    title: '앱 종료',
    message: '앱을 어떻게 종료할까요?',
    detail: '백그라운드에서 실행하면 트레이 아이콘에서 계속 동작하며 알림을 받을 수 있습니다.',
    checkboxLabel: '다음에도 이 선택 사용',
    checkboxChecked: false,
    noLink: true,
  });

  if (result.response === 2 || !mainWindow) return;

  const choice = result.response === 0 ? 'tray' : 'quit';
  if (result.checkboxChecked) {
    settings.closeBehavior = choice;
    writeJson(SETTINGS_FILE, settings);
  }

  if (choice === 'tray') {
    mainWindow.hide();
  } else {
    isQuitting = true;
    app.quit();
  }
}

function createTray() {
  const trayIcon = nativeImage.createFromPath(ICON_PATH).resize({ width: 16, height: 16 });
  tray = new Tray(trayIcon);
  tray.setToolTip(`개인일정 v${app.getVersion()}`);

  const buildMenu = () =>
    Menu.buildFromTemplate([
      {
        label: `버전 ${app.getVersion()}`,
        enabled: false,
      },
      { type: 'separator' },
      {
        label: '열기',
        click: () => {
          if (!mainWindow) createWindow();
          mainWindow.show();
          mainWindow.focus();
        },
      },
      {
        label: '종료 방식 다시 묻기',
        click: () => {
          settings.closeBehavior = null;
          writeJson(SETTINGS_FILE, settings);
        },
      },
      {
        label: '업데이트 확인',
        click: () => checkForUpdates(true),
      },
      { type: 'separator' },
      {
        label: '완전히 종료',
        click: () => {
          isQuitting = true;
          app.quit();
        },
      },
    ]);

  tray.setContextMenu(buildMenu());
  tray.on('click', () => {
    if (!mainWindow) {
      createWindow();
      return;
    }
    if (mainWindow.isVisible()) {
      mainWindow.hide();
    } else {
      mainWindow.show();
      mainWindow.focus();
    }
  });
}

if (process.platform === 'win32' && app.isPackaged) {
  app.setAppUserModelId('com.local.personalcalendar');
}

if (process.defaultApp) {
  if (process.argv.length >= 2) {
    app.setAsDefaultProtocolClient(PROTOCOL_SCHEME, process.execPath, [path.resolve(process.argv[1])]);
  }
} else {
  app.setAsDefaultProtocolClient(PROTOCOL_SCHEME);
}

const gotSingleInstanceLock = app.requestSingleInstanceLock();
if (!gotSingleInstanceLock) {
  app.quit();
} else {
  app.on('second-instance', (_event, argv) => {
    const url = argv.find((a) => a.startsWith(`${PROTOCOL_SCHEME}://`));
    if (url) handleAuthCallbackUrl(url);
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.show();
      mainWindow.focus();
    }
  });
}

autoUpdater.autoDownload = true;
autoUpdater.autoInstallOnAppQuit = true;

let manualUpdateCheck = false;

function checkForUpdates(manual) {
  if (!app.isPackaged) {
    if (manual && mainWindow) {
      dialog.showMessageBox(mainWindow, {
        type: 'info',
        message: '개발 모드에서는 업데이트를 확인할 수 없습니다.',
      });
    }
    return;
  }
  manualUpdateCheck = manual;
  autoUpdater.checkForUpdates().catch(() => {});
}

autoUpdater.on('update-not-available', () => {
  if (manualUpdateCheck && mainWindow) {
    dialog.showMessageBox(mainWindow, {
      type: 'info',
      message: '현재 최신 버전을 사용 중입니다.',
    });
  }
  manualUpdateCheck = false;
});

autoUpdater.on('error', () => {
  manualUpdateCheck = false;
});

autoUpdater.on('update-downloaded', async (info) => {
  if (!mainWindow) return;
  const result = await dialog.showMessageBox(mainWindow, {
    type: 'info',
    buttons: ['지금 재시작', '나중에'],
    defaultId: 0,
    title: '업데이트 준비 완료',
    message: `새 버전(${info.version})이 다운로드되었습니다.`,
    detail: '지금 재시작하면 업데이트가 적용됩니다. 나중에를 선택하면 다음 종료 시 자동으로 적용됩니다.',
    noLink: true,
  });
  if (result.response === 0) {
    isQuitting = true;
    autoUpdater.quitAndInstall();
  }
});

app.whenReady().then(() => {
  createWindow();
  createTray();
  checkForUpdates(false);

  const initialUrl = process.argv.find((a) => a.startsWith(`${PROTOCOL_SCHEME}://`));
  if (initialUrl) handleAuthCallbackUrl(initialUrl);

  app.on('activate', () => {
    if (!mainWindow) createWindow();
    else mainWindow.show();
  });
});

app.on('before-quit', () => {
  isQuitting = true;
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

ipcMain.handle('events:load', () => {
  return readJson(EVENTS_FILE, []);
});

ipcMain.handle('events:save', (_event, events) => {
  writeJson(EVENTS_FILE, events);
  return true;
});

ipcMain.handle('categories:load', () => {
  return readJson(CATEGORIES_FILE, DEFAULT_CATEGORIES);
});

ipcMain.handle('categories:save', (_event, categories) => {
  writeJson(CATEGORIES_FILE, categories);
  return true;
});

// ---------- account / sync ----------

ipcMain.handle('auth:status', () => {
  return { loggedIn: Boolean(auth.token), user: auth.user, serverUrl: resolvedServerUrl || settings.serverUrl };
});

ipcMain.handle('auth:register', async (_event, { email, password, displayName }) => {
  const { ok, body } = await apiFetch('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password, displayName }),
  });
  if (!ok) return { ok: false, error: (body && body.error) || 'unknown_error' };
  auth = { token: body.token, user: body.user };
  saveAuth();
  return { ok: true, user: auth.user };
});

ipcMain.handle('auth:login', async (_event, { email, password }) => {
  const { ok, body } = await apiFetch('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
  if (!ok) return { ok: false, error: (body && body.error) || 'unknown_error' };
  auth = { token: body.token, user: body.user };
  saveAuth();
  return { ok: true, user: auth.user };
});

ipcMain.handle('auth:logout', () => {
  auth = { ...DEFAULT_AUTH };
  saveAuth();
  return true;
});

ipcMain.handle('auth:discordLogin', async () => {
  const base = await resolveServerUrl();
  shell.openExternal(`${base}/api/auth/discord`);
  return true;
});

ipcMain.handle('sync:pull', async () => {
  if (!auth.token) return { ok: false, error: 'not_logged_in' };
  const { ok, body } = await apiFetch('/sync');
  if (!ok) return { ok: false, error: (body && body.error) || 'sync_failed' };
  return { ok: true, events: body.events, categories: body.categories };
});

ipcMain.handle('sync:push', async (_event, { events, categories }) => {
  if (!auth.token) return { ok: false, error: 'not_logged_in' };
  const { ok, body } = await apiFetch('/sync', {
    method: 'PUT',
    body: JSON.stringify({ events, categories }),
  });
  if (!ok) return { ok: false, error: (body && body.error) || 'sync_failed' };
  return { ok: true };
});

// ---------- PIN lock ----------

ipcMain.handle('pin:status', () => {
  return { hasPin: Boolean(settings.pinHash) };
});

ipcMain.handle('pin:set', (_event, pin) => {
  settings.pinHash = hashPin(pin);
  saveSettings();
  return true;
});

ipcMain.handle('pin:clear', () => {
  settings.pinHash = null;
  saveSettings();
  return true;
});

ipcMain.handle('pin:verify', (_event, pin) => {
  return Boolean(settings.pinHash) && settings.pinHash === hashPin(pin);
});

ipcMain.handle('notify:show', (_event, { title, body }) => {
  try {
    if (!Notification.isSupported()) return { ok: false, reason: 'unsupported' };
    const notification = new Notification({ title, body, icon: ICON_PATH, silent: false });
    notification.on('click', () => {
      if (!mainWindow) return;
      if (mainWindow.isMinimized()) mainWindow.restore();
      if (!mainWindow.isVisible()) mainWindow.show();
      mainWindow.focus();
    });
    notification.show();
    return { ok: true };
  } catch (err) {
    return { ok: false, reason: String(err && err.message ? err.message : err) };
  }
});
