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

// Tự động chuyển hướng đăng nhập Google trực tiếp trên điện thoại
(function() {
  try {
    const origFetch = window.fetch;
    window.fetch = function(...args) {
      const url = args[0] && (typeof args[0] === 'string' ? args[0] : args[0].url);
      if (url && url.includes('LanguageServerService/Login')) {
        console.log('[AUTH_INTERCEPT] Bắt được RPC Login, chuyển hướng sang /auth/google');
        window.location.href = '/auth/google';
        return new Promise(() => {});
      }
      return origFetch.apply(this, args);
    };

    document.addEventListener('click', function(e) {
      const btn = e.target.closest('button, a, div[role="button"]');
      if (btn && (btn.innerText.includes('Continue with Google') || btn.innerText.includes('Sign in with Google'))) {
        console.log('[AUTH_INTERCEPT] Click nút Continue with Google, chuyển hướng /auth/google');
        e.preventDefault();
        e.stopPropagation();
        window.location.href = '/auth/google';
      }
    }, true);
  } catch(e) {}
})();
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

// Xử lý WebSocket Upgrade
server.on('upgrade', (req, clientSocket, head) => {
  const targetSocket = net.connect(currentTargetPort, TARGET_HOST, () => {
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
  console.log(` [AGY LAN BRIDGE V5.0 - DYNAMIC CSRF + REALTIME SYNC]`);
  console.log(` -> Địa chỉ LAN: http://${lanIp}:${LISTEN_PORT}`);
  console.log(` -> Chuyển tiếp: http://${TARGET_HOST}:${currentTargetPort}`);
  console.log(` -> CSRF Token: ${currentCsrfToken}`);
  console.log(`============================================================`);
});
