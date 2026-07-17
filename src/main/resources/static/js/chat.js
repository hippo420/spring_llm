(() => {
    const state = {
        currentSessionId: null,
        pendingFile: null,
    };

    const messagesEl = document.getElementById('messages');
    const sessionListEl = document.getElementById('sessionList');
    const chatForm = document.getElementById('chatForm');
    const messageInput = document.getElementById('messageInput');
    const sendBtn = document.getElementById('sendBtn');
    const newChatBtn = document.getElementById('newChatBtn');
    const fileInput = document.getElementById('fileInput');
    const attachmentChip = document.getElementById('attachmentChip');
    const attachmentName = document.getElementById('attachmentName');
    const removeAttachmentBtn = document.getElementById('removeAttachmentBtn');
    const featureSelect = document.getElementById('featureSelect');

    function el(tag, cls, text) {
        const e = document.createElement(tag);
        if (cls) e.className = cls;
        if (text !== undefined) e.textContent = text;
        return e;
    }

    DOMPurify.addHook('afterSanitizeAttributes', (node) => {
        if (node.tagName === 'A') {
            node.setAttribute('target', '_blank');
            node.setAttribute('rel', 'noopener noreferrer');
        }
    });

    function renderMarkdown(rawText) {
        return DOMPurify.sanitize(marked.parse(rawText ?? ''));
    }

    function clearMessages() {
        messagesEl.innerHTML = '';
        const empty = el('div', 'empty-state', '왼쪽에서 새 대화를 시작하세요.');
        empty.id = 'emptyState';
        messagesEl.appendChild(empty);
    }

    function addMessageBubble(role, content) {
        document.getElementById('emptyState')?.remove();
        const bubble = el('div', `message ${role}`);
        if (role === 'assistant') {
            bubble.innerHTML = renderMarkdown(content);
        } else {
            bubble.textContent = content;
        }
        messagesEl.appendChild(bubble);
        messagesEl.scrollTop = messagesEl.scrollHeight;
        return bubble;
    }

    function addLoadingBubble() {
        document.getElementById('emptyState')?.remove();
        const bubble = el('div', 'message assistant');
        const indicator = el('div', 'typing-indicator');
        indicator.appendChild(el('span'));
        indicator.appendChild(el('span'));
        indicator.appendChild(el('span'));
        bubble.appendChild(indicator);
        messagesEl.appendChild(bubble);
        messagesEl.scrollTop = messagesEl.scrollHeight;
        return bubble;
    }

    async function loadFeatures() {
        const res = await fetch('/api/features');
        const featureOptions = await res.json();
        featureSelect.innerHTML = '';
        featureOptions.forEach((f) => {
            const opt = el('option', null, f.label);
            opt.value = f.value;
            featureSelect.appendChild(opt);
        });
    }

    async function loadSessions() {
        const res = await fetch('/api/sessions');
        const sessions = await res.json();
        renderSessions(sessions);
        return sessions;
    }

    function renderSessions(sessions) {
        sessionListEl.innerHTML = '';
        sessions.forEach((s) => {
            const li = el('li', 'session-item' + (s.id === state.currentSessionId ? ' active' : ''));
            li.dataset.id = s.id;
            li.appendChild(el('span', null, s.title));

            const delBtn = el('button', 'delete-btn', '×');
            delBtn.type = 'button';
            delBtn.setAttribute('aria-label', '대화 삭제');
            delBtn.addEventListener('click', async (e) => {
                e.stopPropagation();
                await fetch(`/api/sessions/${s.id}`, {method: 'DELETE'});
                if (state.currentSessionId === s.id) {
                    state.currentSessionId = null;
                    clearMessages();
                }
                await loadSessions();
            });
            li.appendChild(delBtn);

            li.addEventListener('click', () => selectSession(s.id));
            sessionListEl.appendChild(li);
        });
    }

    async function selectSession(sessionId) {
        state.currentSessionId = sessionId;
        [...sessionListEl.children].forEach((li) => {
            li.classList.toggle('active', li.dataset.id === sessionId);
        });
        const res = await fetch(`/api/sessions/${sessionId}/messages`);
        const msgs = await res.json();
        messagesEl.innerHTML = '';
        if (msgs.length === 0) {
            clearMessages();
        } else {
            msgs.forEach((m) => addMessageBubble(m.role, m.content));
        }
    }

    async function createSession() {
        const res = await fetch('/api/sessions', {method: 'POST'});
        const session = await res.json();
        state.currentSessionId = session.id;
        clearMessages();
        await loadSessions();
        return session;
    }

    async function uploadAttachment(sessionId, file) {
        const formData = new FormData();
        formData.append('file', file);
        const res = await fetch(`/api/sessions/${sessionId}/attachments`, {method: 'POST', body: formData});
        if (!res.ok) {
            let detail = '';
            try {
                detail = (await res.json()).message ?? '';
            } catch (ignored) { /* 본문 없는 에러 응답 */ }
            throw new Error(detail || `첨부파일 업로드 실패 (${res.status})`);
        }
        return res.json();
    }

    function setPendingFile(file) {
        state.pendingFile = file;
        if (file) {
            attachmentName.textContent = file.name;
            attachmentChip.hidden = false;
        } else {
            attachmentChip.hidden = true;
            fileInput.value = '';
        }
    }

    // SSE 이벤트 프로토콜 (docs/07-thinking-tool-status-design.md 4절):
    //   기본(무명) 이벤트 = 답변 토큰, event:thinking = 추론 델타,
    //   event:status = 서버 진행 상태 문구("문서 검색 중..." 등),
    //   event:sources = 참조 문서 파일명 목록(개행 구분), event:tool = 도구 상태 JSON.
    async function streamChat(sessionId, content, feature, {onToken, onThinking, onStatus, onSources, onTool, onDone, onError}) {
        let res;
        try {
            res = await fetch(`/api/sessions/${sessionId}/messages/stream`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({content, feature}),
            });
        } catch (err) {
            onError(err);
            return;
        }
        if (!res.ok || !res.body) {
            onError(new Error(`요청 실패 (${res.status})`));
            return;
        }

        const reader = res.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';

        while (true) {
            const {value, done} = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, {stream: true});

            let boundary;
            while ((boundary = buffer.indexOf('\n\n')) !== -1) {
                const rawEvent = buffer.slice(0, boundary);
                buffer = buffer.slice(boundary + 2);
                const lines = rawEvent.split('\n');
                const evName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() ?? '';
                const text = lines
                    .filter((line) => line.startsWith('data:'))
                    .map((line) => line.slice(5))
                    .join('\n');
                if (text.length === 0) continue;
                if (evName === 'thinking') {
                    onThinking(text);
                } else if (evName === 'status') {
                    onStatus(text);
                } else if (evName === 'sources') {
                    onSources(text.split('\n').filter((n) => n.trim().length > 0));
                } else if (evName === 'tool') {
                    try {
                        onTool(JSON.parse(text));
                    } catch (ignored) { /* 형식이 깨진 상태 이벤트는 표시만 포기한다 */ }
                } else {
                    onToken(text);
                }
            }
        }
        onDone();
    }

    newChatBtn.addEventListener('click', () => createSession());

    fileInput.addEventListener('change', () => {
        setPendingFile(fileInput.files[0] ?? null);
        // 문서를 올려놓고 다른 기능으로 질문하는 함정을 막는다 — 첨부하면 문서 Q&A로 전환.
        if (fileInput.files[0]) featureSelect.value = 'DOC_QA';
    });

    removeAttachmentBtn.addEventListener('click', () => setPendingFile(null));

    messageInput.addEventListener('input', () => {
        messageInput.style.height = 'auto';
        messageInput.style.height = Math.min(messageInput.scrollHeight, 160) + 'px';
    });

    messageInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            chatForm.requestSubmit();
        }
    });

    chatForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const text = messageInput.value.trim();
        const file = state.pendingFile;
        if (!text && !file) return;

        if (!state.currentSessionId) {
            await createSession();
        }

        let attachmentTag = '';
        if (file) {
            try {
                const uploaded = await uploadAttachment(state.currentSessionId, file);
                attachmentTag = `📎 ${uploaded.filename} (${uploaded.chunkCount}청크 인덱싱됨)`;
            } catch (err) {
                addMessageBubble('assistant', `[첨부파일 업로드 오류: ${err.message}]`);
            }
            setPendingFile(null);
        }

        const userBubbleText = attachmentTag ? `${attachmentTag}\n${text}` : text;
        if (userBubbleText) addMessageBubble('user', userBubbleText);
        messageInput.value = '';
        messageInput.style.height = 'auto';

        // 파일만 올리고 질문이 없으면 인덱싱까지만 — 빈 질문을 LLM에 보내지 않는다.
        if (!text) return;

        sendBtn.disabled = true;
        const assistantBubble = addLoadingBubble();
        let rawText = '';
        let thinkingText = '';
        // 말풍선 내부: 추론 패널 + 답변. 진행 상태·참조 문서는 말풍선 내부가 아니라
        // 말풍선 "아래"에 붙는 별도 형제 요소다 (docs/07-thinking-tool-status-design.md 6.2절).
        let thinkingPanel = null;
        let thinkingBody = null;
        let answerBody = null;
        let statusLine = null;
        let sourceFiles = [];

        function ensureSkeleton() {
            if (answerBody) return;
            assistantBubble.innerHTML = '';
            thinkingPanel = document.createElement('details');
            thinkingPanel.className = 'thinking-panel';
            thinkingPanel.open = true;
            thinkingPanel.hidden = true;
            thinkingPanel.appendChild(el('summary', null, '🧠 추론 과정'));
            thinkingBody = el('div', 'thinking-body');
            thinkingPanel.appendChild(thinkingBody);
            answerBody = el('div', 'answer-body');
            assistantBubble.append(thinkingPanel, answerBody);
        }

        // 진행 상태: 현재 작업 하나만 문자로 표시하고, 답변이 완료되면 제거한다.
        function showStatus(text) {
            if (!statusLine) {
                statusLine = el('div', 'stream-status');
                assistantBubble.after(statusLine);
            }
            statusLine.textContent = text;
            messagesEl.scrollTop = messagesEl.scrollHeight;
        }

        function clearStatus() {
            statusLine?.remove();
            statusLine = null;
        }

        // 참조 문서: 답변 완료 후 말풍선 아래에 확장자별 아이콘과 함께 표시한다.
        const EXT_ICONS = {
            pdf: '📕', doc: '📘', docx: '📘', xls: '📗', xlsx: '📗',
            ppt: '📙', pptx: '📙', txt: '📄', md: '📄', hwp: '📝', hwpx: '📝',
        };

        function renderSources() {
            if (sourceFiles.length === 0) return;
            const list = el('div', 'source-list');
            list.appendChild(el('span', 'source-label', '참조 문서'));
            sourceFiles.forEach((name) => {
                const ext = name.includes('.') ? name.split('.').pop().toLowerCase() : '';
                list.appendChild(el('span', 'source-item', `${EXT_ICONS[ext] ?? '📎'} ${name}`));
            });
            assistantBubble.after(list);
            messagesEl.scrollTop = messagesEl.scrollHeight;
        }

        await streamChat(state.currentSessionId, text, featureSelect.value, {
            onToken: (chunk) => {
                ensureSkeleton();
                rawText += chunk;
                answerBody.innerHTML = renderMarkdown(rawText);
                messagesEl.scrollTop = messagesEl.scrollHeight;
            },
            onThinking: (chunk) => {
                ensureSkeleton();
                thinkingPanel.hidden = false;
                thinkingText += chunk;
                thinkingBody.innerHTML = renderMarkdown(thinkingText);
                messagesEl.scrollTop = messagesEl.scrollHeight;
            },
            onStatus: showStatus,
            onSources: (names) => {
                sourceFiles = names;
            },
            // 도구 실행(Phase 2)도 "현재 작업" 한 줄 표시로 흡수한다.
            onTool: (status) => {
                if (status.phase === 'start') {
                    showStatus(`${status.name} 실행 중...`);
                } else {
                    showStatus(status.ok ? `${status.name} 완료` : `${status.name} 실패`);
                }
            },
            onDone: () => {
                clearStatus();
                // 최종 화면의 주인공은 답변 — 추론 패널은 자동으로 접는다 (토글 가능).
                if (thinkingPanel && !thinkingPanel.hidden) thinkingPanel.open = false;
                if (!rawText && !thinkingText) assistantBubble.innerHTML = '';
                renderSources();
                sendBtn.disabled = false;
                loadSessions();
                // 첫 턴 완료 후 서버가 비동기로 LLM 요약 제목을 만든다 —
                // 몇 초 뒤 한 번 더 목록을 읽어 "새 대화"를 생성된 제목으로 교체한다.
                setTimeout(loadSessions, 4000);
            },
            onError: (err) => {
                clearStatus();
                ensureSkeleton();
                rawText += `\n[오류: ${err.message}]`;
                answerBody.innerHTML = renderMarkdown(rawText);
                sendBtn.disabled = false;
            },
        });
    });

    loadFeatures();
    // 페이지 로드 시 세션 목록을 읽고, 가장 최근 세션의 실제 대화(영속 저장분)를
    // 자동으로 불러온다 — 서버를 재시작해도 이어서 보이는 화면이 된다.
    loadSessions().then((sessions) => {
        if (!state.currentSessionId && sessions.length > 0) {
            selectSession(sessions[0].id);
        }
    });
})();
