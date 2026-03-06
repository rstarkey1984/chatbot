# 챗봇/RAG 서비스

> 발표자료 본문 (7장 구성)

---

## 목차

1. 표지  
2. 문제 정의 및 설계 목표  
3. 개발환경 및 프로젝트 구조  
4. 프로젝트 수행 절차 및 방법  
5. 아키텍처/세션 정책/Gateway 요청 계약  
6. 시연 단계  
7. 기대효과 및 향후 개선

---

## 1. 표지

**챗봇/RAG 서비스**  
부제: 지식베이스(문서셋)별 세션 분리

---

## 2. 문제 정의 및 설계 목표

- 일반적인 챗봇은 여러 질문이 하나의 흐름으로 이어지기 쉬움
- 이전 대화 맥락이 다음 답변에 과하게 영향을 주는 경우가 발생
- 반대로 같은 주제 대화가 끊기면 맥락 단절로 인해 반복 질문이 늘어남
- 사용자는 "왜 지금 이 답을 하지?" 또는 "왜 이전 맥락을 기억 못하지?" 같은 혼선을 경험하게 됨

핵심 질문:  
**"사용자 입장에서 대화가 자연스럽고 일관되게 이어지게 하려면 무엇이 필요한가?"**

설계 목표:
- 챗봇/RAG 서비스에서 참고자료별 세션 라우팅 기준 확립
- 같은 참고자료 기반 대화는 이어지고, 다른 참고자료는 분리
- 세션 식별값 무결성 보장
- 코드와 정책의 책임 분리로 운영 유연성 확보

---

## 3. 개발환경 및 프로젝트 구조

개발환경:
- OS: Ubuntu
- Java 21, Spring Boot
- DB: MySQL

프로젝트 구조(상세):
- 프론트엔드
  - `src/main/resources/templates` : Thymeleaf 템플릿
  - `src/main/resources/static` : 정적 리소스(html/css/js)
- 백엔드
  - `controller` : API 엔드포인트
  - `service` : 세션 라우팅/질의 처리/Gateway 호출
  - `repository`, `entity` : DB 접근 및 데이터 모델
  - `common`, `controller/advice` : 공통 예외/응답 처리
- 데이터베이스
  - MySQL
  - `sql/` : 초기 스키마/운영 SQL
- OpenClaw Gateway
  - 백엔드에서 `/v1/chat/completions` 연동
  - `x-openclaw-agent-id`, `x-openclaw-session-key` 기반 문맥 라우팅

---

## 4. 프로젝트 수행 절차 및 방법

1) 요구사항 분석  
- 사용자 세션별 대화 이력을 애플리케이션에서 직접 관리해야 함  
- 요청마다 대화 이력을 함께 보내지 않으면 같은 주제도 맥락 단절 발생  
- 세션 키를 너무 넓게 잡으면 다른 질문 문맥이 섞일 위험 존재  
- 따라서 지식베이스(문서셋) 기준의 세션 식별/분리 규칙이 필요

2) 시스템 설계  
- 사용자 세션 이력 관리를 단순화하기 위해 OpenClaw Gateway 사용  
- 세션 식별 기준을 `user + custom`으로 정의  
- `custom`에 문서셋 식별값을 반영해 세션 분리 정책 수립  
- Gateway 연동 시 `x-openclaw-session-key` 규칙 확정

3) UI/UX 설계  
- 사용자가 현재 어떤 자료 기반으로 대화 중인지 인지 가능하게 구성  
- 자료 전환 시 대화가 분리된다는 점을 자연스럽게 이해하도록 흐름 설계  
- 불필요한 입력 없이 대화 중심 인터랙션 유지

4) 기능 구현  
- `SessionRoutingService`에 세션 계산/분리 로직 구현  
- `GatewayClient`에 헤더 계약(`agent-id`, `session-key`) 반영  
- 컨트롤러/서비스 계층에서 요청 검증 및 처리 흐름 정리

5) 테스트 및 검증  
- 같은 문서셋으로 연속 질문 시 문맥 유지 확인  
- 다른 문서셋으로 전환 시 세션 분리 확인  
- sessionTicket 유무에 따른 fallback 동작 점검

6) 결과 정리
- 문맥 섞임과 단절을 함께 줄이는 세션 운영 기준 확보  
- 운영 시 확인 가능한 라우팅 기준 및 로그 포인트 정리  
- 발표/문서에 재사용 가능한 설계 원칙으로 정리

---

## 5. 아키텍처/세션 정책/Gateway 요청 계약

전체 아키텍처:

1) 클라이언트 요청 수신 (`question`, `userName`, `ragContext`, `sessionTicket`)  
2) `ChatQueryService`가 입력 검증/동시성/rate limit 처리  
3) `SessionRoutingService`가 `sessionTicket` 기준으로 `sessionId` 해석 및 `sessionKey` 계산  
4) `GatewayClient`가 OpenClaw Gateway 호출  
5) 동일 세션 기준으로 응답/로그 일관 처리

세션 모델 (user + custom):
- `sessionId`는 **user + custom 스코프** 기반
- `custom`은 사용자 내부 대화 구분 키 (예: 문서셋/주제)

규칙:
- same user + different custom → 다른 세션
- same user + same custom → 같은 세션 재사용

세션 토큰 정책:
- `sessionTicket`: HMAC 서명 토큰 (sessionId 무결성 보장)
- `sessionTicket` 미제공 시 `app.gateway.default-session` 기반으로 기본 세션 적용

Gateway 요청 계약:
- `Authorization: Bearer <gateway-token>`
- `x-openclaw-agent-id: chatbot`
- `x-openclaw-session-key: {session-key-prefix}{sessionId}`
- `model: openclaw:chatbot`, `messages`, `stream`

---

## 6. 시연 단계

기준 주소:
- `http://localhost:8888`

0) 스프링부트 애플리케이션 실행  
- 프로젝트 루트에서 실행: `~/projects/chatbot`

```bash
cd ~/projects/chatbot
./gradlew bootRun
```

사전 확인:
1) 헬스체크  
- 브라우저에서 `http://localhost:8888/health` 접속
- 응답에 `{"status":"ok"}` 확인

2) 채팅 화면 접속  
- 브라우저에서 `http://localhost:8888/chat` 접속
- 화면이 정상 렌더링되는지 확인

본 시연(브라우저 기준):
3) 같은 세션으로 연속 질문(문맥 유지)  
- `http://localhost:8888/chat?sessionTicket=<SESSION_TICKET_A>` 접속
- 문서셋 A 기준으로 질문 1회 후, 이어서 후속 질문 1회
- 앞 질문 맥락이 이어지는지 확인

4) 다른 세션으로 질문(세션 분리)  
- 새 탭에서 `http://localhost:8888/chat?sessionTicket=<SESSION_TICKET_B>` 접속
- 문서셋 B 기준으로 질문
- A 세션 맥락이 섞이지 않는지 확인

5) 기본 세션 fallback 확인  
- `http://localhost:8888/chat` (sessionTicket 없이) 접속
- 질문 후 기본 세션으로 동작하는지 확인

6) 세션 라우팅 결과 확인  
- `http://localhost:8888/chat/whoami?sessionTicket=<SESSION_TICKET_A>` 접속
- `sessionId`, `sessionKey` 확인
- `http://localhost:8888/chat/history?sessionTicket=<SESSION_TICKET_A>` 접속
- A 세션 히스토리만 조회되는지 확인

---

## 7. 기대효과 및 향후 개선

기대효과:
- 같은 문서셋 대화는 안정적으로 이어지고, 다른 문서셋은 분리되어 문맥 혼선 감소
- 세션 식별/라우팅 기준이 명확해져 운영 및 장애 분석 효율 향상
- 사용자 세션 이력 관리 부담을 줄여 서비스 확장 시 유지보수성 확보

향후 개선:
- 세션 라우팅 모니터링 강화(세션별 지표, 오류 추적 대시보드)
- 문서셋 특성에 맞춘 응답 정책 고도화(프롬프트/정책 파일 세분화)
