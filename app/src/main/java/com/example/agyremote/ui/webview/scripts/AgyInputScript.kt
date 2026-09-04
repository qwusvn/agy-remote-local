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

                let lastHandledTimestamp = 0;

                // Lắng nghe và đính kèm vào document
                function attachListeners(doc) {
                    if (!doc || doc.__agySmartInputAttached) return;
                    doc.__agySmartInputAttached = true;

                    // 1. Bắt sự kiện 'beforeinput' (bàn phím ảo Android / Gboard / IME)
                    doc.addEventListener('beforeinput', function(e) {
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

                    // 2. Bắt sự kiện 'keydown' (bàn phím cứng / phím Enter / keyevent 66 ADB)
                    doc.addEventListener('keydown', function(e) {
                        if (Date.now() - lastHandledTimestamp < 300) {
                            if (e.key === 'Enter' || e.keyCode === 13) {
                                e.preventDefault();
                                e.stopPropagation();
                            }
                            return;
                        }

                        if (e.key !== 'Enter' && e.keyCode !== 13) return;

                        const handled = processSmartInputFormatting(e.target);
                        if (handled) {
                            e.preventDefault();
                            e.stopPropagation();
                            lastHandledTimestamp = Date.now();
                        }
                    }, true);
                }

                // Gắn vào document chính
                attachListeners(document);

                // Quét và gắn vào các sub-iframes nếu có
                function scanIframes() {
                    try {
                        const iframes = document.querySelectorAll('iframe');
                        iframes.forEach(iframe => {
                            try {
                                const subDoc = iframe.contentDocument || iframe.contentWindow?.document;
                                if (subDoc) attachListeners(subDoc);
                            } catch(err) {}
                        });
                    } catch(e) {}
                }

                scanIframes();
                const observer = new MutationObserver(() => scanIframes());
                observer.observe(document.documentElement || document.body, { childList: true, subtree: true });

                console.log('[AGY] Smart Input Formatter (Lexical & Textarea Auto-Bullet) loaded successfully.');
            } catch(e) {
                console.error('[AGY] Error in AgyInputScript:', e);
            }
        })();
    """.trimIndent()
}
