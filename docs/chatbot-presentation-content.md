# AI 대화 문맥 관리를 위한 세션 라우팅 서비스

> `docs/chatbot.pdf`(16:9, 약 9페이지) 기준 발표용 채움 텍스트

---

## 1. 표지

**AI 대화 문맥 관리를 위한 세션 라우팅 서비스**

- user+custom 스코프 기반 sessionId 설계
- OpenClaw Gateway 연동 아키텍처

---

## 2. 문제 정의

- 같은 사용자의 요청이 하나의 세션으로 섞이면 문맥 충돌 발생
- 채널/탭/쓰레드 단위 컨텍스트 분리 필요
- 운영 관점에서 세션 무결성과 라우팅 일관성이 중요

핵심 질문:
**"어떻게 대화 문맥을 안전하게 분리하고 재사용할 것인가?"**

---

## 3. 전체 아키텍처

요청 흐름:
1) Client → question/userName/ragContext/sessionTicket 전달  
2) SessionRoutingService → sessionId/sessionKey 계산  
3) ChatQueryService → 검증/동시성/rate limit 적용  
4) GatewayClient → `/v1/chat/completions` 호출  
5) 동일 세션 기준 응답/로그 처리

---

## 4. 세션 모델 (user + custom)

- `sessionId`는 **user + custom 스코프** 기반
- `custom`은 사용자 내부 문맥 분리 키
  - 예: 채널, 방, 탭, 쓰레드, 업무 컨텍스트

규칙:
- same user + different custom = 다른 세션
- same user + same custom = 같은 세션 재사용

---

## 5. 세션 토큰 정책

- `sessionTicket`: HMAC 서명 토큰 (sessionId 무결성 보장)
- sessionTicket 미제공 시 `app.gateway.default-session` 사용
  - 값에 `.` 포함: 토큰으로 간주
  - 값에 `.` 미포함: sessionId로 간주 후 access token 발급

목표:
- 세션 위변조 방지
- 기본 세션 fallback 일관 처리

---

## 6. Gateway 요청 계약

필수 헤더:
- `Authorization: Bearer <gateway-token>`
- `x-openclaw-agent-id: chatbot`
- `x-openclaw-session-key: {session-key-prefix}{sessionId}`

핵심 바디:
- `model: openclaw:chatbot`
- `messages: [system, user]`
- `stream: true|false`

---

## 7. 역할 분리: 코드 vs 정책

코드(Spring):
- 검증/세션 라우팅/요청 전달

정책 파일(OpenClaw):
- 답변 기준/스타일/행동 제한
- `AGENTS.md`, `SOUL.md`, `USER.md`

효과:
- 서비스 로직과 응답 정책의 독립적 개선 가능

---

## 8. 운영 관점의 장점

- 문맥 혼선 감소 (세션 스코프 명확화)
- 장애 분석 용이 (sessionKey 단위 추적)
- 변경 유연성 (custom 확장으로 컨텍스트 추가)
- 운영 안정성 (stream/non-stream 전략 분리)

---

## 9. 결론

**세션은 사용자 단위가 아니라, user+custom 문맥 단위로 관리한다.**

요약:
- 세션 일관성 = 응답 품질 일관성
- 에이전트 정책 파일 = 운영 제어의 핵심 레버
- Gateway 계약 준수 = 확장 가능한 연동 기반
