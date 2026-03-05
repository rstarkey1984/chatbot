package com.example.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
public class GatewayClient {
    private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.gateway.url}")
    private String gatewayUrl;

    @Value("${app.gateway.token}")
    private String gatewayToken;

    @Value("${app.gateway.config-path}")
    private String gatewayConfigPath;

    @Value("${app.gateway.agent-id}")
    private String gatewayAgentId;

    @PostConstruct
    void validateGatewayAgentId() {
        if (gatewayAgentId == null || gatewayAgentId.isBlank()) {
            throw new IllegalStateException("app.gateway.agent-id는 필수입니다.");
        }
        if (!"chatbot".equals(gatewayAgentId)) {
            throw new IllegalStateException("app.gateway.agent-id는 chatbot만 허용됩니다.");
        }
    }

    public String getGatewayAgentId() {
        return gatewayAgentId;
    }

    public Map<String, Object> pingGateway(String sessionKey) {
        String url = trimTrailingSlash(gatewayUrl) + "/v1/chat/completions";
        HttpHeaders headers = buildHeaders(sessionKey, false);
        String resolvedToken = headers.getFirst(HttpHeaders.AUTHORIZATION);

        Map<String, Object> payload = Map.of(
                "model", resolveGatewayModel(),
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "temperature", 0
        );

        try {
            Map<String, Object> response = postForMap(url, headers, payload);
            String resolvedModel = extractResolvedModel(response);
            boolean routeMatched = isExpectedAgentRoute(resolvedModel);

            return Map.of(
                    "ok", routeMatched,
                    "url", url,
                    "agentId", gatewayAgentId,
                    "sessionKey", sessionKey == null ? "(none)" : sessionKey,
                    "hasToken", resolvedToken != null && !resolvedToken.isBlank(),
                    "responseModel", resolvedModel == null ? "(unknown)" : resolvedModel,
                    "routeMatched", routeMatched,
                    "error", routeMatched ? "" : "요청이 chatbot 에이전트로 라우팅되지 않았습니다.",
                    "responseKeys", response == null ? List.of() : response.keySet()
            );
        } catch (WebClientResponseException e) {
            return Map.of("ok", false, "url", url, "agentId", gatewayAgentId, "sessionKey", sessionKey == null ? "(none)" : sessionKey, "hasToken", resolvedToken != null && !resolvedToken.isBlank(), "status", e.getStatusCode().value(), "body", e.getResponseBodyAsString());
        } catch (Exception e) {
            return Map.of("ok", false, "url", url, "agentId", gatewayAgentId, "sessionKey", sessionKey == null ? "(none)" : sessionKey, "hasToken", resolvedToken != null && !resolvedToken.isBlank(), "error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public String generateAnswer(String systemPrompt, String userPrompt) {
        return generateAnswer(systemPrompt, userPrompt, null);
    }

    public String generateAnswer(String systemPrompt, String userPrompt, String sessionKeyOverride) {
        try {
            String url = trimTrailingSlash(gatewayUrl) + "/v1/chat/completions";
            String requestModel = resolveGatewayModel();
            String effectiveSessionKey = (sessionKeyOverride != null && !sessionKeyOverride.isBlank()) ? sessionKeyOverride : null;

            HttpHeaders headers = buildHeaders(effectiveSessionKey, false);
            Map<String, Object> payload = Map.of(
                    "model", requestModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.2
            );

            Map<String, Object> response = postForMap(url, headers, payload);
            if (response == null) return "응답 생성 실패";

            String resolvedModel = extractResolvedModel(response);
            if (!isExpectedAgentRoute(resolvedModel)) {
                log.error("[AGENT_ROUTE_MISMATCH] configuredAgent={}, responseModel={}, sessionKey={}", gatewayAgentId, resolvedModel, effectiveSessionKey);
                return "에이전트 라우팅 오류가 발생했습니다. 챗봇 라우팅 설정을 확인해 주세요.";
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) return "응답 생성 실패";
            Map<String, Object> first = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) first.get("message");
            return String.valueOf(message.get("content"));
        } catch (WebClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 401) return "게이트웨이 인증에 실패했습니다. 토큰 설정을 확인해 주세요.";
            if (code == 404) return "게이트웨이 채팅 엔드포인트가 비활성화되어 있습니다.";
            return "게이트웨이 호출에 실패했습니다. (HTTP " + code + ")";
        } catch (Exception e) {
            return "게이트웨이 처리 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    @SuppressWarnings("null")
    public String streamAnswer(String systemPrompt, String userPrompt, String sessionKeyOverride, Consumer<String> onChunk) {
        String effectiveSessionKey = (sessionKeyOverride != null && !sessionKeyOverride.isBlank()) ? sessionKeyOverride : null;
        StringBuilder full = new StringBuilder();
        AtomicReference<String> resolvedModelRef = new AtomicReference<>(null);
        StringBuilder sseBuffer = new StringBuilder();

        try {
            String url = trimTrailingSlash(gatewayUrl) + "/v1/chat/completions";
            String requestModel = resolveGatewayModel();

            HttpHeaders headers = buildHeaders(effectiveSessionKey, true);
            Map<String, Object> payload = Map.of(
                    "model", requestModel,
                    "stream", true,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.2
            );

            webClient.post()
                    .uri(url)
                    .headers(h -> h.addAll(headers))
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(part -> {
                        if (part == null || part.isEmpty()) return;
                        sseBuffer.append(part);
                        consumeSseBuffer(sseBuffer, resolvedModelRef, full, onChunk);
                    })
                    .blockLast();

            consumeSseBuffer(sseBuffer, resolvedModelRef, full, onChunk);

            if (full.isEmpty()) {
                return "응답 생성 실패";
            }
            return full.toString();
        } catch (WebClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 401) return "게이트웨이 인증에 실패했습니다. 토큰 설정을 확인해 주세요.";
            if (code == 404) return "게이트웨이 채팅 엔드포인트가 비활성화되어 있습니다.";
            return "게이트웨이 호출에 실패했습니다. (HTTP " + code + ")";
        } catch (Exception e) {
            return "게이트웨이 처리 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

    private void consumeSseBuffer(StringBuilder sseBuffer, AtomicReference<String> resolvedModelRef, StringBuilder full, Consumer<String> onChunk) {
        int newlineIdx;
        while ((newlineIdx = sseBuffer.indexOf("\n")) >= 0) {
            String line = sseBuffer.substring(0, newlineIdx).trim();
            sseBuffer.delete(0, newlineIdx + 1);
            processSseLine(line, resolvedModelRef, full, onChunk);
        }

        // tail은 완전한 JSON처럼 보일 때만 시도하고, 실패하면 버리지 않는다.
        String tail = sseBuffer.toString().trim();
        if (!tail.isEmpty() && (tail.startsWith("data: {") || (tail.startsWith("{") && tail.endsWith("}")))) {
            boolean consumed = processSseLine(tail, resolvedModelRef, full, onChunk);
            if (consumed) {
                sseBuffer.setLength(0);
            }
        }
    }

    private boolean processSseLine(String line, AtomicReference<String> resolvedModelRef, StringBuilder full, Consumer<String> onChunk) {
        if (line == null || line.isEmpty()) {
            return false;
        }

        String data = line;
        if (line.startsWith("data:")) {
            data = line.substring(5).trim();
        }
        if (data.isEmpty() || "[DONE]".equals(data) || !data.startsWith("{")) {
            return false;
        }

        try {
            JsonNode root = objectMapper.readTree(data);
            if (resolvedModelRef.get() == null && root.hasNonNull("model")) {
                resolvedModelRef.set(root.get("model").asText());
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return true;
            JsonNode delta = choices.get(0).path("delta");
            if (!delta.hasNonNull("content")) return true;
            String chunk = delta.get("content").asText();
            full.append(chunk);
            if (onChunk != null && !chunk.isEmpty()) {
                onChunk.accept(chunk);
            }
            return true;
        } catch (Exception ignore) {
            // ignore malformed partial chunks
            return false;
        }
    }

    @SuppressWarnings("null")
    private HttpHeaders buildHeaders(String sessionKey, boolean stream) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (stream) {
            headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        }
        headers.set("x-openclaw-agent-id", gatewayAgentId);
        if (sessionKey != null && !sessionKey.isBlank()) {
            headers.set("x-openclaw-session-key", sessionKey);
        }
        String resolvedToken = resolveGatewayToken();
        if (resolvedToken != null && !resolvedToken.isBlank()) {
            headers.setBearerAuth(resolvedToken);
        }
        return headers;
    }

    private Map<String, Object> postForMap(String url, HttpHeaders headers, Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        @SuppressWarnings("null")
        Map<String, Object> response = webClient.post()
                .uri(url)
                .headers(h -> h.addAll(headers))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        return response == null ? new LinkedHashMap<>() : response;
    }

    private String extractResolvedModel(Map<String, Object> response) {
        if (response == null) return null;
        Object model = response.get("model");
        return model == null ? null : String.valueOf(model);
    }

    private boolean isExpectedAgentRoute(String resolvedModel) {
        if (resolvedModel == null || resolvedModel.isBlank()) return false;
        String expected = "openclaw:" + gatewayAgentId;
        return expected.equalsIgnoreCase(resolvedModel.trim());
    }

    private String resolveGatewayToken() {
        if (gatewayToken != null && !gatewayToken.isBlank()) {
            return gatewayToken;
        }
        try {
            String rawPath = gatewayConfigPath == null || gatewayConfigPath.isBlank()
                    ? "~/.openclaw/openclaw.json"
                    : gatewayConfigPath;
            String home = System.getProperty("user.home");
            String expanded = rawPath.startsWith("~/") ? home + rawPath.substring(1) : rawPath;
            Path path = Paths.get(expanded);
            if (!Files.exists(path)) return null;

            JsonNode root = objectMapper.readTree(Files.readString(path));
            JsonNode tokenNode = root.path("gateway").path("auth").path("token");
            if (tokenNode.isMissingNode() || tokenNode.isNull()) return null;
            String token = tokenNode.asText();
            return token == null || token.isBlank() ? null : token;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveGatewayModel() {
        if (gatewayAgentId == null || gatewayAgentId.isBlank()) return "openclaw";
        return "openclaw:" + gatewayAgentId;
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
