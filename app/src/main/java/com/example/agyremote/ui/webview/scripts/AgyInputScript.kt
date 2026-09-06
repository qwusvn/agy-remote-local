package com.example.agyremote.ui.webview.scripts

/**
 * Module độc lập quản lý tính năng gõ thông minh cho Khung Nhập Văn Bản (Smart Input Formatting).
 * - Tự động duy trì định dạng danh sách (bullet list: "- ", "* ", "+ ", "1. ", "- [ ] ") khi xuống dòng.
 * - Thoát danh sách (xóa bullet) khi nhấn Enter liên tiếp trên dòng trống.
 * - Hỗ trợ cả <textarea>, <input> và [contenteditable="true"].
 */
object AgyInputScript {
    fun getScript(): String = """
        (function() {
            try {
                if (window.__agyInputScriptLoaded) return;
                window.__agyInputScriptLoaded = true;

                // 1. Hàm cập nhật value cho thẻ <textarea> / <input> (tương thích React Synthetic Events)
                function updateNativeTextareaValue(element, newValue, newSelectionPos) {
                    const prototype = Object.getPrototypeOf(element);
                    const descriptor = Object.getOwnPropertyDescriptor(prototype, 'value');
                    if (descriptor && descriptor.set) {
                        descriptor.set.call(element, newValue);
                    } else {
                        element.value = newValue;
                    }
                    if (typeof newSelectionPos === 'number') {
                        try {
                            element.selectionStart = element.selectionEnd = newSelectionPos;
                        } catch(e) {}
                    }
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                    element.dispatchEvent(new Event('change', { bubbles: true }));
                }

                // 2. Hàm chèn text vào Lexical Editor / contenteditable thông qua ClipboardEvent 'paste'
                function insertIntoContentEditable(target, textToInsert) {
                    try {
                        const dt = new DataTransfer();
                        dt.setData('text/plain', textToInsert);
                        const pasteEv = new ClipboardEvent('paste', {
                            clipboardData: dt,
                            bubbles: true,
                            cancelable: true
                        });
                        target.dispatchEvent(pasteEv);
                        return true;
                    } catch(e) {
                        try {
                            return document.execCommand('insertText', false, textToInsert);
                        } catch(err) {
                            return false;
                        }
                    }
                }

                // 3. Xử lý logic gõ thông minh (Auto-Bullet & Exit List)
                function processSmartInputFormatting(target) {
                    if (!target) return false;

                    const isTextArea = target.tagName === 'TEXTAREA' || (target.tagName === 'INPUT' && target.type === 'text');
                    const isContentEditable = target.isContentEditable || target.getAttribute('contenteditable') === 'true';

                    if (!isTextArea && !isContentEditable) return false;

                    let currentLine = '';
                    let isTextareaMode = false;
                    let textBefore = '';
                    let textAfter = '';
                    let startPos = 0;
                    let lastNewLineIdx = -1;

                    if (isTextArea) {
                        isTextareaMode = true;
                        const val = target.value || '';
                        startPos = target.selectionStart ?? val.length;
                        const endPos = target.selectionEnd ?? val.length;

                        textBefore = val.substring(0, startPos);
                        textAfter = val.substring(endPos);

                        lastNewLineIdx = textBefore.lastIndexOf('\n');
                        currentLine = lastNewLineIdx === -1 ? textBefore : textBefore.substring(lastNewLineIdx + 1);
                    } else {
                        // contenteditable / Lexical Editor
                        const sel = window.getSelection();
                        if (!sel || !sel.rangeCount) return false;

                        try {
                            const range = sel.getRangeAt(0);
                            const preCaretRange = range.cloneRange();
                            preCaretRange.selectNodeContents(target);
                            preCaretRange.setEnd(range.endContainer, range.endOffset);
                            textBefore = preCaretRange.toString();

                            lastNewLineIdx = textBefore.lastIndexOf('\n');
                            currentLine = lastNewLineIdx === -1 ? textBefore : textBefore.substring(lastNewLineIdx + 1);
                        } catch(e) {
                            return false;
                        }
                    }

                    // Regex nhận diện các dạng danh sách:
                    // 1. Checkbox: "- [ ] ", "- [x] "
                    const checkMatch = currentLine.match(/^(\s*[-*+]\s+\[[\sxX]?\]\s+)(.*)$/);
                    // 2. Numbered: "1. ", "2. "
                    const numberMatch = currentLine.match(/^(\s*)(\d+)(\.\s+)(.*)$/);
                    // 3. Bullet: "- ", "* ", "+ "
                    const bulletMatch = currentLine.match(/^(\s*[-*+]\s+)(.*)$/);

                    // A. THOÁT DANH SÁCH: Người dùng nhấn Enter khi dòng chỉ có bullet trống
                    const emptyBulletMatch = currentLine.match(/^(\s*[-*+]\s*|\s*\d+\.\s*|\s*[-*+]\s+\[[\sxX]?\]\s*)$/);
                    if (emptyBulletMatch) {
                        if (isTextareaMode) {
                            const lineStart = lastNewLineIdx === -1 ? 0 : lastNewLineIdx + 1;
                            const newVal = target.value.substring(0, lineStart) + textAfter;
                            updateNativeTextareaValue(target, newVal, lineStart);
                        } else {
                            // Trong Lexical / contenteditable: Bôi đen lùi lại toàn bộ bullet trống và delete
                            try {
                                const sel = window.getSelection();
                                if (sel) {
                                    const delCount = currentLine.length;
                                    for (let i = 0; i < delCount; i++) {
                                        sel.modify('extend', 'backward', 'character');
                                    }
                                    document.execCommand('delete');
                                }
                            } catch(e) {
                                document.execCommand('delete');
                            }
                        }
                        return true;
                    }

                    // B. TỰ ĐỘNG SINH BULLET TIẾP THEO: Khi dòng có nội dung sau bullet
                    let nextPrefix = null;
                    if (checkMatch && checkMatch[2].length > 0) {
                        nextPrefix = checkMatch[1].replace(/\[[xX]\]/, '[ ]');
                    } else if (numberMatch && numberMatch[4].length > 0) {
                        const nextNum = parseInt(numberMatch[2], 10) + 1;
                        nextPrefix = numberMatch[1] + nextNum + numberMatch[3];
                    } else if (bulletMatch && bulletMatch[2].length > 0) {
                        nextPrefix = bulletMatch[1]; // Ví dụ: "- "
                    }

                    if (nextPrefix) {
                        if (isTextareaMode) {
                            const insertion = '\n' + nextPrefix;
                            const newVal = textBefore + insertion + textAfter;
                            const newPos = startPos + insertion.length;
                            updateNativeTextareaValue(target, newVal, newPos);

                            try {
                                target.style.height = 'auto';
                                target.style.height = target.scrollHeight + 'px';
                            } catch(e) {}
                        } else {
                            // Lexical / contenteditable: chèn \n + nextPrefix qua paste event
                            insertIntoContentEditable(target, '\n' + nextPrefix);
                        }
                        return true;
                    }

                    return false;
                }

                let lastSendTimestamp = 0;

                // Hàm kích hoạt gửi tin nhắn chuẩn xác cho Antigravity (gọi từ phím Enter hoặc nút TopStatusBar)
                window.__agyTriggerSend = function() {
                    try {
                        const now = Date.now();
                        if (now - lastSendTimestamp < 500) {
                            return true;
                        }
                        lastSendTimestamp = now;

                        const sendBtn = document.querySelector(
                            'button[data-testid="send-button"], ' +
                            'button[data-tooltip-id="input-send-button-send-tooltip"], ' +
                            'button[aria-label*="Send message" i], ' +
                            'button[aria-label*="Send" i]'
                        );
                        if (sendBtn && !sendBtn.disabled) {
                            setTimeout(() => {
                                try { sendBtn.click(); } catch(e) {}
                            }, 10);
                            return true;
                        }
                    } catch(err) {
                        console.error('[AGY] __agyTriggerSend error:', err);
                    }
                    return false;
                };
                // Hàm chèn text vào editor hiện tại (dùng cho Accessory Coding Bar)
                window.__agyInsertText = function(text) {
                    try {
                        const target = document.querySelector('[contenteditable="true"]') || document.querySelector('textarea, input[type="text"]');
                        if (!target) return false;
                        target.focus();
                        if (target.isContentEditable || target.getAttribute('contenteditable') === 'true') {
                            return insertIntoContentEditable(target, text);
                        } else {
                            const val = target.value || '';
                            const start = target.selectionStart ?? val.length;
                            const end = target.selectionEnd ?? val.length;
                            const newVal = val.substring(0, start) + text + val.substring(end);
                            updateNativeTextareaValue(target, newVal, start + text.length);
                            return true;
                        }
                    } catch(e) {
                        console.error('[AGY] __agyInsertText error:', e);
                    }
                    return false;
                };

                // Hàm xóa trắng ô nhập
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
                            updateNativeTextareaValue(textarea, '', 0);
                            return true;
                        }
                    } catch(e) {
                        console.error('[AGY] __agyClearInput error:', e);
                    }
                    return false;
                };

                let lastHandledTimestamp = 0;

                // Lắng nghe và đính kèm vào document
                function attachListeners(doc) {
                    if (!doc || doc.__agySmartInputAttached) return;
                    doc.__agySmartInputAttached = true;

                    // 1. Bắt sự kiện 'beforeinput' (bàn phím ảo Android / Gboard / IME)
                    doc.addEventListener('beforeinput', function(e) {
                        if (e.isComposing) return;
                        const isNewline = e.inputType === 'insertLineBreak' || 
                                          e.inputType === 'insertParagraph' ||
                                          (e.inputType === 'insertText' && (e.data === '\n' || e.data === '\r\n'));

                        if (!isNewline) return;

                        const handled = processSmartInputFormatting(e.target);
                        if (handled) {
                            e.preventDefault();
                            e.stopPropagation();
                            lastHandledTimestamp = Date.now();
                        }
                    }, true);

                    // 2. Bắt sự kiện 'keydown' (bàn phím ảo Android / phím Enter / keyevent 66 ADB)
                    doc.addEventListener('keydown', function(e) {
                        if (e.isComposing || e.keyCode === 229) return;
                        if (e.key !== 'Enter' && e.keyCode !== 13) return;

                        if (Date.now() - lastHandledTimestamp < 300) {
                            e.preventDefault();
                            e.stopPropagation();
                            return;
                        }

                        // A. Ưu tiên logic định dạng danh sách thông minh (- , * , 1. )
                        const handled = processSmartInputFormatting(e.target);
                        if (handled) {
                            e.preventDefault();
                            e.stopPropagation();
                            lastHandledTimestamp = Date.now();
                            return;
                        }

                        // B. Nếu người dùng không giữ phím Shift (Shift+Enter để xuống dòng):
                        // Gửi tin nhắn ngay lập tức nếu nút Send đang được kích hoạt (đã có chữ hoặc ảnh)
                        if (!e.shiftKey) {
                            const sent = window.__agyTriggerSend();
                            if (sent) {
                                e.preventDefault();
                                e.stopPropagation();
                                lastHandledTimestamp = Date.now();
                                console.log('[AGY] Tin nhắn đã được gửi qua phím Enter.');
                                return;
                            }
                        }
                    }, true);

                    // 3. Bắt sự kiện chạm / click vào mục "Media" trong menu Add Context (+)
                    // Tránh lỗi Chromium: "File chooser dialog can only be shown with a user activation"
                    doc.addEventListener('click', function(e) {
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
                                    // Đóng menu Add context
                                    try {
                                        const esc = new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true });
                                        doc.dispatchEvent(esc);
                                    } catch(err) {}
                                }
                            }
                        }
                    }, true);
                }

                // Gắn vào document chính
                attachListeners(document);

                console.log('[AGY] Smart Input Formatter (Lexical & Textarea Auto-Bullet) loaded successfully.');
            } catch(e) {
                console.error('[AGY] Error in AgyInputScript:', e);
            }
        })();
    """.trimIndent()
}
