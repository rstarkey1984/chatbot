# AI 대화 문맥 관리를 위한 세션 라우팅 서비스

> 발표자료 본문 (10장 이내 / 9장 구성)

---

## 목차

1. 표지  
2. 문제 정의  
3. 설계 목표  
4. 전체 아키텍처  
5. 세션 모델 (user + custom)  
6. 세션 토큰 정책  
7. Gateway 요청 계약  
8. 운영 전략 및 기대 효과  
9. 결론

---

## 1. 표지

**AI 대화 문맥 관리를 위한 세션 라우팅 서비스**  
부제: user+custom 스코프 기반 sessionId 설계와 Gateway 연동

발표 멘트(3줄):
- 이번 발표는 대화 문맥 충돌을 줄이기 위한 세션 설계 이야기입니다.
- 핵심은 사용자 단위가 아니라 문맥 단위로 세션을 관리하는 것입니다.
- user+custom 모델과 Gateway 계약 중심으로 설명드리겠습니다.

---

## 2. 문제 정의

- 사용자 요청이 단일 세션에 섞이면 문맥 충돌 발생
- 채널/탭/쓰레드 단위 문맥 분리 필요
- 운영 관점에서 세션 무결성과 라우팅 일관성 확보 필요

발표 멘트(3줄):
- 기존 방식에서는 같은 사용자의 다른 대화가 한 컨텍스트로 섞였습니다.
- 이 문제는 답변 품질 저하와 운영 이슈로 바로 이어집니다.
- 그래서 세션 분리 기준을 명확히 정의하는 것이 출발점입니다.

---

## 3. 설계 목표

- 문맥 충돌 없이 대화 컨텍스트 분리
- 세션 식별값 무결성 보장
- 코드와 정책의 책임 분리로 운영 유연성 확보

발표 멘트(3줄):
- 목표는 단순히 세션을 나누는 게 아니라, 재사용 규칙까지 정의하는 것입니다.
- 보안 측면에서는 세션 식별값의 위변조 방지도 필요했습니다.
- 또한 운영을 위해 로직과 정책을 분리 가능한 구조로 설계했습니다.

---

## 4. 전체 아키텍처

1) 클라이언트 요청 수신 (`question`, `userName`, `ragContext`, `sessionTicket`)  
2) `SessionRoutingService`가 `sessionId`, `sessionKey` 계산  
3) `ChatQueryService`가 검증/동시성/rate limit 처리  
4) `GatewayClient`가 OpenClaw Gateway 호출  
5) 동일 세션 기준으로 응답/로그 일관 처리

발표 멘트(3줄):
- 요청은 라우팅 서비스에서 먼저 세션 컨텍스트를 계산합니다.
- 그 다음 쿼리 서비스에서 안정성 관련 검증을 적용합니다.
- 마지막으로 Gateway 호출과 로그까지 같은 세션 기준으로 맞춥니다.

---

## 5. 세션 모델: user + custom

- `sessionId`는 **user + custom 스코프** 기반
- `custom`은 사용자 내부 문맥 분리 키
  - 예: 채널, 방, 탭, 쓰레드, 업무 컨텍스트

규칙:
- same user + different custom → 다른 세션
- same user + same custom → 같은 세션 재사용

발표 멘트(3줄):
- 여기서 핵심은 custom이 문맥 분리의 실질 기준이라는 점입니다.
- 같은 사용자라도 custom이 다르면 완전히 다른 세션으로 처리합니다.
- 반대로 custom이 같으면 문맥을 이어서 사용합니다.

---

## 6. 세션 토큰 정책

- `sessionTicket`: HMAC 서명 토큰 (sessionId 무결성 보장)
- `sessionTicket` 미제공 시 `app.gateway.default-session` 사용
  - `.` 포함: 토큰으로 간주
  - `.` 미포함: sessionId로 간주 후 access token 발급

의도:
- 세션 위변조 방지
- fallback 경로 일관화

발표 멘트(3줄):
- sessionTicket은 신뢰 가능한 세션 식별값을 위한 장치입니다.
- 티켓이 없을 때도 default-session 규칙으로 동일한 흐름을 유지합니다.
- 즉, 보안성과 운영 일관성을 동시에 확보한 정책입니다.

---

## 7. Gateway 요청 계약

필수 헤더:
- `Authorization: Bearer <gateway-token>`
- `x-openclaw-agent-id: chatbot`
- `x-openclaw-session-key: {session-key-prefix}{sessionId}`

핵심 바디:
- `model: openclaw:chatbot`
- `messages: [system, user]`
- `stream: true|false`

발표 멘트(3줄):
- agent-id는 어떤 정책/워크스페이스에서 처리할지 결정합니다.
- session-key는 어떤 대화 문맥으로 처리할지 결정합니다.
- 이 두 축을 분리해 요청 계약을 명확하게 유지했습니다.

---

## 8. 운영 전략 및 기대 효과

운영 전략:
- 코드(Spring): 입력 검증, 세션 라우팅, Gateway 전달
- 정책(OpenClaw): 답변 기준/스타일/행동 제한 (`AGENTS.md`, `SOUL.md`, `USER.md`)

기대 효과:
- 문맥 혼선 감소 (세션 스코프 명확화)
- 장애 분석 용이 (sessionKey 단위 추적)
- 확장 유연성 (custom 키 추가로 컨텍스트 확장)

발표 멘트(3줄):
- 구현 로직과 응답 정책을 분리해 운영 변경 비용을 낮췄습니다.
- 문제 발생 시 sessionKey 기준으로 추적이 쉬워집니다.
- 신규 채널/업무가 추가돼도 custom만 확장하면 대응 가능합니다.

---

## 9. 결론

**세션은 사용자 단위가 아니라 user+custom 문맥 단위로 관리한다.**

요약:
- 세션 일관성 → 응답 일관성
- 정책 파일 → 운영 제어 포인트
- Gateway 계약 준수 → 확장 가능한 연동 기반

발표 마무리 멘트(3줄):
- 이번 설계의 본질은 문맥 단위 세션 관리입니다.
- 이를 통해 응답 품질과 운영 효율을 함께 가져갈 수 있습니다.
- 이후 확장도 같은 계약 위에서 일관되게 진행할 수 있습니다.

---

## Q&A 예상 질문 (옵션 슬라이드로 추가 가능)

1. custom 값은 어디서 만들고 누가 관리하나요?  
2. sessionTicket 없이도 운영 가능한가요?  
3. stream/non-stream 분리 전략의 운영 이점은 무엇인가요?
