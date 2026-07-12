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

    async function uploadAttachment(file) {
        const formData = new FormData();
        formData.append('file', file);
        const res = await fetch('/api/attachments', {method: 'POST', body: formData});
        if (!res.ok) throw new Error('첨부파일 업로드 실패');
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

    async function streamChat(sessionId, content, feature, onToken, onDone, onError) {
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
                const text = rawEvent
                    .split('\n')
                    .filter((line) => line.startsWith('data:'))
                    .map((line) => line.slice(5))
                    .join('\n');
                if (text.length > 0) onToken(text);
            }
        }
        onDone();
    }

    newChatBtn.addEventListener('click', () => createSession());

    fileInput.addEventListener('change', () => {
        setPendingFile(fileInput.files[0] ?? null);
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
                const uploaded = await uploadAttachment(file);
                attachmentTag = `📎 ${uploaded.filename}`;
            } catch (err) {
                addMessageBubble('assistant', `[첨부파일 업로드 오류: ${err.message}]`);
            }
            setPendingFile(null);
        }

        const userBubbleText = attachmentTag ? `${attachmentTag}\n${text}` : text;
        if (userBubbleText) addMessageBubble('user', userBubbleText);
        messageInput.value = '';
        messageInput.style.height = 'auto';

        sendBtn.disabled = true;
        const assistantBubble = addLoadingBubble();
        let rawText = '';

        await streamChat(
            state.currentSessionId,
            text,
            featureSelect.value,
            (chunk) => {
                rawText += chunk;
                assistantBubble.innerHTML = renderMarkdown(rawText);
                messagesEl.scrollTop = messagesEl.scrollHeight;
            },
            () => {
                if (!rawText) assistantBubble.innerHTML = '';
                sendBtn.disabled = false;
                loadSessions();
            },
            (err) => {
                rawText += `\n[오류: ${err.message}]`;
                assistantBubble.innerHTML = renderMarkdown(rawText);
                sendBtn.disabled = false;
            }
        );
    });

    loadFeatures();
    loadSessions();
})();
