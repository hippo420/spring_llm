-- 대화 영속화 L3 스키마 (docs/chat-history-tiered-storage-design.md 3.3절)
-- 자동 실행되지 않는다 — 로컬 Postgres에 직접 실행할 것.
-- (또는 개발 중에는 spring.jpa.hibernate.ddl-auto=update로 Entity에서 자동 생성 가능)

CREATE TABLE IF NOT EXISTS chat_session (
    id            UUID PRIMARY KEY,
    title         VARCHAR(200) NOT NULL DEFAULT '새 대화',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seq      BIGINT NOT NULL DEFAULT 0          -- 마지막으로 발급된 seq
);

CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGSERIAL PRIMARY KEY,
    session_id  UUID NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    seq         BIGINT NOT NULL,
    role        VARCHAR(20) NOT NULL,               -- 'user' | 'assistant'
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_chat_message_session_seq UNIQUE (session_id, seq)  -- 멱등 백업의 핵심
);
CREATE INDEX IF NOT EXISTS idx_chat_message_session ON chat_message(session_id, seq);

CREATE TABLE IF NOT EXISTS chat_summary (
    id          BIGSERIAL PRIMARY KEY,
    session_id  UUID NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    summary     TEXT NOT NULL,
    up_to_seq   BIGINT NOT NULL,                    -- 이 요약이 커버하는 마지막 메시지
    model       VARCHAR(60),                        -- 요약 생성 모델 (재생성 판단용)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_chat_summary_session_seq UNIQUE (session_id, up_to_seq)
);
