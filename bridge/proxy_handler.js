const http = require('http');
const net = require('net');
const url = require('url');

class ProxyHandler {
    constructor() {
        this.targetHost = '127.0.0.1';
        this.targetPort = 51896;
        this.csrfToken = '';
    }

    setTarget(host, port, csrfToken) {
        this.targetHost = host;
        this.targetPort = port;
        if (csrfToken) this.csrfToken = csrfToken;
    }

    handleHttpRequest(req, res) {
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

        const parsedUrl = url.parse(req.url);
        const headers = { ...req.headers };

        headers['host'] = `${this.targetHost}:${this.targetPort}`;
        headers['origin'] = `http://${this.targetHost}:${this.targetPort}`;
        headers['referer'] = `http://${this.targetHost}:${this.targetPort}/`;

        if (this.csrfToken) {
            headers['x-codeium-csrf-token'] = this.csrfToken;
        }

        const options = {
            hostname: this.targetHost,
            port: this.targetPort,
            path: parsedUrl.path,
            method: req.method,
            headers: headers
        };

        const proxyReq = http.request(options, (proxyRes) => {
            res.writeHead(proxyRes.statusCode, proxyRes.headers);
            proxyRes.pipe(res, { end: true });
        });

        proxyReq.on('error', (err) => {
            if (!res.headersSent) {
                res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
                res.end('Antigravity LAN Bridge: Lỗi kết nối tới Language Server');
            }
        });

        req.pipe(proxyReq, { end: true });
    }

    handleTunnelUpgrade(req, clientSocket, head) {
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

        targetSocket.on('error', () => { clientSocket.destroy(); });
        clientSocket.on('error', () => { targetSocket.destroy(); });
    }
}

module.exports = new ProxyHandler();
