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
                // Biến trạng thái phiên và tác vụ
                let lastReportedTitle = '';
                let lastReportedHref = '';
                let lastWorkingState = false;
                let absentCount = 0;

                // Hàm hủy/dừng Agent khẩn cấp (gọi từ nút Stop trên TopStatusBar)
                window.__agyTriggerStop = function() {
                    try {
                        const cancelBtn = document.querySelector(
                            'button[data-tooltip-id="input-send-button-cancel-tooltip"], ' +
                            'button[aria-label*="Cancel (" i], ' +
                            'button[aria-label*="Cancel(" i], ' +
                            'button[aria-label="Cancel prompt" i], ' +
                            'button[aria-label="Stop generation" i], ' +
                            'button[aria-label*="Dừng" i], ' +
                            'button[aria-label*="Hủy" i], ' +
                            'button[data-testid="subagent-stop"]'
                        );
                        if (cancelBtn) {
                            const btn = cancelBtn.tagName === 'BUTTON' ? cancelBtn : cancelBtn.closest('button');
                            if (btn) {
                                const propKey = Object.keys(btn).find(k => k.startsWith('__reactProps'));
                                if (propKey && btn[propKey] && typeof btn[propKey].onClick === 'function') {
                                    try { btn[propKey].onClick({ preventDefault: () => {}, stopPropagation: () => {} }); } catch(e) {}
                                }
                                btn.click();
                                return true;
                            }
                        }
                    } catch(e) {
                        console.error('[AGY] __agyTriggerStop error:', e);
                    }
                    return false;
                };

                // 1. Giám sát trạng thái Agent isWorking bằng selector chuẩn xác Antigravity
                function isAgentActive() {
                    try {
                        const path = window.location.pathname;
                        // Nếu không phải trang cuộc trò chuyện (/c/...), tuyệt đối không bao giờ báo isWorking
                        if (!path.startsWith('/c/')) {
                            return false;
                        }

                        // Tìm nút Cancel trong ô nhập prompt ở đáy màn hình cuộc trò chuyện hiện tại
                        // Khi Agent đang suy nghĩ / sinh câu trả lời trong phiên này, nút Send biến thành nút Cancel
                        const cancelPromptBtn = document.querySelector(
                            'button[data-tooltip-id="input-send-button-cancel-tooltip"], ' +
                            'button[aria-label="Cancel prompt" i], ' +
                            'button[aria-label*="Cancel (" i], ' +
                            'button[aria-label*="Cancel(" i], ' +
                            'button[aria-label="Stop generation" i], ' +
                            'button[aria-label*="Dừng" i], ' +
                            'button[aria-label*="Hủy" i], ' +
                            'button[data-testid="subagent-stop"], ' +
                            'div[data-testid="send-button-pending"]'
                        );
                        if (cancelPromptBtn) return true;

                        return false;
                    } catch(e) {
                        return false;
                    }
                }

                // Trích xuất kết luận cuối cùng của Agent từ DOM cuộc trò chuyện
                function extractFinalConclusion() {
                    try {
                        const articles = document.querySelectorAll('div[role="article"][aria-label="Agent response"], div[role="article"]');
                        if (!articles || articles.length === 0) return '';
                        const lastArt = articles[articles.length - 1];
                        
                        const clone = lastArt.cloneNode(true);
                        // Loại bỏ các khối công cụ, terminal và collapsible phụ
                        const removable = clone.querySelectorAll(
                            '[data-testid="worked-for-collapsible"], [data-testid^="tool-"], [class*="collapsible"], button, svg, [role="button"], pre'
                        );
                        for (let el of removable) {
                            el.parentElement?.removeChild(el);
                        }
                        
                        let text = clone.innerText || '';
                        const lines = text.split('\n')
                            .map(l => l.trim())
                            .filter(l => l.length > 0 && 
                                !l.startsWith('Worked for') && 
                                !l.startsWith('Ran ') && 
                                !l.startsWith('Edited ') && 
                                !l.startsWith('Explored ') &&
                                !l.startsWith('Created At:') &&
                                !l.startsWith('Completed At:') &&
                                !l.startsWith('Task:') &&
                                !l.startsWith('The command exited') &&
                                !l.startsWith('Output:') &&
                                !l.startsWith('Status:') &&
                                !l.startsWith('Log:') &&
                                !l.includes('task-')
                            );
                        
                        if (lines.length > 0) {
                            let summary = lines.slice(0, 3).join(' ');
                            if (summary.length > 200) {
                                summary = summary.substring(0, 197) + '...';
                            }
                            return summary;
                        }
                    } catch(e) {}
                    return '';
                }

                function checkAgentStatus() {
                    try {
                        const path = window.location.pathname;
                        const isWorking = isAgentActive();

                        if (isWorking) {
                            absentCount = 0;
                            if (lastWorkingState !== true) {
                                lastWorkingState = true;
                                if (window.AgyAndroidBridge && window.AgyAndroidBridge.reportWorkingStatus) {
                                    window.AgyAndroidBridge.reportWorkingStatus(true, path);
                                }
                            }
                        } else {
                            if (lastWorkingState !== false) {
                                absentCount++;
                                // 2 chu kỳ không thấy dấu hiệu làm việc (2 * 400ms = 800ms)
                                // hoặc vừa chuyển trang mới (lastWorkingState === null)
                                if (absentCount >= 2 || lastWorkingState === null) {
                                    lastWorkingState = false;
                                    absentCount = 0;

                                    const conclusion = extractFinalConclusion();
                                    const finalMsg = (conclusion && conclusion.length >= 2) ? conclusion : 'Agent đã hoàn tất câu trả lời';

                                    if (window.AgyAndroidBridge) {
                                        if (window.AgyAndroidBridge.reportWorkingStatus) {
                                            window.AgyAndroidBridge.reportWorkingStatus(false, path);
                                        }
                                        if (window.AgyAndroidBridge.notifyTaskDone && path.startsWith('/c/')) {
                                            window.AgyAndroidBridge.notifyTaskDone(
                                                lastReportedTitle || 'Cuộc trò chuyện Antigravity',
                                                finalMsg
                                            );
                                        }
                                    }
                                }
                            } else {
                                absentCount = 0;
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
                    window.__agyTrackInterval = setInterval(trackActiveConvo, 1000);
                }
                trackActiveConvo();

                // 3. Trích xuất tiêu đề phiên thực tế cho Android Tab
                function reportCurrentSession() {
                    try {
                        const p = window.location.pathname;
                        let title = '';
                        if (p === '/' || p === '') {
                            title = 'Dự án / Phiên';
                        } else if (p.startsWith('/c/')) {
                            // Ưu tiên 0: Lấy từ document.title
                            if (document.title && document.title.trim() && !document.title.trim().startsWith('Antigravity') && document.title.trim() !== 'about:blank') {
                                title = document.title.trim();
                            }

                            // Ưu tiên 1: Header title span hiển thị bên cạnh nút quay lại
                            if (!title) {
                                const headerSpan = document.querySelector('header span.truncate, span.truncate.inline-block, [class*="truncate"][class*="inline-block"]');
                                if (headerSpan && headerSpan.innerText) {
                                    const text = headerSpan.innerText.trim().split('\n')[0];
                                    if (text && text.length > 1 && !text.startsWith('Antigravity')) {
                                        title = text;
                                    }
                                }
                            }

                            // Ưu tiên 2: Lấy từ sidebar item của chính phiên này
                            if (!title) {
                                const sidebarLink = document.querySelector('a[href="' + p + '"], a[href^="' + p + '"]');
                                if (sidebarLink && sidebarLink.innerText) {
                                    const text = sidebarLink.innerText.trim().split('\n')[0];
                                    if (text && text.length > 1 && !text.startsWith('Antigravity')) {
                                        title = text;
                                    }
                                }
                            }

                            // Ưu tiên 3: Lấy từ active conversation item
                            if (!title) {
                                const activeItem = document.querySelector('[class*="conversationItem"][class*="active"], [aria-selected="true"] [class*="title"], [data-active="true"]');
                                if (activeItem && activeItem.innerText) {
                                    const text = activeItem.innerText.trim().split('\n')[0];
                                    if (text && text.length > 1 && !text.startsWith('Antigravity')) {
                                        title = text;
                                    }
                                }
                            }

                            // Ưu tiên 4: Lấy từ header title
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

                            // Ưu tiên 5: Lấy từ câu hỏi đầu tiên của người dùng
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

                        const currentHref = window.location.href;
                        if (title && (title !== lastReportedTitle || currentHref !== lastReportedHref)) {
                            lastReportedTitle = title;
                            lastReportedHref = currentHref;
                            if (window.AgyAndroidBridge && window.AgyAndroidBridge.reportSessionInfo) {
                                window.AgyAndroidBridge.reportSessionInfo(currentHref, title);
                            }
                        }
                    } catch(e) {}
                }

                // Cung cấp hàm ép buộc cập nhật thông tin phiên ngay lập tức
                window.agyForceReportSession = function() {
                    lastReportedTitle = '';
                    lastReportedHref = '';
                    reportCurrentSession();
                };

                if (!window.__agySessionTitleInterval) {
                    window.__agySessionTitleInterval = setInterval(reportCurrentSession, 1000);
                }
                reportCurrentSession();

                // 4. Quan sát thay đổi DOM với debounce để tránh giật lag thread JS
                if (!window.__agyRealtimeDomWatcher) {
                    let domDebounceTimer = null;
                    window.__agyRealtimeDomWatcher = new MutationObserver(() => {
                        if (domDebounceTimer) return;
                        domDebounceTimer = setTimeout(() => {
                            domDebounceTimer = null;
                            reportCurrentSession();
                        }, 1000);
                    });
                    window.__agyRealtimeDomWatcher.observe(document.body, { childList: true, subtree: true });
                }

                // 4.1. Tự động đóng sidebar drawer khi chọn một cuộc trò chuyện từ danh sách
                if (!window.__agyLinkClickListener) {
                    window.__agyLinkClickListener = true;
                    document.addEventListener('click', function(e) {
                        const link = e.target && e.target.closest && e.target.closest('a[href*="/c/"]');
                        if (link) {
                            setTimeout(() => {
                                const openSheet = document.querySelector('div[role="dialog"][data-state="open"]');
                                if (openSheet) {
                                    const closeBtn = document.querySelector('button[aria-label*="menu" i][data-state="open"], button[data-state="open"], div[data-state="open"].fixed.inset-0');
                                    if (closeBtn) closeBtn.click();
                                }
                            }, 60);
                        }
                    }, true);
                }

                // 5. Hàm điều hướng SPA tức thì (0ms không reload trang)
                window.agySpaNavigate = function(targetPathOrUrl) {
                    try {
                        let targetPath = targetPathOrUrl;
                        try {
                            if (targetPathOrUrl.startsWith('http://') || targetPathOrUrl.startsWith('https://')) {
                                const u = new URL(targetPathOrUrl);
                                targetPath = u.pathname + u.search + u.hash;
                            }
                        } catch(e) {}

                        if (!targetPath) targetPath = '/';

                        const currentPath = window.location.pathname + window.location.search + window.location.hash;
                        if (currentPath === targetPath) {
                            return true;
                        }

                        // Đóng auxiliary pane nếu đang mở
                        try {
                            const auxBtn = document.querySelector('[data-testid="mobile-toggle-aux-sidebar"]') || 
                                           document.querySelector('button[aria-label="Toggle Auxiliary Pane"]');
                            const hasAux = document.querySelector('[data-testid="changed-file-row"]') || 
                                           document.querySelector('[data-testid="aux-panel-plus-dropdown-trigger"]');
                            if (hasAux && auxBtn) auxBtn.click();
                        } catch(e) {}

                        // Đóng mobile drawer / sidebar nếu đang mở
                        try {
                            const openSheet = document.querySelector('div[role="dialog"][data-state="open"]');
                            if (openSheet) {
                                const closeBtn = document.querySelector('button[aria-label*="menu" i][data-state="open"], button[data-state="open"], div[data-state="open"].fixed.inset-0');
                                if (closeBtn) closeBtn.click();
                            }
                        } catch(e) {}

                        // Reset trạng thái làm việc để đánh giá chính xác phiên vừa chuyển tới
                        lastWorkingState = null;
                        absentCount = 0;
                        setTimeout(() => {
                            checkAgentStatus();
                            reportCurrentSession();
                        }, 100);

                        const r = window.__TSR_ROUTER__;
                        if (r && typeof r.navigate === 'function') {
                            if (targetPath.startsWith('/c/')) {
                                const cid = targetPath.replace('/c/', '').split('?')[0].split('#')[0];
                                r.navigate({ to: '/c/${'$'}cascadeId', params: { cascadeId: cid } });
                                return true;
                            } else if (targetPath === '/' || targetPath === '') {
                                r.navigate({ to: '/' });
                                return true;
                            } else if (targetPath.startsWith('/history')) {
                                r.navigate({ to: '/history' });
                                return true;
                            } else {
                                r.navigate({ to: targetPath });
                                return true;
                            }
                        }

                        // Fallback: Tìm link a trong DOM
                        const domLink = document.querySelector('a[href="' + targetPath + '"]');
                        if (domLink) {
                            domLink.click();
                            setTimeout(reportCurrentSession, 80);
                            return true;
                        }

                        // Fallback: History API popstate
                        window.history.pushState(null, '', targetPath);
                        window.dispatchEvent(new PopStateEvent('popstate'));
                        setTimeout(reportCurrentSession, 80);
                        return true;
                    } catch(err) {
                        console.error('[AGY] SPA Navigation error:', err);
                        window.location.href = targetPathOrUrl;
                        return false;
                    }
                };

                // 6. Hàm bơm ảnh Base64 trực tiếp vào khung chat (hỗ trợ Clipboard & File Upload)
                window.agyInjectImage = function(base64Data, mimeType, fileName) {
                    try {
                        const type = mimeType || 'image/jpeg';
                        const name = fileName || ('upload_' + Date.now() + '.jpg');

                        const byteCharacters = atob(base64Data);
                        const byteNumbers = new Array(byteCharacters.length);
                        for (let i = 0; i < byteCharacters.length; i++) {
                            byteNumbers[i] = byteCharacters.charCodeAt(i);
                        }
                        const byteArray = new Uint8Array(byteNumbers);
                        const blob = new Blob([byteArray], { type: type });
                        const file = new File([blob], name, { type: type, lastModified: Date.now() });

                        // Cách 1: Gắn vào input[type="file"] của Antigravity
                        const fileInputs = document.querySelectorAll('input[type="file"]');
                        for (const input of fileInputs) {
                            try {
                                const dt = new DataTransfer();
                                dt.items.add(file);
                                input.files = dt.files;
                                input.dispatchEvent(new Event('change', { bubbles: true }));
                                console.log('[AGY] Image injected into input[type=file] successfully.');
                                return true;
                            } catch(e) {}
                        }

                        // Cách 2: Dispatch sự kiện paste vào Lexical Editor
                        const editor = document.querySelector('[contenteditable="true"], textarea');
                        if (editor) {
                            try {
                                const dt = new DataTransfer();
                                dt.items.add(file);
                                const pasteEvent = new ClipboardEvent('paste', {
                                    clipboardData: dt,
                                    bubbles: true,
                                    cancelable: true
                                });
                                editor.dispatchEvent(pasteEvent);
                                console.log('[AGY] Image injected via paste event successfully.');
                                return true;
                            } catch(e) {}
                        }

                        // Cách 3: Dispatch sự kiện drop lên window
                        try {
                            const dt = new DataTransfer();
                            dt.items.add(file);
                            const dropEvent = new DragEvent('drop', {
                                dataTransfer: dt,
                                bubbles: true,
                                cancelable: true
                            });
                            window.dispatchEvent(dropEvent);
                            console.log('[AGY] Image injected via drop event successfully.');
                            return true;
                        } catch(e) {}

                        return false;
                    } catch(err) {
                        console.error('[AGY] Error in agyInjectImage:', err);
                        return false;
                    }
                };
            } catch(err) {
                console.error('[AGY] SessionScript Error:', err);
            }
        })();
    """.trimIndent()
}
