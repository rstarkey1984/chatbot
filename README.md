# Chatbot Gateway RAG 

이 프로젝트는 OpenClaw 에이전트에 요청을 안정적으로 전달하는 오케스트레이션 레이어입니다.

핵심은 두 가지입니다.

1. 세션 일관성 (`user + custom` 스코프)
2. 에이전트 정책 파일(AGENTS/SOUL/USER) 기반 응답 통제

---

## 1) 핵심 아키텍처

요청 흐름:

1. 클라이언트가 `question`, `userName`, `ragContext`, `sessionTicket` 전송
2. `SessionRoutingService`가 `sessionTicket/default-session` 규칙으로 세션 컨텍스트 계산
   - `sessionTicket`, `sessionId`, `sessionKey`
   - `sessionId`는 `user + custom` 스코프 기반 식별자
3. `ChatQueryService`가 입력 검증/동시성/rate limit 적용
4. `GatewayClient`가 OpenClaw Gateway `/v1/chat/completions` 호출
5. 응답/로그를 같은 세션 기준으로 처리

`custom` 의미:
- 사용자 내부에서 대화 문맥을 분리하기 위한 추가 키
- 예: 채널/방/탭/쓰레드/업무 컨텍스트 식별자
- 같은 `user`라도 `custom`이 다르면 다른 세션으로 라우팅
- `custom`이 같으면 같은 세션 문맥을 재사용

---

## 2) Gateway 요청 계약

필수 헤더:

- `Authorization: Bearer <gateway-token>`
- `x-openclaw-agent-id: chatbot`
- `x-openclaw-session-key: {session-key-prefix}{sessionId}`

핵심 요청 데이터:

- `model: openclaw:chatbot`
- `messages: [system, user]`
- `stream: true|false`

헤더 역할:

- `x-openclaw-agent-id`: 어떤 에이전트(워크스페이스/규칙/도구 정책)로 처리할지 선택
- `x-openclaw-session-key`: 같은 에이전트 내에서 어떤 대화 문맥으로 처리할지 선택

클라이언트 전략:
- non-stream 요청: `WebClient`
- stream 요청: `HttpClient + BufferedReader` (SSE 라인 단위 안정성 우선)

---

## 3) 세션 정책

- `sessionTicket`은 HMAC 서명 토큰으로 `sessionId` 무결성 보장 용도
- `sessionTicket`이 없으면 `app.gateway.default-session` 사용
  - 값에 `.`이 있으면 토큰으로 간주
  - 값에 `.`이 없으면 sessionId로 보고 access token 생성

세션키 접두어:

- 기본값: `agent:chatbot:tutor:session:`
- 설정: `app.gateway.session-key-prefix` / `OPENCLAW_SESSION_KEY_PREFIX`

---

## 4) 에이전트 정책 파일

실제 답변 품질은 코드보다 아래 파일에서 결정됩니다.

- `~/.openclaw/workspace-chatbot/AGENTS.md`
- `~/.openclaw/workspace-chatbot/SOUL.md`
- `~/.openclaw/workspace-chatbot/USER.md`

운영 원칙:

- 코드(Spring): 검증/라우팅/전달
- 정책 파일(OpenClaw): 답변 기준/스타일/행동 제한

---

## 5) 주요 설정

`src/main/resources/application.yml`

- `app.gateway.url`
- `app.gateway.token`
- `app.gateway.config-path`
- `app.gateway.agent-id` (`OPENCLAW_AGENT_ID`로 override 가능)
- `app.gateway.default-session`
- `app.gateway.session-key-prefix`
- `app.auth.token-secret`
- `app.auth.issue-api-key`
- `app.auth.default-issue-ttl-seconds`
- `app.auth.max-issue-ttl-seconds`

---

## 6) 실행

```bash
cd ~/projects/chatbot
./gradlew bootRun
```

- 앱: `http://localhost:8888`
- 헬스체크: `GET /health`

사용 예시(임베드/운영 가이드)는 릴리즈 노트에서 버전별로 관리합니다.

---

## 7) 문서

- `docs/README.md`
- `docs/architecture-and-operations.md`
- `docs/deployment-config.md`
- `docs/agent-policy-files.md`
