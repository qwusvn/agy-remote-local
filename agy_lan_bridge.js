/**
 * AGY LAN Bridge v6.1 (Ultra-Fast Non-Blocking Architecture)
 * - proxy_handler: HTTP Proxy & TCP Tunnel với HTTP Keep-Alive & Trailer support
 * - ws_hub: WebSocket Hub RFC 6455
 * - session_watcher: Giám sát thư mục brain bất đồng bộ (non-blocking)
 */

const http = require('http');
const os = require('os');
const { exec } = require('child_process');

const proxyHandler = require('./bridge/proxy_handler');
const wsHub = require('./bridge/ws_hub');
const sessionWatcher = require('./bridge/session_watcher');

const LISTEN_PORT = 4400;
let currentTargetPort = 0;
let currentCsrfToken = '';
let isDetecting = false;
let lastSyncSuccess = 0;

function getLanIp() {
    const interfaces = os.networkInterfaces();
    let fallbackIp = '192.168.1.220';
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                if (iface.address.startsWith('192.168.')) {
                    return iface.address;
                }
                if (!iface.address.startsWith('169.254.')) {
                    fallbackIp = iface.address;
                }
            }
        }
    }
    return fallbackIp;
}

function detectPcLanguageServerAsync() {
    return new Promise((resolve) => {
        const psCmd = `powershell -NoProfile -Command "$p = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'language_server.exe' -and $_.CommandLine -like '*--subclient_type*hub*' } | Select-Object -First 1; if ($p) { $ports = (Get-NetTCPConnection -OwningProcess $p.ProcessId -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalPort) -join ','; Write-Output ($p.ProcessId.ToString() + '|' + $p.CommandLine + '|' + $ports) }"`;
        exec(psCmd, { encoding: 'utf8', timeout: 5000 }, (err, stdout) => {
            if (err || !stdout) return resolve(null);
            const out = stdout.trim();
            const [pidStr, cmdLine, portsStr] = out.split('|');
            if (!pidStr || !portsStr) return resolve(null);

            const csrfMatch = cmdLine.match(/--csrf_token\s+([a-f0-9\-]+)/);
            const csrfToken = csrfMatch ? csrfMatch[1] : '';
            const ports = portsStr.split(',').map(p => parseInt(p.trim())).filter(p => !isNaN(p));
            resolve({
                pid: parseInt(pidStr),
                csrfToken: csrfToken,
                candidatePorts: ports
            });
        });
    });
}

function checkPortAlive(port, csrfToken) {
    return new Promise((resolve) => {
        const req = http.get({
            hostname: '127.0.0.1',
            port: port,
            path: '/',
            headers: csrfToken ? { 'x-codeium-csrf-token': csrfToken } : {},
            timeout: 1000
        }, (res) => {
            resolve(res.statusCode === 200 || res.statusCode === 302 || res.statusCode === 404);
        });
        req.on('error', () => resolve(false));
        req.on('timeout', () => { req.destroy(); resolve(false); });
    });
}

async function findHttpPort(serverInfo) {
    if (!serverInfo || !serverInfo.candidatePorts.length) return null;
    for (const port of serverInfo.candidatePorts) {
        const ok = await checkPortAlive(port, serverInfo.csrfToken);
        if (ok) return port;
    }
    return null;
}

async function syncTargetServer(force = false) {
    if (isDetecting) return;

    // Fast-path: Nếu cổng hiện tại đang chạy tốt và không force, không chạy PowerShell
    if (!force && currentTargetPort && (Date.now() - lastSyncSuccess < 30000)) {
        const alive = await checkPortAlive(currentTargetPort, currentCsrfToken);
        if (alive) {
            lastSyncSuccess = Date.now();
            return;
        }
    }

    isDetecting = true;
    try {
        const info = await detectPcLanguageServerAsync();
        if (info) {
            const httpPort = await findHttpPort(info);
            if (httpPort) {
                if (currentTargetPort !== httpPort || currentCsrfToken !== info.csrfToken) {
                    currentTargetPort = httpPort;
                    currentCsrfToken = info.csrfToken;
                    proxyHandler.setTarget('127.0.0.1', httpPort, info.csrfToken);
                    console.log(`[SYNC PC] ✅ Kết nối Language Server PC (PID: ${info.pid}, Port: ${httpPort})`);
                }
                lastSyncSuccess = Date.now();
                return;
            }
        }
    } catch (e) {
        console.error('[SYNC PC] Lỗi quét PC Language Server:', e.message);
    } finally {
        isDetecting = false;
    }
}

// Bắt lỗi kết nối từ Proxy để kích hoạt dò lại port ngay lập tức (không đợi timer)
proxyHandler.setOnConnectionError(() => {
    syncTargetServer(true);
});

// Khởi chạy đồng bộ PC Server ban đầu (bất đồng bộ hoàn toàn)
syncTargetServer(true);
// Health-check nhẹ 10 giây/lần bằng HTTP GET (0ms block, không spawn process)
setInterval(() => syncTargetServer(false), 10000);

// Khởi tạo HTTP Server
const server = http.createServer((req, res) => {
    proxyHandler.handleHttpRequest(req, res);
});

// Xử lý Upgrade (WebSocket Hub hoặc Tunnel)
server.on('upgrade', (req, socket, head) => {
    if (req.url === '/connect-websocket' || req.url.startsWith('/connect-websocket')) {
        wsHub.handleUpgrade(req, socket, head);
    } else {
        proxyHandler.handleTunnelUpgrade(req, socket, head);
    }
});

server.listen(LISTEN_PORT, '0.0.0.0', () => {
    const lanIp = getLanIp();
    console.log(`=======================================================`);
    console.log(`🚀 AGY LAN BRIDGE v6.1 (Ultra-Fast Non-Blocking)`);
    console.log(`📱 LAN Web UI:     http://${lanIp}:${LISTEN_PORT}`);
    console.log(`⚡ WebSocket Hub:  ws://${lanIp}:${LISTEN_PORT}/connect-websocket`);
    console.log(`=======================================================`);

    // Khởi động Session Watcher
    sessionWatcher.start(LISTEN_PORT);
});
