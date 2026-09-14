// ONewPipe desktop shell.
//
// The interface is the web UI served by the ONewPipe server (module `server`),
// which also runs the NewPipe extraction core. This shell starts that server on
// a free loopback port, waits until it answers, and shows the UI in a window.
// Video playback uses Chromium's own player, so the native-surface issues of
// the former Compose/VLC desktop app do not apply here.

const { app, BrowserWindow, dialog, shell } = require('electron');
const { spawn } = require('child_process');
const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const net = require('net');
const path = require('path');

const SERVER_JAR = 'onewpipe-server-all.jar';
const HEALTH_TIMEOUT_MS = 60_000;

let serverProcess = null;
let mainWindow = null;

function resourcePath(...parts) {
  return app.isPackaged
    ? path.join(process.resourcesPath, ...parts)
    : path.join(__dirname, ...parts);
}

function serverJarPath() {
  if (app.isPackaged) return path.join(process.resourcesPath, 'server', SERVER_JAR);
  return path.join(__dirname, '..', 'server', 'build', 'libs', SERVER_JAR);
}

/** Bundled Java runtime first, then JAVA_HOME, then whatever `java` is on PATH. */
function javaExecutable() {
  const binary = process.platform === 'win32' ? 'java.exe' : 'java';
  const candidates = [
    resourcePath('runtime', 'bin', binary),
    process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', binary) : null
  ].filter(Boolean);
  return candidates.find((candidate) => fs.existsSync(candidate)) || 'java';
}

function freePort() {
  return new Promise((resolve, reject) => {
    const probe = net.createServer();
    probe.unref();
    probe.on('error', reject);
    probe.listen(0, '127.0.0.1', () => {
      const { port } = probe.address();
      probe.close(() => resolve(port));
    });
  });
}

/** A per-installation secret, so sessions survive restarts without a shared default. */
function jwtSecret(dataDir) {
  const file = path.join(dataDir, 'jwt-secret');
  if (fs.existsSync(file)) return fs.readFileSync(file, 'utf8').trim();
  const secret = crypto.randomBytes(48).toString('hex');
  fs.writeFileSync(file, secret, { mode: 0o600 });
  return secret;
}

function waitForHealth(port, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const attempt = () => {
      if (serverProcess && serverProcess.exitCode !== null) {
        reject(new Error(`The server stopped during start-up (exit code ${serverProcess.exitCode}).`));
        return;
      }
      const request = http.get({ host: '127.0.0.1', port, path: '/health', timeout: 2_000 }, (response) => {
        response.resume();
        if (response.statusCode === 200) resolve();
        else retry();
      });
      request.on('error', retry);
      request.on('timeout', () => { request.destroy(); retry(); });
    };
    const retry = () => {
      if (Date.now() > deadline) reject(new Error('The server did not answer in time.'));
      else setTimeout(attempt, 250);
    };
    attempt();
  });
}

async function startServer() {
  const dataDir = path.join(app.getPath('userData'), 'server-data');
  const logDir = path.join(app.getPath('userData'), 'logs');
  fs.mkdirSync(dataDir, { recursive: true });
  fs.mkdirSync(logDir, { recursive: true });
  const logFile = path.join(logDir, 'server.log');
  const log = fs.createWriteStream(logFile, { flags: 'w' });

  const jar = serverJarPath();
  if (!fs.existsSync(jar)) {
    throw new Error(`Server not found at ${jar}. Build it with: gradlew :server:fatJar`);
  }

  const port = await freePort();
  serverProcess = spawn(javaExecutable(), ['-jar', jar], {
    env: {
      ...process.env,
      PORT: String(port),
      HOST: '127.0.0.1',
      DATA_DIR: dataDir,
      JWT_SECRET: jwtSecret(dataDir)
    },
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true
  });
  serverProcess.stdout.pipe(log);
  serverProcess.stderr.pipe(log);
  serverProcess.on('error', (error) => log.write(`\nFailed to start Java: ${error.message}\n`));

  try {
    await waitForHealth(port, HEALTH_TIMEOUT_MS);
  } catch (error) {
    error.message += `\n\nServer log: ${logFile}`;
    throw error;
  }
  return `http://127.0.0.1:${port}/`;
}

function stopServer() {
  if (serverProcess && serverProcess.exitCode === null) serverProcess.kill();
  serverProcess = null;
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 480,
    minHeight: 360,
    title: 'ONewPipe',
    backgroundColor: '#0f0f0f',
    icon: resourcePath('build', process.platform === 'win32' ? 'icon.ico' : 'icon.png'),
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      contextIsolation: true,
      sandbox: true
    }
  });
  mainWindow.once('ready-to-show', () => mainWindow.show());

  // Links to other sites (channel pages, "open in browser") open in the
  // user's browser instead of replacing the app.
  const isAppUrl = (url) => url.startsWith('http://127.0.0.1:');
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (!isAppUrl(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (!isAppUrl(url) && !url.startsWith('file:')) {
      event.preventDefault();
      shell.openExternal(url);
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'loading.html'));
  return mainWindow;
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
  });

  app.whenReady().then(async () => {
    createWindow();
    try {
      const url = await startServer();
      await mainWindow.loadURL(url);
    } catch (error) {
      dialog.showErrorBox('ONewPipe could not start', error.message);
      app.quit();
    }
  });

  app.on('window-all-closed', () => app.quit());
  app.on('before-quit', stopServer);
  process.on('exit', stopServer);
}
