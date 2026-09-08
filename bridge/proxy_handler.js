const http = require('http');
const net = require('net');
const url = require('url');

class ProxyHandler {
    constructor() {
        this.targetHost = '127.0.0.1';
        this.targetPort = 0;
        this.csrfToken = '';
        this.onConnectionError = null;
    }

    setTarget(host, port, csrfToken) {
        this.targetHost = host;
        this.targetPort = port;
        if (csrfToken) this.csrfToken = csrfToken;
    }

    setOnConnectionError(callback) {
        this.onConnectionError = callback;
    }

    handleHttpRequest(req, res) {
        if (!this.targetPort) {
            res.writeHead(503, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end('Antigravity LAN Bridge: Đang đồng bộ Language Server...');
            return;
        }

        // Intercept OAuth Callback từ Mobile
        if (req.url === '/__agy_oauth_code' && req.method === 'POST') {
            let body = '';
            req.on('data', chunk => { body += chunk; });
            req.on('end', () => {
                try {
                    const parsed = JSON.parse(body);
                    console.log('[PROXY] 🔑 Nhận Auth Code từ điện thoại:', parsed.code);
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ status: 'OK' }));
                } catch (e) {
                    res.writeHead(400, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ error: 'Invalid JSON' }));
                }
            });
            return;
        }

        if (res.socket) {
            res.socket.setNoDelay(true);
        }

        const parsedUrl = url.parse(req.url);
        const headers = { ...req.headers };

        headers['host'] = `${this.targetHost}:${this.targetPort}`;
        headers['origin'] = `http://${this.targetHost}:${this.targetPort}`;
        headers['referer'] = `http://${this.targetHost}:${this.targetPort}/`;

        if (this.csrfToken) {
            headers['x-codeium-csrf-token'] = this.csrfToken;
        }

        // Xóa các header hop-by-hop trong HTTP request thông thường
        delete headers['connection'];
        delete headers['upgrade'];

        const options = {
            hostname: this.targetHost,
            port: this.targetPort,
            path: parsedUrl.path,
            method: req.method,
            headers: headers
        };

        const proxyReq = http.request(options, (proxyRes) => {
            const resHeaders = { ...proxyRes.headers };
            delete resHeaders['transfer-encoding'];
            delete resHeaders['connection'];

            res.writeHead(proxyRes.statusCode, resHeaders);
            if (typeof res.flushHeaders === 'function') {
                res.flushHeaders();
            }

            proxyRes.on('end', () => {
                if (proxyRes.trailers && Object.keys(proxyRes.trailers).length > 0) {
                    try {
                        res.addTrailers(proxyRes.trailers);
                    } catch (e) {}
                }
            });

            proxyRes.on('error', (err) => {
                console.error('[PROXY] proxyRes error:', err.message);
                if (!res.destroyed) res.destroy(err);
            });

            proxyRes.pipe(res);
        });

        proxyReq.setNoDelay(true);

        proxyReq.on('error', (err) => {
            console.error(`[PROXY] ❌ Lỗi kết nối tới Language Server (${this.targetHost}:${this.targetPort}):`, err.message);
            if (this.onConnectionError) {
                this.onConnectionError(err);
            }
            if (!res.headersSent) {
                res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
                res.end('Antigravity LAN Bridge: Lỗi kết nối tới Language Server');
            } else {
                if (!res.destroyed) res.destroy();
            }
        });

        // Dọn dẹp kết nối CHỈ khi client bị ngắt ngang trước khi hoàn tất
        req.on('close', () => {
            if (!req.readableEnded && !proxyReq.destroyed) {
                proxyReq.destroy();
            }
        });
        res.on('close', () => {
            if (!res.writableFinished && !proxyReq.destroyed) {
                proxyReq.destroy();
            }
        });

        req.on('error', (err) => {
            if (!proxyReq.destroyed) proxyReq.destroy(err);
        });

        req.pipe(proxyReq);
    }

    handleTunnelUpgrade(req, clientSocket, head) {
        if (!this.targetPort) {
            clientSocket.destroy();
            return;
        }

        clientSocket.setKeepAlive(true, 2500);
        clientSocket.setNoDelay(true);

        const targetSocket = net.connect(this.targetPort, this.targetHost, () => {
            targetSocket.setKeepAlive(true, 2500);
            targetSocket.setNoDelay(true);

            let rawReq = `${req.method} ${req.url} HTTP/${req.httpVersion}\r\n`;
            for (let i = 0; i < req.rawHeaders.length; i += 2) {
                let key = req.rawHeaders[i];
                let val = req.rawHeaders[i + 1];
                if (key.toLowerCase() === 'host') val = `${this.targetHost}:${this.targetPort}`;
                if (key.toLowerCase() === 'origin') val = `http://${this.targetHost}:${this.targetPort}`;
                rawReq += `${key}: ${val}\r\n`;
            }
            if (this.csrfToken) {
                rawReq += `x-codeium-csrf-token: ${this.csrfToken}\r\n`;
            }
            rawReq += '\r\n';

            targetSocket.write(rawReq);
            if (head && head.length > 0) targetSocket.write(head);

            clientSocket.pipe(targetSocket);
            targetSocket.pipe(clientSocket);
        });

        targetSocket.on('error', (err) => {
            if (this.onConnectionError) this.onConnectionError(err);
            clientSocket.destroy();
        });
        clientSocket.on('error', () => { targetSocket.destroy(); });
    }
}

module.exports = new ProxyHandler();
