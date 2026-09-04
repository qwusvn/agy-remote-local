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

                function handleKeyDown(e) {
                    if (e.key !== 'Enter' && e.keyCode !== 13) return;

                    const target = e.target;
                    if (!target) return;

                    const isTextArea = target.tagName === 'TEXTAREA' || (target.tagName === 'INPUT' && target.type === 'text');
                    const isContentEditable = target.isContentEditable || target.getAttribute('contenteditable') === 'true';

                    if (!isTextArea && !isContentEditable) return;

                    if (isTextArea) {
                        const val = target.value;
                        const start = target.selectionStart;
                        const end = target.selectionEnd;

                        const textBefore = val.substring(0, start);
                        const textAfter = val.substring(end);

                        // Tìm dòng hiện tại mà con trỏ đang đứng
                        const lastNewLineIdx = textBefore.lastIndexOf('\n');
                        const currentLine = lastNewLineIdx === -1 ? textBefore : textBefore.substring(lastNewLineIdx + 1);

                        // Kiểm tra các định dạng danh sách
                        const bulletMatch = currentLine.match(/^(\s*[-*+]\s+)/);
                        const numberMatch = currentLine.match(/^(\s*)(\d+)(\.\s+)/);
                        const checkMatch = currentLine.match(/^(\s*[-*+]\s+\[[\sxX]?\]\s+)/);

                        // 1. Trường hợp người dùng nhấn Enter trên dòng chỉ có bullet trống (muốn thoát danh sách)
                        if (currentLine.match(/^(\s*[-*+]\s+|\s*\d+\.\s+|\s*[-*+]\s+\[[\sxX]?\]\s+)$/)) {
                            e.preventDefault();
                            e.stopPropagation();

                            const lineStart = lastNewLineIdx === -1 ? 0 : lastNewLineIdx + 1;
                            target.value = val.substring(0, lineStart) + textAfter;
                            target.selectionStart = target.selectionEnd = lineStart;

                            target.dispatchEvent(new Event('input', { bubbles: true }));
                            target.dispatchEvent(new Event('change', { bubbles: true }));
                            return;
                        }

                        // 2. Trường hợp dòng có nội dung sau bullet -> Tự động sinh bullet cho dòng tiếp theo
                        let nextPrefix = null;
                        if (checkMatch) {
                            nextPrefix = checkMatch[1].replace(/\[[xX]\]/, '[ ]');
                        } else if (numberMatch) {
                            const nextNum = parseInt(numberMatch[2], 10) + 1;
                            nextPrefix = numberMatch[1] + nextNum + numberMatch[3];
                        } else if (bulletMatch) {
                            nextPrefix = bulletMatch[1]; // ví dụ: "- "
                        }

                        if (nextPrefix) {
                            e.preventDefault();
                            e.stopPropagation();

                            const insertion = '\n' + nextPrefix;

                            let inserted = false;
                            try {
                                inserted = document.execCommand('insertText', false, insertion);
                            } catch (err) {
                                inserted = false;
                            }

                            if (!inserted) {
                                target.value = textBefore + insertion + textAfter;
                                const newPos = start + insertion.length;
                                target.selectionStart = target.selectionEnd = newPos;

                                target.dispatchEvent(new Event('input', { bubbles: true }));
                                target.dispatchEvent(new Event('change', { bubbles: true }));
                            }

                            // Tự động điều chỉnh chiều cao của textarea nếu có auto-grow
                            try {
                                target.style.height = 'auto';
                                target.style.height = target.scrollHeight + 'px';
                            } catch(e) {}
                            return;
                        }
                    } else if (isContentEditable) {
                        const sel = window.getSelection();
                        if (!sel || !sel.rangeCount) return;
                        const range = sel.getRangeAt(0);
                        const node = sel.anchorNode;
                        if (!node) return;

                        const text = node.textContent || '';
                        const offset = sel.anchorOffset;
                        const textBefore = text.substring(0, offset);

                        const lastNewLineIdx = textBefore.lastIndexOf('\n');
                        const currentLine = lastNewLineIdx === -1 ? textBefore : textBefore.substring(lastNewLineIdx + 1);

                        const bulletMatch = currentLine.match(/^(\s*[-*+]\s+)/);
                        const numberMatch = currentLine.match(/^(\s*)(\d+)(\.\s+)/);

                        let nextPrefix = null;
                        if (numberMatch) {
                            const nextNum = parseInt(numberMatch[2], 10) + 1;
                            nextPrefix = numberMatch[1] + nextNum + numberMatch[3];
                        } else if (bulletMatch) {
                            nextPrefix = bulletMatch[1];
                        }

                        if (nextPrefix) {
                            e.preventDefault();
                            e.stopPropagation();
                            document.execCommand('insertText', false, '\n' + nextPrefix);
                        }
                    }
                }

                // Đăng ký listener ở capture phase (true) để ưu tiên xử lý trước mọi framework
                document.addEventListener('keydown', handleKeyDown, true);
                console.log('[AGY] Smart Input Formatter loaded successfully.');
            } catch(e) {
                console.error('[AGY] Error in AgyInputScript:', e);
            }
        })();
    """.trimIndent()
}
