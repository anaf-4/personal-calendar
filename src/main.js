const { app, BrowserWindow, ipcMain, Notification, Tray, Menu, dialog, nativeImage } = require('electron');
const path = require('path');
const fs = require('fs');
const { autoUpdater } = require('electron-updater');

const EVENTS_FILE = path.join(app.getPath('userData'), 'events.json');
const CATEGORIES_FILE = path.join(app.getPath('userData'), 'categories.json');
const SETTINGS_FILE = path.join(app.getPath('userData'), 'settings.json');
const ICON_PATH = path.join(__dirname, '..', 'assets', 'icon.png');

const DEFAULT_CATEGORIES = [
  { id: 'work', name: '업무', color: '#5b8def' },
  { id: 'personal', name: '개인', color: '#3ea36f' },
  { id: 'important', name: '중요', color: '#e0596b' },
  { id: 'etc', name: '기타', color: '#9b6ce0' },
];

const DEFAULT_SETTINGS = { closeBehavior: null }; // null = ask every time, 'tray' | 'quit'

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
let settings = readJson(SETTINGS_FILE, DEFAULT_SETTINGS);

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
  tray.setToolTip('개인일정');

  const buildMenu = () =>
    Menu.buildFromTemplate([
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
