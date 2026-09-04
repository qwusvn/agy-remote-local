package com.example.agyremote.ui.webview.scripts

/**
 * Module độc lập quản lý việc theo dõi Phiên làm việc (Session) và trạng thái Agent.
 * - Trích xuất tiêu đề phiên thực tế (tránh Tab ngố).
 * - Giám sát trạng thái Agent isWorking.
 * - Theo dõi đường dẫn phiên hoạt động gần nhất.
 * - Quan sát biến đổi DOM ngầm để giữ giao diện realtime.
 */
object AgySessionScript {
    fun getScript(): String = """
        (function() {
            try {
                // 1. Giám sát trạng thái Agent isWorking
                let lastWorkingState = false;
                let consecutiveMatches = 0;
                let consecutiveAbsents = 0;

                function checkAgentStatus() {
                    try {
                        let isWorking = false;
                        const chatInput = document.querySelector('textarea, [contenteditable="true"], input[placeholder*="Ask" i]');
                        if (chatInput) {
                            const chatContainer = chatInput.closest('form, div.relative, [class*="chat-input"], [class*="input-container"]') || document.body;
                            const stopBtn = chatContainer.querySelector('button[aria-label*="Stop" i], button[aria-label*="Cancel" i], button rect, button .lucide-square');
                            if (stopBtn) isWorking = true;
                        }

                        if (!isWorking) {
                            const explicitStopBtn = document.querySelector('button[aria-label="Stop generation" i], button[aria-label="Stop" i], button[aria-label="Cancel" i]');
                            if (explicitStopBtn) isWorking = true;
                        }

                        if (!isWorking) {
                            const streamActive = document.querySelector('[data-is-generating="true"], [class*="streaming-active"], [class*="thinking-bubble"]');
                            if (streamActive) isWorking = true;
                        }

                        if (isWorking) {
                            consecutiveMatches++;
                            consecutiveAbsents = 0;
                            if (consecutiveMatches >= 2 && !lastWorkingState) {
                                lastWorkingState = true;
                                if (window.AgyAndroidBridge) window.AgyAndroidBridge.reportWorkingStatus(true);
                            }
                        } else {
                            consecutiveAbsents++;
                            consecutiveMatches = 0;
                            if (consecutiveAbsents >= 2 && lastWorkingState) {
                                lastWorkingState = false;
                                if (window.AgyAndroidBridge) window.AgyAndroidBridge.reportWorkingStatus(false);
                            }
                        }
                    } catch(e) {}
                }

                if (!window.__agyMonitorInterval) {
                    window.__agyMonitorInterval = setInterval(checkAgentStatus, 400);
                }

                // 2. Theo dõi active conversation path
                function trackActiveConvo() {
                    try {
                        const p = window.location.pathname;
                        if (p.startsWith('/c/')) {
                            window.__agyLastActiveConvo = p;
                            try { localStorage.setItem('agy_last_active_convo', p); } catch(e){}
                        }
                    } catch(e) {}
                }
                if (!window.__agyTrackInterval) {
                    window.__agyTrackInterval = setInterval(trackActiveConvo, 500);
                }
                trackActiveConvo();

                // 3. Trích xuất tiêu đề phiên thực tế cho Android Tab
                let lastReportedTitle = '';
                function reportCurrentSession() {
                    try {
                        const p = window.location.pathname;
                        let title = '';
                        if (p === '/' || p === '') {
                            title = 'Dự án / Phiên';
                        } else if (p.startsWith('/c/')) {
                            // Ưu tiên 1: Lấy từ sidebar item của chính phiên này
                            const sidebarLink = document.querySelector('a[href="' + p + '"], a[href^="' + p + '"]');
                            if (sidebarLink && sidebarLink.innerText) {
                                const text = sidebarLink.innerText.trim().split('\n')[0];
                                if (text && text.length > 1 && !text.startsWith('Antigravity')) {
                                    title = text;
                                }
                            }

                            // Ưu tiên 2: Lấy từ active conversation item
                            if (!title) {
                                const activeItem = document.querySelector('[class*="conversationItem"][class*="active"], [aria-selected="true"] [class*="title"], [data-active="true"]');
                                if (activeItem && activeItem.innerText) {
                                    const text = activeItem.innerText.trim().split('\n')[0];
                                    if (text && text.length > 1 && !text.startsWith('Antigravity')) {
                                        title = text;
                                    }
                                }
                            }

                            // Ưu tiên 3: Lấy từ header title
                            if (!title) {
                                const header = document.querySelector('header, [class*="header"], [role="banner"]');
                                if (header) {
                                    const candidates = Array.from(header.querySelectorAll('h1, h2, h3, div, span'))
                                        .filter(el => el.children.length === 0 && !el.closest('button') && el.innerText && el.innerText.trim().length > 1);
                                    if (candidates.length > 0) {
                                        const text = candidates[0].innerText.trim();
                                        if (text && !text.startsWith('Antigravity')) title = text;
                                    }
                                }
                            }

                            // Ưu tiên 4: Lấy từ câu hỏi đầu tiên của người dùng
                            if (!title) {
                                const userMsg = document.querySelector('[data-role="user"], .user-message, div[class*="user_message"]');
                                if (userMsg && userMsg.innerText) {
                                    const snippet = userMsg.innerText.trim().split('\n')[0].substring(0, 30);
                                    if (snippet) title = snippet + '...';
                                }
                            }

                            if (!title) {
                                title = 'Phiên đang mở';
                            }
                        } else if (p.startsWith('/history')) {
                            title = 'Lịch sử';
                        }

                        if (title && title !== lastReportedTitle) {
                            lastReportedTitle = title;
                            if (window.AgyAndroidBridge && window.AgyAndroidBridge.reportSessionInfo) {
                                window.AgyAndroidBridge.reportSessionInfo(window.location.href, title);
                            }
                        }
                    } catch(e) {}
                }

                if (!window.__agySessionTitleInterval) {
                    window.__agySessionTitleInterval = setInterval(reportCurrentSession, 350);
                }
                reportCurrentSession();

                // 4. Quan sát thay đổi DOM để kích hoạt cập nhật ngầm
                if (!window.__agyRealtimeDomWatcher) {
                    window.__agyRealtimeDomWatcher = new MutationObserver(() => {
                        reportCurrentSession();
                    });
                    window.__agyRealtimeDomWatcher.observe(document.body, { childList: true, subtree: true });
                }
            } catch(err) {
                console.error('[AGY] SessionScript Error:', err);
            }
        })();
    """.trimIndent()
}
