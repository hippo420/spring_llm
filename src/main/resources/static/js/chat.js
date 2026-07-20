(() => {
    const state = {
        currentSessionId: null,
        pendingFile: null,
    };

    const mainEl = document.querySelector('.main');
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

    // 랜딩 화면: 대화(세션) 자체가 없거나, 세션은 있지만 메시지가 아직 없을 때
    // 중앙 정렬된 인사말 + 입력창을 보여준다. 첫 메시지가 오가면 일반 레이아웃(메시지
    // 목록 + 하단 고정 입력창)으로 전환된다 — 입력 폼은 같은 DOM 노드를 CSS로만 재배치한다.
    function setLanding(isLanding) {
        mainEl.classList.toggle('landing', isLanding);
    }

    function clearMessages() {
        messagesEl.innerHTML = '';
        setLanding(true);
    }

    function addMessageBubble(role, content) {
        setLanding(false);
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
        setLanding(false);
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
        if (fileInput.files[0]) featureSelect.value = 'DOC_RAG';
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
        // initial=true는 전송 직후의 기본 상태 — 서버 이벤트 없이 답변이 시작되면 지운다.
        let statusIsInitial = false;

        function showStatus(text, initial = false) {
            if (!statusLine) {
                statusLine = el('div', 'stream-status');
                assistantBubble.after(statusLine);
            }
            statusIsInitial = initial;
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
                if (/^https?:\/\//i.test(name)) {
                    // 웹 검색 출처: 도메인 라벨 + 새 탭 링크 (URL 판정이 확장자 판정보다 먼저)
                    const a = document.createElement('a');
                    a.className = 'source-item';
                    a.href = name;
                    a.target = '_blank';
                    a.rel = 'noopener noreferrer';
                    let label = name;
                    try { label = new URL(name).hostname.replace(/^www\./, ''); } catch (ignored) { /* 원본 URL 그대로 표시 */ }
                    a.textContent = `🌐 ${label}`;
                    a.title = name;
                    list.appendChild(a);
                    return;
                }
                const ext = name.includes('.') ? name.split('.').pop().toLowerCase() : '';
                list.appendChild(el('span', 'source-item', `${EXT_ICONS[ext] ?? '📎'} ${name}`));
            });
            assistantBubble.after(list);
            messagesEl.scrollTop = messagesEl.scrollHeight;
        }

        // 답변 아래 액션 아이콘 — 지금은 복사만 실제 동작한다. 좋아요/싫어요·더보기 같은
        // 피드백 저장은 백엔드가 없어 눌러도 아무 일이 없는 장식 버튼이 되므로 넣지 않는다.
        function addCopyAction(container, text) {
            const actions = el('div', 'message-actions');
            const copyBtn = el('button', 'msg-action-btn', '📋 복사');
            copyBtn.type = 'button';
            copyBtn.addEventListener('click', async () => {
                try {
                    await navigator.clipboard.writeText(text);
                    copyBtn.textContent = '✓ 복사됨';
                } catch (ignored) {
                    copyBtn.textContent = '복사 실패';
                }
                setTimeout(() => { copyBtn.textContent = '📋 복사'; }, 1500);
            });
            actions.appendChild(copyBtn);
            container.appendChild(actions);
        }

        // 도구명 → 사용자에게 보여줄 한국어 작업명 (docs/08 문서 3절 6항 — 상태는 문자만).
        const TOOL_LABELS = {
            getCurrentDateTime: '현재 시각 확인',
            searchDocuments: '문서 검색',
            searchNews: '뉴스 검색',
            searchWeb: '웹 검색',
            searchStock: '종목 검색',
            getStockPrice: '시세 조회',
            getTopStocks: '순위 조회',
            getInvestorTrend: '수급 조회',
            getFinancialStatement: '재무제표 조회',
            getEconomicEvents: '경제지표 조회',
            askMarketAnalyst: '시장 분석가 작업',
            askDocumentAnalyst: '문서 분석가 작업',
            askNewsAnalyst: '뉴스 분석가 작업',
            askWebResearcher: '웹 리서처 작업',
            criticReview: '비평가 검토',
            reviseAnswer: '답변 보완',
        };

        // ── 에이전트 활동 타임라인 (멀티 에이전트 전용) ──
        // 워커 위임(askXxx)과 워커 내부 도구의 TOOL 이벤트를 상태 한 줄로 덮어쓰지 않고
        // 워커별 트리로 누적한다. 워커 위임 이벤트가 처음 올 때만 패널이 만들어지므로
        // 다른 기능(TOOL_RAG 등)의 화면은 기존 그대로다. 워커 내부 도구 이벤트는 백엔드가
        // 태깅한 agent(워커 표시명) 필드로 소속 워커에 귀속된다.
        const AGENT_TOOL_NAMES = {
            askMarketAnalyst: '시장 데이터 분석가',
            askDocumentAnalyst: '문서 분석가',
            askNewsAnalyst: '뉴스 분석가',
            askWebResearcher: '웹 리서처',
            // 비평가 루프(백엔드 CriticLoop 발행)도 워커와 같은 행으로 표시한다.
            criticReview: '비평가',
            reviseAnswer: '답변 보완',
        };
        const AGENT_ICONS = {
            '시장 데이터 분석가': '📊',
            '문서 분석가': '📘',
            '뉴스 분석가': '📰',
            '웹 리서처': '🌐',
            '비평가': '🧐',
            '답변 보완': '✍️',
        };
        let agentPanel = null;
        let agentBody = null;
        const openWorkers = new Map(); // 표시명 → 실행 중인 워커 행 (직렬 실행이라 이름당 최대 1개)
        let lastWorker = null;         // agent 태그가 없는 이벤트의 폴백 귀속처

        function ensureAgentPanel() {
            if (agentPanel) return;
            ensureSkeleton();
            agentPanel = document.createElement('details');
            agentPanel.className = 'agent-panel';
            agentPanel.open = true;
            agentPanel.appendChild(el('summary', null, '🤖 에이전트 활동'));
            agentBody = el('div', 'agent-body');
            agentPanel.appendChild(agentBody);
            assistantBubble.insertBefore(agentPanel, answerBody);
        }

        // args는 {"task":"..."} JSON의 200자 프리뷰 — 절단으로 JSON이 깨질 수 있어
        // 파싱 실패 시 정규식으로 task 값만 건진다.
        function parseTask(args) {
            try {
                const parsed = JSON.parse(args);
                if (parsed && typeof parsed.task === 'string') return parsed.task;
            } catch (ignored) { /* 아래 정규식 폴백 */ }
            const m = /"task"\s*:\s*"([^"]*)/.exec(args ?? '');
            return m ? m[1] : '';
        }

        function formatDuration(ms) {
            return ms >= 1000 ? (ms / 1000).toFixed(1) + 's' : ms + 'ms';
        }

        // 워커 위임 이벤트: start → 워커 행(이름 + 하위 과제) 추가, end → ✓/✗ + 소요시간.
        function onWorkerEvent(status) {
            ensureAgentPanel();
            const name = AGENT_TOOL_NAMES[status.name];
            if (status.phase === 'start') {
                const row = el('div', 'agent-worker');
                const head = el('div', 'agent-worker-head');
                head.appendChild(el('span', 'agent-name', `${AGENT_ICONS[name] ?? '🤖'} ${name}`));
                const statusEl = el('span', 'agent-status running', '⏳ 작업 중...');
                head.appendChild(statusEl);
                row.appendChild(head);
                const task = parseTask(status.args);
                if (task) row.appendChild(el('div', 'agent-task', `“${task}”`));
                const toolsEl = el('div', 'agent-tools');
                row.appendChild(toolsEl);
                agentBody.appendChild(row);
                const worker = {toolsEl, statusEl, openTools: new Map()};
                openWorkers.set(name, worker);
                lastWorker = worker;
            } else {
                const worker = openWorkers.get(name);
                if (!worker) return;
                worker.statusEl.className = 'agent-status ' + (status.ok ? 'ok' : 'fail');
                worker.statusEl.textContent =
                    (status.ok ? '✓ 완료' : '✗ 실패') + ' · ' + formatDuration(status.durationMs ?? 0);
                openWorkers.delete(name);
                if (lastWorker === worker) lastWorker = null;
            }
            messagesEl.scrollTop = messagesEl.scrollHeight;
        }

        // 워커 내부 도구 이벤트: 소속 워커 행 아래 들여쓴 줄로 진행을 보여준다.
        function onWorkerToolEvent(status) {
            const worker = openWorkers.get(status.agent) ?? lastWorker;
            if (!worker) return;
            const label = TOOL_LABELS[status.name] ?? status.name;
            if (status.phase === 'start') {
                const line = el('div', 'agent-tool running', `⏳ ${label} 중...`);
                worker.toolsEl.appendChild(line);
                worker.openTools.set(status.name, line);
            } else {
                const line = worker.openTools.get(status.name);
                if (!line) return;
                line.className = 'agent-tool ' + (status.ok ? 'ok' : 'fail');
                line.textContent =
                    (status.ok ? '✓' : '✗') + ` ${label} · ${formatDuration(status.durationMs ?? 0)}`;
                worker.openTools.delete(status.name);
            }
            messagesEl.scrollTop = messagesEl.scrollHeight;
        }

        // 전송 직후 기본 상태 — 모델이 도구 호출을 준비하는 동안(수 초~수십 초) 화면이
        // 무반응으로 보이지 않게 한다. 서버 STATUS/도구 이벤트가 오면 대체된다.
        showStatus('응답 준비 중...', true);

        await streamChat(state.currentSessionId, text, featureSelect.value, {
            onToken: (chunk) => {
                ensureSkeleton();
                // 도구 없이 바로 답변이 시작되면 기본 상태는 치운다 — 서버가 만든 상태
                // ("웹 검색 완료" 등)는 답변 완료까지 유지한다(기존 DOC_RAG 동작과 동일).
                if (statusIsInitial) clearStatus();
                rawText += chunk;
                answerBody.innerHTML = renderMarkdown(rawText);
                messagesEl.scrollTop = messagesEl.scrollHeight;
            },
            onThinking: (chunk) => {
                ensureSkeleton();
                if (statusIsInitial) showStatus('추론 중...', true);
                thinkingPanel.hidden = false;
                thinkingText += chunk;
                thinkingBody.innerHTML = renderMarkdown(thinkingText);
                messagesEl.scrollTop = messagesEl.scrollHeight;
            },
            onStatus: showStatus,
            // sources 이벤트는 여러 번 올 수 있다 (TOOL_RAG의 searchDocuments 호출마다 1회)
            // — 중복 없이 누적한다.
            onSources: (names) => {
                sourceFiles = [...new Set([...sourceFiles, ...names])];
            },
            // 도구 실행(Phase 2)도 "현재 작업" 한 줄 표시로 흡수한다 — 한국어 라벨로.
            // 멀티 에이전트 이벤트는 추가로 타임라인 패널에도 누적한다.
            onTool: (status) => {
                const label = TOOL_LABELS[status.name] ?? status.name;
                if (status.phase === 'start') {
                    showStatus(`${label} 중...`);
                } else {
                    showStatus(status.ok ? `${label} 완료` : `${label} 실패`);
                }
                if (AGENT_TOOL_NAMES[status.name]) {
                    onWorkerEvent(status);
                } else if (agentPanel) {
                    onWorkerToolEvent(status);
                }
            },
            onDone: () => {
                clearStatus();
                // 최종 화면의 주인공은 답변 — 추론·에이전트 패널은 자동으로 접는다 (토글 가능).
                if (thinkingPanel && !thinkingPanel.hidden) thinkingPanel.open = false;
                if (agentPanel) agentPanel.open = false;
                if (!rawText && !thinkingText) assistantBubble.innerHTML = '';
                if (rawText) addCopyAction(assistantBubble, rawText);
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
