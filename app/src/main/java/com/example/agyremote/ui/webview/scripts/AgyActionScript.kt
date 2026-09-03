package com.example.agyremote.ui.webview.scripts

/**
 * Module độc lập quản lý việc hiển thị Thẻ Action của Agent (Tool Actions / Terminal blocks).
 * Được bọc trong try-catch riêng biệt, hoàn toàn không gây ảnh hưởng đến các tính năng khác.
 */
object AgyActionScript {
    fun getScript(): String = """
        (function() {
            try {
                // CSS bảo đảm các thẻ tool action luôn hiển thị với viền và nền terminal chuẩn
                if (!document.getElementById('agy-action-style')) {
                    const actionStyle = document.createElement('style');
                    actionStyle.id = 'agy-action-style';
                    actionStyle.textContent = `
                        [data-testid="worked-for-collapsible"],
                        .tool-viewer-card,
                        [class*="worked-for"],
                        [class*="tool-action"] {
                            display: block !important;
                            visibility: visible !important;
                            opacity: 1 !important;
                            max-width: 100% !important;
                        }
                        .tool-viewer-card {
                            border: 1px solid rgba(148, 163, 184, 0.25) !important;
                            border-radius: 8px !important;
                            margin-top: 5px !important;
                            margin-bottom: 5px !important;
                            background-color: rgba(30, 41, 59, 0.45) !important;
                        }
                    `;
                    document.head.appendChild(actionStyle);
                }

                // Tự động mở rộng các khối collapsible chứa tool actions
                function expandToolActions() {
                    try {
                        const triggers = document.querySelectorAll('[data-testid="worked-for-collapsible"], [class*="worked-for"] button, button[aria-expanded="false"]');
                        triggers.forEach(btn => {
                            const text = (btn.innerText || btn.textContent || '').trim();
                            if (text.includes('Worked for') || text.includes('Generation Steps') || text.includes('step') || text.includes('action')) {
                                const actualBtn = btn.tagName === 'BUTTON' ? btn : (btn.querySelector('button, [role="button"]') || btn);
                                if (actualBtn && actualBtn.getAttribute('aria-expanded') !== 'true') {
                                    actualBtn.click();
                                }
                            }
                        });

                        const toolCards = document.querySelectorAll('.tool-viewer-card');
                        toolCards.forEach(card => {
                            card.style.display = 'block';
                            card.style.visibility = 'visible';
                        });
                    } catch(e) {}
                }

                if (!window.__agyExpandActionsInterval) {
                    window.__agyExpandActionsInterval = setInterval(expandToolActions, 600);
                }
                expandToolActions();
            } catch(err) {
                console.error('[AGY] ActionScript Error:', err);
            }
        })();
    """.trimIndent()

    /**
     * Script kích hoạt bung mở ngay lập tức khi người dùng bấm nút < >
     */
    fun getForceExpandScript(): String = """
        (function() {
            try {
                const triggers = document.querySelectorAll('[data-testid="worked-for-collapsible"], [class*="worked-for"] button, button[aria-expanded="false"]');
                triggers.forEach(btn => {
                    const actualBtn = btn.tagName === 'BUTTON' ? btn : (btn.querySelector('button, [role="button"]') || btn);
                    if (actualBtn && actualBtn.getAttribute('aria-expanded') !== 'true') {
                        actualBtn.click();
                    }
                });
                const toolCards = document.querySelectorAll('.tool-viewer-card');
                toolCards.forEach(card => {
                    card.style.display = 'block';
                    card.style.visibility = 'visible';
                });
            } catch(e) {}
        })();
    """.trimIndent()
}
