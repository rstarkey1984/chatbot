package com.example.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SessionRoutingService {
    public record SessionContext(String sessionTicket, String sessionId, String sessionKey) {}
    private final AuthTokenService authTokenService;

    @Value("${app.gateway.default-session}")
    private String defaultSession;

    @Value("${app.auth.default-issue-ttl-seconds}")
    private long defaultIssueTtlSeconds;

    @Value("${app.gateway.session-key-prefix}")
    private String sessionKeyPrefix;

    public SessionRoutingService(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    public String resolveSessionTicketOrDefault(String sessionTicket) {
        if (sessionTicket != null && !sessionTicket.isBlank()) {
            return sessionTicket;
        }
        String raw = defaultSession == null ? "" : defaultSession.trim();
        if (raw.isBlank()) {
            throw new IllegalArgumentException("sessionTicket이 필요합니다.");
        }
        if (raw.contains(".")) {
            return raw;
        }
        long exp = Instant.now().getEpochSecond() + defaultIssueTtlSeconds;
        return authTokenService.createAccessToken(raw, exp);
    }

    public SessionContext resolveContextOrDefault(String sessionTicket) {
        String effectiveTicket = resolveSessionTicketOrDefault(sessionTicket);
        String sessionId = authTokenService.extractSessionIdFromAccess(effectiveTicket);
        return new SessionContext(effectiveTicket, sessionId, toSessionKeyOrNull(sessionId));
    }

    public SessionContext resolveContextNullable(String sessionTicket) {
        String sessionId = extractSessionIdNullable(sessionTicket);
        return new SessionContext(sessionTicket, sessionId, toSessionKeyOrNull(sessionId));
    }

    public String extractSessionIdNullable(String sessionTicket) {
        if (sessionTicket == null || sessionTicket.isBlank()) {
            return null;
        }
        return authTokenService.extractSessionId(sessionTicket);
    }

    public String toSessionKeyOrNull(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        String safeSessionId = sessionId.replaceAll("[^a-z0-9-]", "-");
        return normalizeSessionKeyPrefix() + safeSessionId;
    }

    private String normalizeSessionKeyPrefix() {
        String prefix = sessionKeyPrefix == null ? "" : sessionKeyPrefix.trim();
        if (prefix.isBlank()) {
            throw new IllegalStateException("app.gateway.session-key-prefix는 비어 있을 수 없습니다.");
        }
        if (!prefix.endsWith(":")) {
            prefix = prefix + ":";
        }
        if (!prefix.matches("^[a-zA-Z0-9:_-]+$")) {
            throw new IllegalStateException("app.gateway.session-key-prefix 형식이 올바르지 않습니다.");
        }
        return prefix;
    }
}
