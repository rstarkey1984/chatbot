package com.example.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class AuthTokenService {
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String HMAC_ALG = "HmacSHA256";
    private static final String SESSION_ID_REGEX = "^[a-z0-9](?:[a-z0-9-]{1,62}[a-z0-9])?$";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.auth.token-secret}")
    private String tokenSecret;

    public String extractSessionId(String token) {
        return extractSessionIdFromAccess(token);
    }

    public String extractSessionIdFromAccess(String token) {
        JsonNode payload = parseAndVerify(token);

        String sessionId = payload.path("sid").asText("");
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("토큰의 세션 ID가 비어 있습니다.");
        }
        validateSessionId(sessionId);

        long exp = payload.path("exp").asLong(0L);
        long now = Instant.now().getEpochSecond();
        if (exp <= 0L || now >= exp) {
            throw new IllegalArgumentException("토큰이 만료되었습니다.");
        }

        return sessionId;
    }

    public String createToken(String sessionId, long expiresAtEpochSeconds) {
        return createAccessToken(sessionId, expiresAtEpochSeconds);
    }

    public String createAccessToken(String sessionId, long expiresAtEpochSeconds) {
        validateSessionId(sessionId);
        if (expiresAtEpochSeconds <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("만료시간은 현재 시각보다 커야 합니다.");
        }
        try {
            String payloadJson = objectMapper.writeValueAsString(Map.of(
                    "sid", sessionId,
                    "exp", expiresAtEpochSeconds
            ));
            String payloadPart = URL_ENCODER.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String sigPart = sign(payloadPart);
            return payloadPart + "." + sigPart;
        } catch (Exception e) {
            throw new IllegalStateException("토큰 생성에 실패했습니다", e);
        }
    }

    private JsonNode parseAndVerify(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("토큰이 필요합니다.");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("토큰 형식이 올바르지 않습니다.");
        }

        String payloadPart = parts[0];
        String signaturePart = parts[1];

        String expectedSig = sign(payloadPart);
        if (!constantTimeEquals(signaturePart, expectedSig)) {
            throw new IllegalArgumentException("토큰 서명이 유효하지 않습니다.");
        }

        try {
            String payloadJson = new String(URL_DECODER.decode(payloadPart), StandardCharsets.UTF_8);
            return objectMapper.readTree(payloadJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("토큰 해석에 실패했습니다.");
        }
    }

    private void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("세션 ID는 필수입니다.");
        }
        if (!sessionId.matches(SESSION_ID_REGEX)) {
            throw new IllegalArgumentException("세션 ID 형식이 올바르지 않습니다. (소문자/숫자/하이픈, 3~64자)");
        }
        if ("main".equals(sessionId) || "admin".equals(sessionId) || "root".equals(sessionId) || "default".equals(sessionId)) {
            throw new IllegalArgumentException("허용되지 않는 세션 ID입니다.");
        }
    }

    private String sign(String payloadPart) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            SecretKeySpec key = new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALG);
            mac.init(key);
            byte[] sig = mac.doFinal(payloadPart.getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("토큰 서명 처리에 실패했습니다", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int result = 0;
        for (int i = 0; i < x.length; i++) {
            result |= x[i] ^ y[i];
        }
        return result == 0;
    }
}
