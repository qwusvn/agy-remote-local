package com.example.agyremote.ui.webview.scripts

/**
 * Module độc lập quản lý Dark Theme, CSS styling và tỷ lệ hiển thị cho WebView.
 * Được bọc trong try-catch riêng biệt, hoàn toàn không gây ảnh hưởng đến các tính năng khác.
 */
object AgyThemeScript {
    fun getScript(): String = """
        (function() {
            try {
                if (window.__agyThemeInjected) return;
                window.__agyThemeInjected = true;

                try {
                    localStorage.setItem('theme', 'dark');
                    localStorage.setItem('color-theme', 'dark');
                    localStorage.setItem('chakra-ui-color-mode', 'dark');
                } catch(e) {}

                document.documentElement.classList.add('dark');
                document.documentElement.setAttribute('data-theme', 'dark');
                document.documentElement.style.colorScheme = 'dark';

                if (!document.getElementById('agy-custom-style')) {
                    const style = document.createElement('style');
                    style.id = 'agy-custom-style';
                    style.textContent = `
                        html, body {
                            background-color: #0f172a !important;
                            color: #f8fafc !important;
                            -webkit-tap-highlight-color: transparent !important;
                            touch-action: manipulation;
                        }
                        main, [role="main"], #root, [class*="app-container"] {
                            transition: opacity 0.2s cubic-bezier(0.4, 0, 0.2, 1), transform 0.22s cubic-bezier(0.4, 0, 0.2, 1) !important;
                        }
                        /* Tối ưu hóa kích thước nút Send và Add context trên di động */
                        button[data-testid="send-button"],
                        button[data-tooltip-id="input-send-button-send-tooltip"],
                        button[aria-label*="Send" i] {
                            min-width: 44px !important;
                            min-height: 44px !important;
                            width: 44px !important;
                            height: 44px !important;
                            padding: 8px !important;
                            touch-action: manipulation !important;
                            cursor: pointer !important;
                            pointer-events: auto !important;
                            -webkit-tap-highlight-color: rgba(59, 130, 246, 0.4) !important;
                        }
                        button[aria-label="Add context"] {
                            min-width: 40px !important;
                            min-height: 40px !important;
                            touch-action: manipulation !important;
                            cursor: pointer !important;
                        }
                        [contenteditable="true"] {
                            touch-action: manipulation !important;
                            -webkit-user-select: text !important;
                            user-select: text !important;
                        }
                    `;
                    document.head.appendChild(style);
                }
            } catch(err) {
                console.error('[AGY] ThemeScript Error:', err);
            }
        })();
    """.trimIndent()
}
