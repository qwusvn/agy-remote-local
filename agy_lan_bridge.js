/**
 * AGY LAN Bridge (Node.js High Performance Native Proxy)
 * - Khong can cai them bat ky thu vien npm nao (Zero external dependencies).
 * - Rewrite Host: 127.0.0.1:4400 cho 100% HTTP, SSE, REST API, Chunked Streaming.
 * - Ho tro 100% WebSocket upgrade va streaming hai chieu.
 * - Ho tro upload file/anh khong gioi han dung luong.
 */

const http = require('http');
const net = require('net');
const os = require('os');

const TARGET_HOST = '127.0.0.1';
const TARGET_PORT = 4400;
const LISTEN_PORT = 4400;

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

const server = http.createServer((req, res) => {
  const headers = { ...req.headers };
  headers['host'] = `${TARGET_HOST}:${TARGET_PORT}`;
  if (headers['origin']) {
    headers['origin'] = `http://${TARGET_HOST}:${TARGET_PORT}`;
  }

  const options = {
    hostname: TARGET_HOST,
    port: TARGET_PORT,
    path: req.url,
    method: req.method,
    headers: headers,
  };

  const proxyReq = http.request(options, (proxyRes) => {
    res.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(res, { end: true });
  });

  proxyReq.on('error', (err) => {
    console.error(`[HTTP Error] ${req.url} -> ${err.message}`);
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'text/plain' });
      res.end('Bad Gateway: Cannot connect to Antigravity 2.0 daemon');
    }
  });

  req.pipe(proxyReq, { end: true });
});

// Xu ly WebSocket Upgrade requests
server.on('upgrade', (req, clientSocket, head) => {
  const targetSocket = net.connect(TARGET_PORT, TARGET_HOST, () => {
    // Tao lai Upgrade request header voi Host duoc rewrite sang 127.0.0.1
    let upgradeHeader = `${req.method} ${req.url} HTTP/${req.httpVersion}\r\n`;
    for (const [key, value] of Object.entries(req.headers)) {
      if (key.toLowerCase() === 'host') {
        upgradeHeader += `host: ${TARGET_HOST}:${TARGET_PORT}\r\n`;
      } else if (key.toLowerCase() === 'origin') {
        upgradeHeader += `origin: http://${TARGET_HOST}:${TARGET_PORT}\r\n`;
      } else {
        upgradeHeader += `${key}: ${value}\r\n`;
      }
    }
    upgradeHeader += '\r\n';

    targetSocket.write(upgradeHeader);
    if (head && head.length > 0) {
      targetSocket.write(head);
    }

    clientSocket.pipe(targetSocket);
    targetSocket.pipe(clientSocket);
  });

  targetSocket.on('error', (err) => {
    console.error(`[WebSocket Error] ${err.message}`);
    clientSocket.destroy();
  });

  clientSocket.on('error', () => {
    targetSocket.destroy();
  });
});

const lanIp = getLanIp();
server.listen(LISTEN_PORT, '0.0.0.0', () => {
  console.log('============================================================');
  console.log(' [AGY LAN BRIDGE - NODE.JS NATIVE RUNTIME]');
  console.log(` -> Dia chi ket noi dien thoai: http://${lanIp}:${LISTEN_PORT}`);
  console.log(` -> Chuyen tiep may chu:         http://${TARGET_HOST}:${TARGET_PORT}`);
  console.log(' -> Ho tro: HTTP 1.1, SSE, WebSocket, File Upload, Host Rewrite');
  console.log('============================================================');
});
