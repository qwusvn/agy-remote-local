/**
 * AGY LAN Bridge v6.0 (Modularized & Decoupled Architecture)
 * - proxy_handler: Quản lý HTTP Proxy & TCP Socket Tunnel
 * - ws_hub: Quản lý Realtime WebSocket Hub RFC 6455
 * - session_watcher: Quản lý quét thư mục brain và bắn sự kiện Agent hoàn thành
 */

const http = require('http');
const os = require('os');
const { execSync } = require('child_process');

const proxyHandler = require('./bridge/proxy_handler');
const wsHub = require('./bridge/ws_hub');
const sessionWatcher = require('./bridge/session_watcher');

const LISTEN_PORT = 4400;
let currentTargetPort = 51896;
let currentCsrfToken = '';

function getLanIp() {
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                return iface.address;
            }
        }
    }
    return '192.168.1.220';
}

function detectPcLanguageServer() {
    try {
        const psCmd = `powershell -NoProfile -Command "$p = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'language_server.exe' -and $_.CommandLine -like '*--subclient_type*hub*' } | Select-Object -First 1; if ($p) { $ports = (Get-NetTCPConnection -OwningProcess $p.ProcessId -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalPort) -join ','; Write-Output ($p.ProcessId.ToString() + '|' + $p.CommandLine + '|' + $ports) }"`;
        const out = execSync(psCmd, { encoding: 'utf8', timeout: 4000 }).trim();
        if (!out) return null;

        const [pidStr, cmdLine, portsStr] = out.split('|');
        if (!pidStr || !portsStr) return null;

        const csrfMatch = cmdLine.match(/--csrf_token\s+([a-f0-9\-]+)/);
        const csrfToken = csrfMatch ? csrfMatch[1] : '';

        const ports = portsStr.split(',').map(p => parseInt(p.trim())).filter(p => !isNaN(p));
        return {
            pid: parseInt(pidStr),
            csrfToken: csrfToken,
            candidatePorts: ports
        };
    } catch (e) {
        return null;
    }
}

async function findHttpPort(serverInfo) {
    if (!serverInfo || !serverInfo.candidatePorts.length) return null;
    for (const port of serverInfo.candidatePorts) {
        const ok = await new Promise((resolve) => {
            const req = http.get({
                hostname: '127.0.0.1',
                port: port,
                path: '/',
                headers: { 'x-codeium-csrf-token': serverInfo.csrfToken },
                timeout: 1000
            }, (res) => {
                resolve(res.statusCode === 200);
            });
            req.on('error', () => resolve(false));
            req.on('timeout', () => { req.destroy(); resolve(false); });
        });
        if (ok) return port;
    }
    return null;
}

async function syncTargetServer() {
    const info = detectPcLanguageServer();
    if (info) {
        const httpPort = await findHttpPort(info);
        if (httpPort) {
            if (currentTargetPort !== httpPort || currentCsrfToken !== info.csrfToken) {
                currentTargetPort = httpPort;
                currentCsrfToken = info.csrfToken;
                proxyHandler.setTarget('127.0.0.1', httpPort, info.csrfToken);
                console.log(`[SYNC PC] ✅ Kết nối Language Server PC (PID: ${info.pid}, Port: ${httpPort})`);
            }
            return;
        }
    }
    if (currentTargetPort !== 4401) {
        currentTargetPort = 4401;
        proxyHandler.setTarget('127.0.0.1', 4401, '');
    }
}

// Khởi chạy đồng bộ PC Server
syncTargetServer();
setInterval(syncTargetServer, 3000);

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
    console.log(`🚀 AGY LAN BRIDGE v6.0 (Modularized Clean Architecture)`);
    console.log(`📱 LAN Web UI:     http://${lanIp}:${LISTEN_PORT}`);
    console.log(`⚡ WebSocket Hub:  ws://${lanIp}:${LISTEN_PORT}/connect-websocket`);
    console.log(`=======================================================`);

    // Khởi động Session Watcher
    sessionWatcher.start(LISTEN_PORT);
});
