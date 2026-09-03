/**
 * AGY LAN Bridge v5.0 (Dynamic CSRF Sync + Full Duplex Streaming Proxy)
 * - Tự động trích xuất và đồng bộ CSRF Token từ agy.exe theo thời gian thực.
 * - Hỗ trợ toàn diện Connect-RPC / gRPC-Web chunked streaming (JetboxSubscribeToSummaries, ProjectUpdatesStream).
 * - Đảm bảo danh sách cuộc hội thoại và trạng thái đồng bộ 100% giữa PC và Điện thoại.
 */

const http = require('http');
const https = require('https');
const net = require('net');
const os = require('os');
const fs = require('fs');
const crypto = require('crypto');
const { exec, execSync } = require('child_process');

const TARGET_HOST = '127.0.0.1';
let currentTargetPort = 51896;
const LISTEN_PORT = 4400;

let currentCsrfToken = 'cab57588-0fdb-48bf-8cc3-81389fe84f9c';
let lastDetectedPid = 0;

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
        lastDetectedPid = info.pid;
        console.log(`[SYNC PC] ✅ Đã kết nối với Language Server PC (PID: ${info.pid}, Port: ${httpPort}) - Tài khoản đồng bộ 100% với máy tính!`);
      }
      return;
    }
  }
  // Dự phòng nếu không mở PC IDE: dùng agy.exe port 4401
  if (currentTargetPort !== 4401) {
    currentTargetPort = 4401;
    console.log('[SYNC PC] Chuyển về Language Server dự phòng (Port: 4401)');
  }
}

// Khởi động đồng bộ ban đầu và mỗi 3 giây
syncTargetServer();
setInterval(syncTargetServer, 3000);

const SOURCE_DIR = 'C:\\Users\\qwusv\\.gemini\\antigravity';
const CLI_DIR = 'C:\\Users\\qwusv\\.gemini\\antigravity-cli';

// Realtime File Watcher: Tự động copy tức thì khi IDE cập nhật summaries/state
function syncSummaries() {
  try {
    const srcPb = `${SOURCE_DIR}\\agyhub_summaries_proto.pb`;
    const dstPb = `${CLI_DIR}\\agyhub_summaries_proto.pb`;
    if (fs.existsSync(srcPb)) {
      const srcBuf = fs.readFileSync(srcPb);
      let needCopy = true;
      if (fs.existsSync(dstPb)) {
        const dstBuf = fs.readFileSync(dstPb);
        if (srcBuf.equals(dstBuf)) needCopy = false;
      }
      if (needCopy) {
        fs.writeFileSync(dstPb, srcBuf);
        console.log(`[SYNC] Đã đồng bộ agyhub_summaries_proto.pb sang CLI (${srcBuf.length} bytes)`);
      }
    }
  } catch (e) {
    console.error(`[SYNC ERR] ${e.message}`);
  }
}

// Watch thư mục antigravity để bắt mọi thay đổi summaries
try {
  fs.watch(SOURCE_DIR, (eventType, filename) => {
    if (filename && filename.includes('summaries')) {
      syncSummaries();
    }
  });
  console.log('[WATCHER] Đã kích hoạt Realtime File Watcher trên .gemini/antigravity');
} catch (e) {
  console.error(`[WATCHER ERR] ${e.message}`);
}

// Chạy đồng bộ ngay lập tức và định kỳ mỗi 2 giây phòng ngừa
syncSummaries();
setInterval(syncSummaries, 2000);



const server = http.createServer((req, res) => {
  // 0. Xử lý yêu cầu Đổi tài khoản Google (/auth/google)
  if (req.method === 'GET' && req.url.startsWith('/auth/google')) {
    const verifier = base64URLEncode(crypto.randomBytes(32));
    const challenge = base64URLEncode(sha256(verifier));
    activeOAuthVerifier = verifier;
    console.log('[AUTH] Sinh PKCE Verifier mới để đổi tài khoản');

    const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${encodeURIComponent(OAUTH_CLIENT_ID)}` +
      `&redirect_uri=${encodeURIComponent(OAUTH_REDIRECT_URI)}` +
      `&response_type=code` +
      `&scope=${encodeURIComponent(OAUTH_SCOPES)}` +
      `&code_challenge=${challenge}` +
      `&code_challenge_method=S256` +
      `&access_type=offline` +
      `&prompt=select_account`;

    res.writeHead(302, { 'Location': authUrl });
    res.end();
    return;
  }

  // 0.1. Xử lý API nạp code (/api/auth_code)
  if (req.method === 'POST' && req.url.startsWith('/api/auth_code')) {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      try {
        const json = JSON.parse(body);
        const code = json.code;
        if (!code) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Missing code' }));
          return;
        }

        console.log('[AUTH] Nhận Authorization Code từ điện thoại, đang đổi token với Google...');
        
        const postData = new URLSearchParams({
          client_id: OAUTH_CLIENT_ID,
          client_secret: OAUTH_CLIENT_SECRET,
          code: code,
          code_verifier: activeOAuthVerifier,
          grant_type: 'authorization_code',
          redirect_uri: OAUTH_REDIRECT_URI
        }).toString();

        const tokenReq = https.request('https://oauth2.googleapis.com/token', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'Content-Length': Buffer.byteLength(postData)
          }
        }, (tokenRes) => {
          let tokenBody = '';
          tokenRes.on('data', chunk => tokenBody += chunk);
          tokenRes.on('end', () => {
            try {
              const resJson = JSON.parse(tokenBody);
              if (tokenRes.statusCode === 200 && resJson.access_token) {
                const rt = resJson.refresh_token;
                const at = resJson.access_token;
                const expiresIn = resJson.expires_in || 3600;
                const expiry = new Date(Date.now() + expiresIn * 1000).toISOString();

                const tokenPath = 'C:\\Users\\qwusv\\.gemini\\jetski-standalone-oauth-token';
                let oldRt = rt;
                if (!rt && fs.existsSync(tokenPath)) {
                  try {
                    const oldData = JSON.parse(fs.readFileSync(tokenPath, 'utf8'));
                    oldRt = oldData.refresh_token;
                  } catch(e){}
                }

                const tokenObj = {
                  access_token: at,
                  token_type: 'Bearer',
                  refresh_token: oldRt || rt,
                  expiry: expiry
                };

                fs.writeFileSync(tokenPath, JSON.stringify(tokenObj, null, 2), 'utf8');
                console.log('[AUTH] Đã lưu token mới vào jetski-standalone-oauth-token thành công!');

                // Khởi động lại Language Server để nạp tài khoản mới
                exec('schtasks /Run /TN "AgyRemoteControl"', (err) => {
                  if (err) console.error('[AUTH RESTART ERR]', err);
                  else console.log('[AUTH] Đã trigger khởi động lại agy.exe');
                });

                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: true, message: 'Đổi tài khoản thành công!' }));
              } else {
                console.error('[AUTH ERR] Google token exchange failed:', tokenBody);
                res.writeHead(400, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ error: tokenBody }));
              }
            } catch (err) {
              res.writeHead(500, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ error: err.message }));
            }
          });
        });

        tokenReq.on('error', (err) => {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: err.message }));
        });

        tokenReq.write(postData);
        tokenReq.end();

      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: err.message }));
      }
    });
    return;
  }

  // 1. Chuyển tiếp request tới target với dynamic CSRF token
  const headers = { ...req.headers };
  headers['host'] = `${TARGET_HOST}:${currentTargetPort}`;
  if (headers['origin']) {
    headers['origin'] = `http://${TARGET_HOST}:${currentTargetPort}`;
  }
  
  // Nạp CSRF token mới nhất nếu client không gửi hoặc gửi token cũ
  headers['x-codeium-csrf-token'] = currentCsrfToken;
  delete headers['accept-encoding'];

  const options = {
    hostname: TARGET_HOST,
    port: currentTargetPort,
    path: req.url,
    method: req.method,
    headers: headers,
  };

  const proxyReq = http.request(options, (proxyRes) => {
    const contentType = proxyRes.headers['content-type'] || '';
    const isHtml = contentType.includes('text/html');

    if (isHtml) {
      let bodyChunks = [];
      proxyRes.on('data', (chunk) => bodyChunks.push(chunk));
      proxyRes.on('end', () => {
        let rawBuf = Buffer.concat(bodyChunks);
        const enc = proxyRes.headers['content-encoding'];
        try {
          if (enc === 'gzip') {
            rawBuf = zlib.gunzipSync(rawBuf);
          } else if (enc === 'deflate') {
            rawBuf = zlib.inflateSync(rawBuf);
          } else if (enc === 'br') {
            rawBuf = zlib.brotliDecompressSync(rawBuf);
          }
        } catch (e) {
          console.error('[DECOMPRESS ERR]', e.message);
        }

        let html = rawBuf.toString('utf8');
        try {
          const pcStoragePath = 'C:/Users/qwusv/AppData/Roaming/Antigravity/app_storage.json';
          if (fs.existsSync(pcStoragePath)) {
            const pcData = JSON.parse(fs.readFileSync(pcStoragePath, 'utf8'));
            const keys = ['pinned_conversations_order', 'projectsOrder', 'sidebar_collapsed_sections', 'sidebar_collapsed_section_headers'];
            const toInject = {};
            for (const k of keys) {
              if (pcData[k] !== undefined) {
                toInject[k] = typeof pcData[k] === 'string' ? pcData[k] : JSON.stringify(pcData[k]);
              }
            }
            const injectScript = `<script>
try {
  const _d = ${JSON.stringify(toInject)};
  for (const [k, v] of Object.entries(_d)) {
    localStorage.setItem(k, v);
  }
} catch(e) {}
</script>`;
            if (html.includes('</head>')) {
              html = html.replace('</head>', `${injectScript}</head>`);
            } else {
              html = injectScript + html;
            }
          }
        } catch (e) {
          console.error('[INJECT ERR]', e.message);
        }

        const outBuf = Buffer.from(html, 'utf8');
        const newHeaders = { ...proxyRes.headers, 'content-length': outBuf.length };
        delete newHeaders['content-encoding'];
        res.writeHead(proxyRes.statusCode, newHeaders);
        res.end(outBuf);
      });
    } else {
      res.writeHead(proxyRes.statusCode, proxyRes.headers);
      proxyRes.pipe(res);
    }
  });

  proxyReq.on('error', (err) => {
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'text/plain' });
      res.end('Bad Gateway: ' + err.message);
    }
  });

  if (req.method === 'GET' || req.method === 'HEAD') {
    proxyReq.end();
  } else {
    req.pipe(proxyReq);
  }
});

// ==========================================
// REALTIME WEBSOCKET HUB & SESSION WATCHER
// ==========================================
const wsClients = new Set();
const BRAIN_DIR = 'C:\\Users\\qwusv\\.gemini\\antigravity\\brain';
const lastKnownSteps = new Map();
const convoTitles = new Map();

function sendWsJson(socket, obj) {
  try {
    const payload = Buffer.from(JSON.stringify(obj), 'utf8');
    const len = payload.length;
    let header;
    if (len < 126) {
      header = Buffer.from([0x81, len]);
    } else if (len <= 65535) {
      header = Buffer.alloc(4);
      header[0] = 0x81;
      header[1] = 126;
      header.writeUInt16BE(len, 2);
    } else {
      header = Buffer.alloc(10);
      header[0] = 0x81;
      header[1] = 127;
      header.writeBigUInt64BE(BigInt(len), 2);
    }
    socket.write(Buffer.concat([header, payload]));
  } catch (e) {
    wsClients.delete(socket);
  }
}

function broadcastWs(obj) {
  for (const client of wsClients) {
    sendWsJson(client, obj);
  }
}

function handleAppWebSocketUpgrade(req, socket) {
  const key = req.headers['sec-websocket-key'];
  if (!key) {
    socket.destroy();
    return;
  }
  const acceptKey = crypto.createHash('sha1').update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
    'Upgrade: websocket\r\n' +
    'Connection: Upgrade\r\n' +
    `Sec-WebSocket-Accept: ${acceptKey}\r\n\r\n`
  );
  socket.setKeepAlive(true, 2500);
  socket.setNoDelay(true);
  wsClients.add(socket);
  console.log(`[APP WS] 📱 Android client đã kết nối WebSocket Hub (${wsClients.size} kết nối)`);

  sendWsJson(socket, {
    type: 'CONNECTED',
    title: 'AGY Remote Realtime Hub',
    message: 'Đã đồng bộ thời gian thực với Antigravity PC',
    timestamp: Date.now()
  });

  socket.on('close', () => wsClients.delete(socket));
  socket.on('error', () => wsClients.delete(socket));
  socket.on('data', (buf) => {
    if (buf[0] === 0x89) {
      socket.write(Buffer.from([0x8A, 0x00])); // Pong
    }
  });
}

// Trích xuất tiêu đề từ nội dung câu hỏi đầu tiên
function getConversationTitle(convoId, transcriptPath) {
  if (convoTitles.has(convoId)) return convoTitles.get(convoId);
  try {
    if (fs.existsSync(transcriptPath)) {
      const content = fs.readFileSync(transcriptPath, 'utf8');
      const lines = content.split('\n');
      for (const line of lines) {
        if (!line.trim()) continue;
        try {
          const item = JSON.parse(line);
          if (item.type === 'USER_INPUT' && item.content) {
            let t = item.content.replace(/<[^>]*>/g, '').trim();
            t = t.split('\n')[0].trim();
            if (t.length > 30) t = t.substring(0, 30) + '...';
            if (t) {
              convoTitles.set(convoId, t);
              return t;
            }
          }
        } catch (e) {}
      }
    }
  } catch (e) {}
  return 'Phiên Antigravity';
}

// Giám sát cập nhật các phiên trong thư mục brain theo thời gian thực
function scanBrainSessions() {
  try {
    if (!fs.existsSync(BRAIN_DIR)) return;
    const entries = fs.readdirSync(BRAIN_DIR, { withFileTypes: true });
    for (const ent of entries) {
      if (!ent.isDirectory() || ent.name.startsWith('.')) continue;
      const convoId = ent.name;
      const transcriptPath = `${BRAIN_DIR}\\${convoId}\\.system_generated\\logs\\transcript.jsonl`;
      if (!fs.existsSync(transcriptPath)) continue;

      const stat = fs.statSync(transcriptPath);
      const lastKnown = lastKnownSteps.get(convoId) || { size: 0, lastIndex: -1, lastMtime: 0 };
      if (stat.size === lastKnown.size) continue;

      // Đọc các dòng mới từ transcript
      const content = fs.readFileSync(transcriptPath, 'utf8');
      const lines = content.trim().split('\n');
      if (lines.length === 0) continue;

      const lastLine = lines[lines.length - 1];
      try {
        const item = JSON.parse(lastLine);
        const title = getConversationTitle(convoId, transcriptPath);

        // Chỉ thông báo khi có bước mới
        if (item.step_index !== lastKnown.lastIndex && lastKnown.size > 0) {
          if (item.type === 'PLANNER_RESPONSE' && item.status === 'DONE') {
            let summary = (item.content || '').replace(/<[^>]*>/g, '').trim();
            summary = summary.replace(/[#*`_~]/g, '');
            if (summary.length > 130) summary = summary.substring(0, 130) + '...';
            if (!summary) summary = 'Agent đã hoàn tất câu trả lời';

            console.log(`[SESSION COMPLETED] 📢 ${title}: ${summary}`);
            broadcastWs({
              type: 'AGENT_COMPLETED',
              convoId: convoId,
              title: title,
              summary: summary,
              url: `http://${getLanIp()}:${LISTEN_PORT}/c/${convoId}`,
              timestamp: Date.now()
            });
          } else if (item.type === 'USER_INPUT') {
            broadcastWs({
              type: 'SESSION_START',
              convoId: convoId,
              title: title,
              isWorking: true,
              timestamp: Date.now()
            });
          }
        }

        lastKnownSteps.set(convoId, {
          size: stat.size,
          lastIndex: item.step_index,
          lastMtime: stat.mtimeMs
        });
      } catch (e) {}
    }
  } catch (e) {
    console.error('[SCAN ERR]', e.message);
  }
}

// Quét định kỳ mỗi 800ms để bắt mọi sự kiện phiên làm việc
setInterval(scanBrainSessions, 800);
scanBrainSessions();

// Xử lý WebSocket Upgrade (Phân luồng giữa App Hub và Language Server Proxy)
server.on('upgrade', (req, clientSocket, head) => {
  // 1. Phục vụ kết nối Realtime Hub từ Android App Service
  if (req.url.startsWith('/connect-websocket') || req.url.startsWith('/ws/events')) {
    handleAppWebSocketUpgrade(req, clientSocket);
    return;
  }

  // 2. Chuyển tiếp WebSocket tới Language Server của PC với KeepAlive cao cấp
  const targetSocket = net.connect(currentTargetPort, TARGET_HOST, () => {
    clientSocket.setKeepAlive(true, 2500);
    targetSocket.setKeepAlive(true, 2500);
    clientSocket.setNoDelay(true);
    targetSocket.setNoDelay(true);

    let upgradeHeader = `${req.method} ${req.url} HTTP/${req.httpVersion}\r\n`;
    for (const [key, value] of Object.entries(req.headers)) {
      if (key.toLowerCase() === 'host') {
        upgradeHeader += `host: ${TARGET_HOST}:${currentTargetPort}\r\n`;
      } else if (key.toLowerCase() === 'origin') {
        upgradeHeader += `origin: http://${TARGET_HOST}:${currentTargetPort}\r\n`;
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

  targetSocket.on('error', () => {
    clientSocket.destroy();
  });

  clientSocket.on('error', () => {
    targetSocket.destroy();
  });
});

server.listen(LISTEN_PORT, '0.0.0.0', () => {
  const lanIp = getLanIp();
  console.log(`============================================================`);
  console.log(` [AGY LAN BRIDGE V5.5 - REALTIME WEBSOCKET HUB & NOTIFICATIONS]`);
  console.log(` -> Địa chỉ LAN: http://${lanIp}:${LISTEN_PORT}`);
  console.log(` -> Realtime WebSocket: ws://${lanIp}:${LISTEN_PORT}/connect-websocket`);
  console.log(` -> Chuyển tiếp: http://${TARGET_HOST}:${currentTargetPort}`);
  console.log(` -> CSRF Token: ${currentCsrfToken}`);
  console.log(`============================================================`);
});

