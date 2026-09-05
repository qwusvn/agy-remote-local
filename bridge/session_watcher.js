const fs = require('fs');
const path = require('path');
const os = require('os');
const wsHub = require('./ws_hub');

/**
 * Realtime Session Watcher:
 * Giám sát thư mục brain của Antigravity mỗi 800ms để phát hiện khi Agent hoàn thành câu trả lời.
 */
class SessionWatcher {
    constructor() {
        this.brainDir = path.join(os.homedir(), '.gemini', 'antigravity', 'brain');
        this.knownDoneSteps = new Set();
        this.watcherInterval = null;
    }

    start(port = 4400) {
        if (!fs.existsSync(this.brainDir)) {
            console.log(`[WATCHER] ⚠️ Không tìm thấy thư mục: ${this.brainDir}`);
            return;
        }

        console.log(`[WATCHER] 👁️ Bắt đầu Realtime Session Watcher tại: ${this.brainDir}`);
        this.watcherInterval = setInterval(() => {
            this.scanSessions(port);
        }, 800);
    }

    stop() {
        if (this.watcherInterval) {
            clearInterval(this.watcherInterval);
            this.watcherInterval = null;
        }
    }

    scanSessions(port) {
        try {
            const entries = fs.readdirSync(this.brainDir, { withFileTypes: true });
            for (const entry of entries) {
                if (!entry.isDirectory()) continue;
                const convoId = entry.name;
                const transcriptPath = path.join(this.brainDir, convoId, '.system_generated', 'logs', 'transcript.jsonl');
                if (!fs.existsSync(transcriptPath)) continue;

                const stat = fs.statSync(transcriptPath);
                // Đọc 4096 bytes cuối file để tối ưu tốc độ CPU
                const readSize = Math.min(stat.size, 4096);
                const buffer = Buffer.alloc(readSize);
                const fd = fs.openSync(transcriptPath, 'r');
                fs.readSync(fd, buffer, 0, readSize, stat.size - readSize);
                fs.closeSync(fd);

                const lines = buffer.toString('utf8').split('\n').filter(Boolean);
                for (let i = lines.length - 1; i >= 0; i--) {
                    try {
                        const step = JSON.parse(lines[i]);
                        if (step.status === 'DONE' && step.step_index !== undefined) {
                            // Chỉ thông báo kết luận cuối cùng cho người dùng:
                            // Bỏ qua các bước trung gian gọi tool (có tool_calls), log thực thi (GENERIC), hoặc output lệnh
                            if (step.type === 'GENERIC') continue;
                            if (step.tool_calls && step.tool_calls.length > 0) continue;
                            if (step.type !== 'PLANNER_RESPONSE' && step.type !== 'ASSISTANT') continue;

                            const content = (step.content || '').trim();
                            if (!content) continue;
                            if (content.includes('Created At:') || 
                                content.includes('Completed At:') || 
                                content.includes('The command exited') || 
                                content.includes('task-') ||
                                content.startsWith('Step ') ||
                                content.startsWith('Ran ')) {
                                continue;
                            }

                            const stepKey = `${convoId}_${step.step_index}`;
                            if (!this.knownDoneSteps.has(stepKey)) {
                                this.knownDoneSteps.add(stepKey);

                                const sessionTitle = this.extractSessionTitle(convoId);
                                const answerSummary = content.substring(0, 140).replace(/\n/g, ' ') || 'Agent đã hoàn tất câu trả lời';

                                console.log(`[WATCHER] 🔔 [HOÀN THÀNH TÁC VỤ] Phiên: "${sessionTitle}" (Convo: ${convoId})`);

                                wsHub.broadcast({
                                    type: 'AGENT_COMPLETED',
                                    convoId: convoId,
                                    title: sessionTitle,
                                    summary: answerSummary,
                                    url: `http://localhost:${port}/c/${convoId}`
                                });
                            }
                            break;
                        }
                    } catch (e) {}
                }
            }
        } catch (err) {}
    }

    extractSessionTitle(convoId) {
        try {
            const fullTranscript = path.join(this.brainDir, convoId, '.system_generated', 'logs', 'transcript.jsonl');
            if (fs.existsSync(fullTranscript)) {
                const head = fs.readFileSync(fullTranscript, { encoding: 'utf8', flag: 'r' }).split('\n').slice(0, 5);
                for (const line of head) {
                    if (!line) continue;
                    const obj = JSON.parse(line);
                    if (obj.type === 'USER_INPUT' && obj.content) {
                        return obj.content.substring(0, 32).trim() + (obj.content.length > 32 ? '...' : '');
                    }
                }
            }
        } catch (e) {}
        return `Phiên ${convoId.substring(0, 8)}`;
    }
}

module.exports = new SessionWatcher();
