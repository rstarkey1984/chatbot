# AI 대화 문맥 관리를 위한 세션 라우팅 서비스

> 발표자료 본문 (10장 구성)

---

## 목차

1. 표지  
2. 문제 정의  
3. 설계 목표  
4. 개발환경 및 프로젝트 구조  
5. 프로젝트 수행 절차 및 방법  
6. 전체 아키텍처  
7. 세션 모델 (user + custom)  
8. 세션 토큰 정책 및 Gateway 요청 계약  
9. 시연 단계  
10. 결론

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

핵심 질문:  
**"대화 문맥을 어떻게 분리하고, 언제 재사용할 것인가?"**

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
- 목표는 세션 분리뿐 아니라 재사용 규칙까지 명확히 하는 것입니다.
- 보안 측면에서는 세션 식별값의 위변조 방지가 필요했습니다.
- 운영 측면에서는 로직과 정책을 분리 가능한 구조로 설계했습니다.

---

## 4. 개발환경 및 프로젝트 구조

개발환경:
- Java 17, Spring Boot
- OpenClaw Gateway 연동
- stream/non-stream 분리 처리

프로젝트 구조(핵심):
- `src/main/java/.../service` : 라우팅/질의/전달 서비스
- `src/main/resources/application.yml` : 런타임 설정
- `src/main/resources/static/docs` : 발표/문서 자산
- `docs/` : 운영/구조 문서

발표 멘트(3줄):
- 먼저 어떤 기술 스택과 구조에서 구현했는지 공유드립니다.
- 서비스 레이어를 기준으로 책임을 분리해 유지보수성을 높였습니다.
- 설정과 문서를 분리해 운영 시 변경 포인트를 명확히 했습니다.

---

## 5. 프로젝트 수행 절차 및 방법

1) 문제 확인: 문맥 충돌/세션 혼선 사례 정리  
2) 설계: user+custom 세션 모델 및 요청 계약 정의  
3) 구현: SessionRoutingService, GatewayClient 중심 반영  
4) 검증: stream/non-stream, fallback 동작 점검  
5) 정리: 정책 파일 분리 및 문서화

발표 멘트(3줄):
- 진행은 문제 확인부터 운영 문서화까지 단계적으로 수행했습니다.
- 구현과 검증을 반복해 세션 규칙의 일관성을 우선 확보했습니다.
- 최종적으로 운영 가능한 형태로 정책/문서를 함께 정리했습니다.

---

## 6. 전체 아키텍처

1) 클라이언트 요청 수신 (`question`, `userName`, `ragContext`, `sessionTicket`)  
2) `SessionRoutingService`가 `sessionId`, `sessionKey` 계산  
3) `ChatQueryService`가 검증/동시성/rate limit 처리  
4) `GatewayClient`가 OpenClaw Gateway 호출  
5) 동일 세션 기준으로 응답/로그 일관 처리

발표 멘트(3줄):
- 요청은 라우팅 서비스에서 먼저 세션 컨텍스트를 계산합니다.
- 쿼리 서비스에서 검증과 제어를 적용해 품질을 보장합니다.
- 최종 호출과 로그까지 동일 세션 기준으로 맞춰 일관성을 유지합니다.

---

## 7. 세션 모델: user + custom

- `sessionId`는 **user + custom 스코프** 기반
- `custom`은 사용자 내부 문맥 분리 키
  - 예: 채널, 방, 탭, 쓰레드, 업무 컨텍스트

규칙:
- same user + different custom → 다른 세션
- same user + same custom → 같은 세션 재사용

발표 멘트(3줄):
- custom은 사용자 내부 대화 맥락을 분리하는 핵심 키입니다.
- 같은 사용자라도 custom이 다르면 완전히 다른 세션으로 처리합니다.
- custom이 같을 때만 문맥을 이어받도록 규칙을 고정했습니다.

---

## 8. 세션 토큰 정책 및 Gateway 요청 계약

세션 토큰 정책:
- `sessionTicket`: HMAC 서명 토큰 (sessionId 무결성 보장)
- `sessionTicket` 미제공 시 `app.gateway.default-session` 사용
  - `.` 포함: 토큰으로 간주
  - `.` 미포함: sessionId로 간주 후 access token 발급

Gateway 요청 계약:
- `Authorization: Bearer <gateway-token>`
- `x-openclaw-agent-id: chatbot`
- `x-openclaw-session-key: {session-key-prefix}{sessionId}`
- `model: openclaw:chatbot`, `messages`, `stream`

발표 멘트(3줄):
- 세션 토큰 정책으로 sessionId 무결성을 보장합니다.
- agent-id와 session-key를 분리해 요청 의미를 명확히 했습니다.
- 결과적으로 보안성과 확장성을 동시에 확보했습니다.

---

## 9. 시연 단계

1) 같은 `user + custom`으로 연속 요청 → 문맥 유지 확인  
2) 같은 user, 다른 custom 요청 → 문맥 분리 확인  
3) `sessionTicket` 없이 요청 → default-session fallback 확인  
4) 헤더/로그 확인 → session-key 라우팅 검증

발표 멘트(3줄):
- 시연에서는 문맥 유지와 문맥 분리를 순서대로 확인합니다.
- fallback 경로까지 보여드려 운영 시 예외 흐름도 검증합니다.
- 마지막으로 로그를 통해 라우팅 결과를 눈으로 확인합니다.

---

## 10. 결론

**세션은 사용자 단위가 아니라 user+custom 문맥 단위로 관리한다.**

요약:
- 세션 일관성 → 응답 일관성
- 정책 파일 → 운영 제어 포인트
- Gateway 계약 준수 → 확장 가능한 연동 기반

발표 마무리 멘트(3줄):
- 이번 설계의 핵심은 문맥 단위 세션 관리입니다.
- 이를 통해 답변 품질과 운영 효율을 함께 개선할 수 있습니다.
- 이후 기능 확장도 같은 계약 위에서 일관되게 진행 가능합니다.
