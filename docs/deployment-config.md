# Deployment & Config

## 1) 애플리케이션 설정

주요 설정(`src/main/resources/application.yml`):

- `app.auth.token-secret`
- `app.gateway.default-session`
- `app.auth.issue-api-key`
- `app.auth.default-issue-ttl-seconds`
- `app.auth.max-issue-ttl-seconds`
- `app.gateway.url`
- `app.gateway.token`
- `app.gateway.config-path`
- `app.gateway.agent-id`

민감정보는 환경변수/시크릿 매니저로 주입합니다.

현재 원칙:
- Java `@Value`에는 기본값을 두지 않음
- 기본값은 `application.yml`에서만 관리
- 시크릿 값(`CHATBOT_DB_PASSWORD`, `CHATBOT_TOKEN_SECRET`, `OPENCLAW_GATEWAY_TOKEN`)은 코드/깃에 하드코딩 금지

## 2) OpenClaw Gateway

권장 점검 명령:

```bash
openclaw status --deep
openclaw security audit --deep
openclaw update status
```

## 3) Control UI / Origin 관련

Control UI 접속 오류 시 `origin not allowed` 로그를 우선 확인합니다.

필요 시 `~/.openclaw/openclaw.json`에 `gateway.controlUi.allowedOrigins`를 명시합니다.
예:

```json
{
  "gateway": {
    "controlUi": {
      "allowedOrigins": [
        "http://127.0.0.1:18789",
        "http://localhost:18789"
      ]
    }
  }
}
```

리버스 프록시 환경이라면 `gateway.trustedProxies`를 정확한 프록시 IP/CIDR로 설정합니다.

## 4) 배포 후 검증

1. `/health` 응답 확인
2. `/chat/ping-gateway` 응답 확인
3. 세션 분리 확인(서로 다른 sessionId로 요청)
4. 보안 감사 확인(critical/warn 여부)

## 5) 문서 운영 기준

- README: 원칙/아키텍처 중심
- 사용법(임베드 코드/빠른 시작): GitHub Release Notes에서 버전별 관리
