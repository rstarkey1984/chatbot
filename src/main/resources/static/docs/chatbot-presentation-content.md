# AI 대화 문맥 관리를 위한 세션 라우팅 서비스

> 발표용 슬라이드 문안 (9장 구성)

---

## 1. 표지

**AI 대화 문맥 관리를 위한 세션 라우팅 서비스**  
부제: user+custom 스코프 기반 sessionId 설계와 Gateway 연동

---

## 2. 문제 정의

- 사용자 요청이 단일 세션에 섞이면 문맥 충돌 발생
- 채널/탭/쓰레드 단위 문맥 분리 필요
- 운영 관점에서 세션 무결성과 라우팅 일관성 확보 필요

핵심 질문:  
**"대화 문맥을 어떻게 분리하고, 언제 재사용할 것인가?"**

---

## 3. 아키텍처 개요

1) 클라이언트 요청 수신 (`question`, `userName`, `ragContext`, `sessionTicket`)  
2) `SessionRoutingService`가 `sessionId`, `sessionKey` 계산  
3) `ChatQueryService`가 검증/동시성/rate limit 처리  
4) `GatewayClient`가 OpenClaw Gateway 호출  
5) 동일 세션 기준으로 응답/로그 일관 처리

---

## 4. 세션 모델: user + custom

- `sessionId`는 **user + custom 스코프** 기반
- `custom`은 사용자 내부 문맥 분리 키
  - 예: 채널, 방, 탭, 쓰레드, 업무 컨텍스트

규칙:
- same user + different custom → 다른 세션
- same user + same custom → 같은 세션 재사용

---

## 5. 세션 토큰 정책

- `sessionTicket`: HMAC 서명 토큰 (sessionId 무결성 보장)
- `sessionTicket` 미제공 시 `app.gateway.default-session` 사용
  - `.` 포함: 토큰으로 간주
  - `.` 미포함: sessionId로 간주 후 access token 발급

의도:
- 세션 위변조 방지
- fallback 경로 일관화

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

## 7. 책임 분리: 코드 vs 정책

코드(Spring):
- 입력 검증
- 세션 라우팅
- Gateway 전달

정책(OpenClaw 파일):
- 답변 기준/스타일/행동 제한
- `AGENTS.md`, `SOUL.md`, `USER.md`

효과: 서비스 로직과 답변 정책을 독립적으로 개선 가능

---

## 8. 운영 효과

- 문맥 혼선 감소 (세션 스코프 명확화)
- 장애 분석 용이 (sessionKey 단위 추적)
- 확장 유연성 (custom 키 추가로 컨텍스트 확장)
- 전송 전략 최적화 (stream / non-stream 분리)

---

## 9. 결론

**세션은 사용자 단위가 아니라 user+custom 문맥 단위로 관리한다.**

요약:
- 세션 일관성 → 응답 일관성
- 정책 파일 → 운영 제어 포인트
- Gateway 계약 준수 → 확장 가능한 연동 기반
