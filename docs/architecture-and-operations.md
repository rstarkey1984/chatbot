# Architecture & Operations

## 1) 운영 핵심

이 서비스는 "요청을 LLM에 보내는 API"가 아니라,
"사용자별 세션 일관성과 튜터 규칙 일관성을 유지하는 백엔드"입니다.

### 핵심 불변 규칙

1. 같은 `(사용자 + custom)` 조합(`sessionId`)은 항상 같은 `sessionKey`를 사용한다.
2. `sessionId`는 검증 규칙(소문자/숫자/하이픈, 3~64자)을 강제한다.
3. 답변 스타일/안전선은 OpenClaw workspace 규칙 파일로 관리한다.

같은 사용자라도 custom 값이 다르면 서로 다른 세션으로 분리됩니다.

## 2) 요청 흐름

1. 클라이언트 → `/chat/query` 또는 `/chat/query/stream`
2. `SessionRoutingService` → 세션 컨텍스트 계산
   - `sessionTicket` 없으면 `default-session` fallback
   - `sessionId`, `sessionKey` 생성
3. `ChatQueryService` → 검증/동시요청 제어/rate limit
4. `GatewayClient` → OpenClaw Gateway `/v1/chat/completions` 호출
5. 응답/로그를 같은 세션 단위로 저장

## 3) OpenClaw 연동 원칙

- agentId: `chatbot`
- workspace: `~/.openclaw/workspace-chatbot`
- 튜터 정책 문서:
  - `AGENTS.md`
  - `SOUL.md`
  - `USER.md`

규칙 변경은 코드 변경보다 정책 영향이 크므로, 문서 변경 시 리뷰를 권장합니다.

## 4) 장애 점검 체크리스트

1. Gateway 상태 확인
   - `openclaw status --deep`
2. 보안/연결 상태 확인
   - `openclaw security audit --deep`
3. 세션 키 생성 규칙 확인
   - `agent:chatbot:tutor:session:{sessionId}`
4. `sessionId` 검증/정규화 로직 확인
5. workspace 규칙 파일(AGENTS/SOUL/USER) 의도치 않은 변경 여부 확인

## 5) 운영 권장사항

- 세션 문맥 꼬임 방지를 위해 사용자 식별자를 임의로 재사용하지 않는다.
- 토큰 시크릿(`CHATBOT_TOKEN_SECRET`)은 정기 교체 정책을 둔다.
- 응답 품질 이슈가 발생하면 먼저 workspace 규칙 파일을 확인한다.
