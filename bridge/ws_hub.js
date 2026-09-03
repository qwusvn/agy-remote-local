const crypto = require('crypto');

/**
 * WebSocket RFC 6455 Hub thuần bằng Node.js.
 * Quản lý kết nối hai chiều giữa Bridge và Android Client.
 */
class WebSocketHub {
    constructor() {
        this.clients = new Set();
    }

    /**
     * Nâng cấp socket TCP thành WebSocket RFC 6455
     */
    handleUpgrade(req, socket, head) {
        const key = req.headers['sec-websocket-key'];
        if (!key) {
            socket.destroy();
            return;
        }

        const acceptKey = crypto
            .createHash('sha1')
            .update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11')
            .digest('base64');

        const headers = [
            'HTTP/1.1 101 Switching Protocols',
            'Upgrade: websocket',
            'Connection: Upgrade',
            'Sec-WebSocket-Accept: ' + acceptKey,
            '\r\n'
        ];

        socket.write(headers.join('\r\n'));
        this.clients.add(socket);
        console.log(`[WS_HUB] 📱 Android client đã kết nối WebSocket Hub. Tổng số client: ${this.clients.size}`);

        socket.setKeepAlive(true, 2500);
        socket.setNoDelay(true);

        socket.on('close', () => {
            this.clients.delete(socket);
            console.log(`[WS_HUB] ❌ Client đã ngắt kết nối. Còn lại: ${this.clients.size}`);
        });

        socket.on('error', (err) => {
            this.clients.delete(socket);
        });

        // Gửi thông điệp chào mừng
        this.sendJson(socket, {
            type: 'WELCOME',
            message: 'Đã kết nối thành công tới Realtime WebSocket Hub'
        });
    }

    /**
     * Gửi frame text WebSocket (Opcode 0x1) tới một socket
     */
    sendJson(socket, data) {
        if (!socket || socket.destroyed) return;
        try {
            const payload = Buffer.from(JSON.stringify(data), 'utf8');
            const length = payload.length;
            let header;

            if (length < 126) {
                header = Buffer.alloc(2);
                header[0] = 0x81;
                header[1] = length;
            } else if (length < 65536) {
                header = Buffer.alloc(4);
                header[0] = 0x81;
                header[1] = 126;
                header.writeUInt16BE(length, 2);
            } else {
                header = Buffer.alloc(10);
                header[0] = 0x81;
                header[1] = 127;
                header.writeBigUInt64BE(BigInt(length), 2);
            }

            socket.write(Buffer.concat([header, payload]));
        } catch (e) {
            this.clients.delete(socket);
        }
    }

    /**
     * Phát sự kiện tới toàn bộ client đang kết nối
     */
    broadcast(data) {
        for (const client of this.clients) {
            this.sendJson(client, data);
        }
    }
}

module.exports = new WebSocketHub();
