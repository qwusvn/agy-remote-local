const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const os = require('os');
const wsHub = require('./ws_hub');

/**
 * Realtime Session Watcher v7.1 (Asynchronous & Non-Blocking):
 * Giám sát thư mục brain của Antigravity để phát hiện khi Agent hoàn thành câu trả lời.
 * - Hoạt động hoàn toàn bất đồng bộ (fs.promises), không bao giờ block event loop của Node.js.
 * - Caching title và bước cũ để loại bỏ hoàn toàn đĩa I/O thừa.
 * - Lọc triệt để 100%: Chỉ bắn sự kiện khi là câu trả lời kết luận thực sự của Agent.
 */
class SessionWatcher {
    constructor() {
        this.brainDir = path.join(os.homedir(), '.gemini', 'antigravity', 'brain');
        this.knownDoneSteps = new Set();
        this.titleCache = new Map();
        this.watcherInterval = null;
        this.isScanning = false;
    }

    start(port = 4400) {
        if (!fs.existsSync(this.brainDir)) {
            console.log(`[WATCHER] ⚠️ Không tìm thấy thư mục: ${this.brainDir}`);
            return;
        }

        console.log(`[WATCHER] 👁️ Khởi tạo Realtime Session Watcher tại: ${this.brainDir}`);
        this.initKnownSteps();

        // Quét bất đồng bộ mỗi 1500ms, hoàn toàn không block luồng xử lý tin nhắn
        this.watcherInterval = setInterval(() => {
            if (!this.isScanning) {
                this.scanSessionsAsync(port).catch(() => {});
            }
        }, 1500);
    }

    stop() {
        if (this.watcherInterval) {
            clearInterval(this.watcherInterval);
            this.watcherInterval = null;
        }
    }

    initKnownSteps() {
        try {
            const entries = fs.readdirSync(this.brainDir, { withFileTypes: true });
            for (const entry of entries) {
                if (!entry.isDirectory()) continue;
                const convoId = entry.name;
                const transcriptPath = path.join(this.brainDir, convoId, '.system_generated', 'logs', 'transcript.jsonl');
                if (!fs.existsSync(transcriptPath)) continue;

                try {
                    const stat = fs.statSync(transcriptPath);
                    const readSize = Math.min(stat.size, 8192);
                    const buffer = Buffer.alloc(readSize);
                    const fd = fs.openSync(transcriptPath, 'r');
                    fs.readSync(fd, buffer, 0, readSize, stat.size - readSize);
                    fs.closeSync(fd);

                    const lines = buffer.toString('utf8').split('\n').filter(Boolean);
                    for (const line of lines) {
                        try {
                            const step = JSON.parse(line);
                            if (step.status === 'DONE' && step.step_index !== undefined) {
                                this.knownDoneSteps.add(`${convoId}_${step.step_index}`);
                            }
                        } catch (e) {}
                    }
                } catch (e) {}
            }
            console.log(`[WATCHER] ✅ Đã lập chỉ mục ${this.knownDoneSteps.size} bước cũ. Chỉ thông báo các tác vụ hoàn thành mới.`);
        } catch (err) {
            console.error('[WATCHER] Lỗi khởi tạo index:', err);
        }
    }

    async scanSessionsAsync(port) {
        this.isScanning = true;
        try {
            const entries = await fsp.readdir(this.brainDir, { withFileTypes: true });
            const now = Date.now();

            for (const entry of entries) {
                if (!entry.isDirectory()) continue;
                const convoId = entry.name;
                const transcriptPath = path.join(this.brainDir, convoId, '.system_generated', 'logs', 'transcript.jsonl');

                let stat;
                try {
                    stat = await fsp.stat(transcriptPath);
                } catch (e) {
                    continue; // File không tồn tại
                }

                // Bỏ qua các file không được cập nhật trong 2 phút gần nhất
                if (now - stat.mtimeMs > 120000) continue;

                // Đọc bất đồng bộ 4096 bytes cuối file
                const readSize = Math.min(stat.size, 4096);
                let buffer = Buffer.alloc(readSize);
                let handle;
                try {
                    handle = await fsp.open(transcriptPath, 'r');
                    await handle.read(buffer, 0, readSize, stat.size - readSize);
                } finally {
                    if (handle) await handle.close();
                }

                const lines = buffer.toString('utf8').split('\n').filter(Boolean);
                for (let i = lines.length - 1; i >= 0; i--) {
                    try {
                        const step = JSON.parse(lines[i]);
                        if (step.status === 'DONE' && step.step_index !== undefined) {
                            const stepKey = `${convoId}_${step.step_index}`;
                            if (this.knownDoneSteps.has(stepKey)) {
                                break;
                            }

                            this.knownDoneSteps.add(stepKey);

                            // BỘ LỌC CHẶN THÔNG BÁO RÁC:
                            // 1. Chỉ chấp nhận PLANNER_RESPONSE hoặc ASSISTANT
                            if (step.type !== 'PLANNER_RESPONSE' && step.type !== 'ASSISTANT') continue;

                            // 2. Tuyệt đối không thông báo khi Agent đang chạy tool (tool_calls)
                            if (step.tool_calls && step.tool_calls.length > 0) continue;

                            // 3. Nội dung phải là chữ người đọc được, loại bỏ các log lệnh
                            let content = (step.content || '').trim();
                            if (!content) continue;

                            if (content.includes('<USER_REQUEST>') ||
                                content.includes('</USER_REQUEST>') ||
                                content.includes('Created At:') || 
                                content.includes('Completed At:') || 
                                content.includes('The command exited') || 
                                content.includes('task-') ||
                                content.startsWith('Step ') ||
                                content.startsWith('Ran ') ||
                                content.startsWith('Edited ') ||
                                content.startsWith('Explored ') ||
                                content.startsWith('Task id ')) {
                                continue;
                            }

                            // Làm sạch nội dung tóm tắt
                            let cleanSummary = content
                                .replace(/```[\s\S]*?```/g, '')
                                .replace(/<[^>]*>/g, '')
                                .replace(/\s+/g, ' ')
                                .trim();

                            if (cleanSummary.length < 5) continue;
                            if (cleanSummary.length > 140) {
                                cleanSummary = cleanSummary.substring(0, 137) + '...';
                            }

                            const sessionTitle = await this.extractSessionTitleAsync(convoId);
                            console.log(`[WATCHER] 🔔 [KẾT LUẬN HOÀN TẤT] Phiên: "${sessionTitle}" -> "${cleanSummary}"`);

                            wsHub.broadcast({
                                type: 'AGENT_COMPLETED',
                                convoId: convoId,
                                title: sessionTitle,
                                summary: cleanSummary,
                                url: `http://localhost:${port}/c/${convoId}`
                            });

                            break;
                        }
                    } catch (e) {}
                }
            }
        } catch (err) {
        } finally {
            this.isScanning = false;
        }
    }

    async extractSessionTitleAsync(convoId) {
        if (this.titleCache.has(convoId)) {
            return this.titleCache.get(convoId);
        }

        try {
            const fullTranscript = path.join(this.brainDir, convoId, '.system_generated', 'logs', 'transcript.jsonl');
            const data = await fsp.readFile(fullTranscript, 'utf8');
            const head = data.split('\n').slice(0, 10);
            for (const line of head) {
                if (!line) continue;
                const obj = JSON.parse(line);
                if (obj.type === 'USER_INPUT' && obj.content) {
                    let clean = obj.content
                        .replace(/<USER_REQUEST>/g, '')
                        .replace(/<\/USER_REQUEST>/g, '')
                        .replace(/<[^>]*>/g, '')
                        .trim();
                    if (clean.includes('\n')) {
                        clean = clean.split('\n').find(l => l.trim().length > 0) || clean;
                    }
                    clean = clean.trim();
                    if (clean) {
                        const title = clean.substring(0, 32).trim() + (clean.length > 32 ? '...' : '');
                        this.titleCache.set(convoId, title);
                        return title;
                    }
                }
            }
        } catch (e) {}

        const fallback = `Phiên ${convoId.substring(0, 8)}`;
        this.titleCache.set(convoId, fallback);
        return fallback;
    }
}

module.exports = new SessionWatcher();
