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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class GatewayClient {
    private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);

    private final WebClient webClient = WebClient.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
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

        try {
            String url = trimTrailingSlash(gatewayUrl) + "/v1/chat/completions";
            String requestModel = resolveGatewayModel();

            Map<String, Object> payload = Map.of(
                    "model", requestModel,
                    "stream", true,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.2
            );

            String payloadJson = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("x-openclaw-agent-id", gatewayAgentId)
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson));

            if (effectiveSessionKey != null && !effectiveSessionKey.isBlank()) {
                builder.header("x-openclaw-session-key", effectiveSessionKey);
            }
            String token = resolveGatewayToken();
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status != 200) {
                if (status == 401) return "게이트웨이 인증에 실패했습니다. 토큰 설정을 확인해 주세요.";
                if (status == 404) return "게이트웨이 채팅 엔드포인트가 비활성화되어 있습니다.";
                return "게이트웨이 호출에 실패했습니다. (HTTP " + status + ")";
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) continue;

                    JsonNode root = objectMapper.readTree(data);
                    JsonNode choices = root.path("choices");
                    if (!choices.isArray() || choices.isEmpty()) continue;
                    JsonNode delta = choices.get(0).path("delta");
                    if (!delta.hasNonNull("content")) continue;

                    String chunk = delta.get("content").asText();
                    full.append(chunk);
                    if (onChunk != null && !chunk.isEmpty()) {
                        onChunk.accept(chunk);
                    }
                }
            }

            if (full.isEmpty()) {
                return "응답 생성 실패";
            }
            return full.toString();
        } catch (Exception e) {
            return "게이트웨이 처리 중 오류가 발생했습니다: " + e.getMessage();
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
