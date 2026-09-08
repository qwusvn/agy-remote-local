package com.example.agyremote.ui.webview.scripts

/**
 * Module quản lý các tiện ích nhập liệu cho WebView (AGY Remote Input Utilities):
 * - Kích hoạt gửi tin nhắn: window.__agyTriggerSend()
 * - Chèn ký tự lập trình từ Accessory Coding Bar: window.__agyInsertText()
 * - Xóa trắng khung nhập: window.__agyClearInput()
 * - Mở trình chọn ảnh khi chạm Media
 *
 * KHÔNG can thiệp hoặc chặn sự kiện beforeinput / keydown của bàn phím ảo di động,
 * đảm bảo 100% độ mượt, tương thích tuyệt đối với bộ gõ tiếng Việt Telex và Lexical Editor.
 */
object AgyInputScript {
    fun getScript(): String = """
        (function() {
            try {
                if (window.__agyInputScriptLoaded) return;
                window.__agyInputScriptLoaded = true;

                let lastSendTimestamp = 0;

                // 1. Kích hoạt gửi tin nhắn (dùng cho phím Enter hoặc nút Send trên TopStatusBar)
                window.__agyTriggerSend = function() {
                    try {
                        const now = Date.now();
                        if (now - lastSendTimestamp < 250) {
                            return true;
                        }
                        lastSendTimestamp = now;

                        const sendBtn = document.querySelector(
                            'button[data-testid="send-button"], ' +
                            'button[data-tooltip-id="input-send-button-send-tooltip"], ' +
                            'button[aria-label*="Send message" i], ' +
                            'button[aria-label*="Send" i], ' +
                            'button[aria-label*="Gửi" i]'
                        );
                        if (sendBtn && !sendBtn.disabled) {
                            const propKey = Object.keys(sendBtn).find(k => k.startsWith('__reactProps'));
                            if (propKey && sendBtn[propKey] && typeof sendBtn[propKey].onClick === 'function') {
                                try { sendBtn[propKey].onClick({ preventDefault: () => {}, stopPropagation: () => {} }); } catch(e) {}
                            }
                            sendBtn.click();
                            return true;
                        }
                    } catch(err) {
                        console.error('[AGY] __agyTriggerSend error:', err);
                    }
                    return false;
                };

                // 2. Chèn ký tự lập trình vào editor hiện tại (dùng cho Accessory Coding Bar)
                window.__agyInsertText = function(text) {
                    try {
                        const target = document.querySelector('[contenteditable="true"]') || 
                                       document.querySelector('textarea, input[type="text"]');
                        if (!target) return false;
                        target.focus();

                        if (target.isContentEditable || target.getAttribute('contenteditable') === 'true') {
                            const success = document.execCommand('insertText', false, text);
                            if (!success) {
                                try {
                                    const dt = new DataTransfer();
                                    dt.setData('text/plain', text);
                                    target.dispatchEvent(new ClipboardEvent('paste', { clipboardData: dt, bubbles: true, cancelable: true }));
                                } catch(e) {}
                            }
                            return true;
                        } else {
                            const val = target.value || '';
                            const start = target.selectionStart ?? val.length;
                            const end = target.selectionEnd ?? val.length;
                            target.value = val.substring(0, start) + text + val.substring(end);
                            target.selectionStart = target.selectionEnd = start + text.length;
                            target.dispatchEvent(new Event('input', { bubbles: true }));
                            return true;
                        }
                    } catch(e) {
                        console.error('[AGY] __agyInsertText error:', e);
                    }
                    return false;
                };

                // 3. Xóa trắng ô nhập
                window.__agyClearInput = function() {
                    try {
                        const editor = document.querySelector('[contenteditable="true"]');
                        if (editor) {
                            editor.focus();
                            document.execCommand('selectAll', false, null);
                            document.execCommand('delete', false, null);
                            return true;
                        }
                        const textarea = document.querySelector('textarea, input[type="text"]');
                        if (textarea) {
                            textarea.value = '';
                            textarea.dispatchEvent(new Event('input', { bubbles: true }));
                            return true;
                        }
                    } catch(e) {
                        console.error('[AGY] __agyClearInput error:', e);
                    }
                    return false;
                };

                // 4. Bắt sự kiện chọn "Media" trong menu (+) của Antigravity
                document.addEventListener('click', function(e) {
                    const target = e.target;
                    if (!target) return;
                    const mediaItem = target.closest('button, [role="menuitem"], div');
                    if (mediaItem) {
                        const txt = (mediaItem.innerText || '').trim();
                        if (txt === 'Media' || txt === 'Hình ảnh' || txt === 'Tệp' || txt === 'Upload') {
                            if (window.AgyAndroidBridge && window.AgyAndroidBridge.openImagePicker) {
                                e.preventDefault();
                                e.stopPropagation();
                                window.AgyAndroidBridge.openImagePicker();
                                try {
                                    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));
                                } catch(err) {}
                            }
                        }
                    }
                }, true);

                console.log('[AGY] Clean Input Utilities loaded successfully (zero keyboard hijacking).');
            } catch(e) {
                console.error('[AGY] Error in AgyInputScript:', e);
            }
        })();
    """.trimIndent()
}
