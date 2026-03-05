package com.example.chatbot.controller;

import com.example.chatbot.controller.dto.ChatQueryRequest;
import com.example.chatbot.controller.dto.ChatQueryResponse;
import com.example.chatbot.controller.dto.RawChatRequest;
import com.example.chatbot.controller.dto.TokenIssueRequest;
import com.example.chatbot.service.AuthTokenService;
import com.example.chatbot.service.ChatLogService;
import com.example.chatbot.service.ChatQueryService;
import com.example.chatbot.service.GatewayClient;
import com.example.chatbot.service.SessionRoutingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Controller
public class AppController {
    private final ChatQueryService chatQueryService;
    private final GatewayClient gatewayClient;
    private final AuthTokenService authTokenService;
    private final ChatLogService chatLogService;
    private final SessionRoutingService sessionRoutingService;

    @Value("${app.auth.issue-api-key}")
    private String issueApiKey;

    @Value("${app.auth.default-issue-ttl-seconds}")
    private long defaultIssueTtlSeconds;

    @Value("${app.auth.max-issue-ttl-seconds}")
    private long maxIssueTtlSeconds;

    public AppController(ChatQueryService chatQueryService, GatewayClient gatewayClient, AuthTokenService authTokenService, ChatLogService chatLogService, SessionRoutingService sessionRoutingService) {
        this.chatQueryService = chatQueryService;
        this.gatewayClient = gatewayClient;
        this.authTokenService = authTokenService;
        this.chatLogService = chatLogService;
        this.sessionRoutingService = sessionRoutingService;
    }

    @GetMapping("/health")
    @ResponseBody
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/chat")
    public String chat(
            @RequestParam(value = "sessionTicket", required = false) String sessionTicket,
            Model model
    ) {
        SessionRoutingService.SessionContext context = sessionRoutingService.resolveContextOrDefault(sessionTicket);
        model.addAttribute("userId", context.sessionId());
        model.addAttribute("sessionTicket", context.sessionTicket());
        return "chat";
    }

    @PostMapping("/chat/query")
    @ResponseBody
    public ResponseEntity<ChatQueryResponse> query(@Valid @RequestBody ChatQueryRequest request) {
        return ResponseEntity.ok(chatQueryService.query(request));
    }

    @PostMapping(value = "/chat/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter queryStream(@Valid @RequestBody ChatQueryRequest request) {
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                chatQueryService.streamQuery(request, chunk -> sendEvent(emitter, "chunk", chunk));
                sendEvent(emitter, "done", "[DONE]");
                emitter.complete();
            } catch (Exception e) {
                sendEvent(emitter, "error", e.getMessage() == null ? "응답 생성 중 오류가 발생했습니다." : e.getMessage());
                emitter.complete();
            }
        });

        return emitter;
    }

    @PostMapping("/chat/raw")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> raw(@Valid @RequestBody RawChatRequest request) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "RAW 모드는 비활성화되었습니다. /chat/query + ragContext를 사용하세요."
        ));
    }

    @GetMapping("/chat/ping-gateway")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> pingGateway(
            @RequestParam(value = "sessionTicket", required = false) String sessionTicket
    ) {
        SessionRoutingService.SessionContext context = sessionRoutingService.resolveContextOrDefault(sessionTicket);
        return ResponseEntity.ok(gatewayClient.pingGateway(context.sessionKey()));
    }

    @GetMapping("/chat/whoami")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> whoAmI(
            @RequestParam(value = "sessionTicket", required = false) String sessionTicket
    ) {
        SessionRoutingService.SessionContext context = sessionRoutingService.resolveContextOrDefault(sessionTicket);
        return ResponseEntity.ok(Map.of(
                "agentId", gatewayClient.getGatewayAgentId(),
                "sessionId", context.sessionId(),
                "sessionKey", context.sessionKey()
        ));
    }


    @GetMapping("/chat/history")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> history(
            @RequestParam(value = "sessionTicket", required = false) String sessionTicket
    ) {
        SessionRoutingService.SessionContext context = sessionRoutingService.resolveContextOrDefault(sessionTicket);
        return ResponseEntity.ok(Map.of(
                "sessionId", context.sessionId(),
                "items", chatLogService.recentHistory(context.sessionId())
        ));
    }

    @GetMapping("/session-ticket")
    public String sessionTicketPage() {
        return "token-issuer";
    }

    @PostMapping("/session-ticket/issue")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> issueToken(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey,
            @Valid @RequestBody TokenIssueRequest request
    ) {
        validateIssueApiKey(apiKey);

        long accessTtl = (request.ttlSeconds() == null || request.ttlSeconds() <= 0)
                ? defaultIssueTtlSeconds
                : request.ttlSeconds();

        if (accessTtl > maxIssueTtlSeconds) {
            throw new IllegalArgumentException("ttlSeconds가 허용 최대값을 초과했습니다.");
        }

        long now = Instant.now().getEpochSecond();
        long accessExp = now + accessTtl;

        String accessToken = authTokenService.createAccessToken(request.sessionId(), accessExp);

        return ResponseEntity.ok()
                .body(Map.of(
                        "sessionId", request.sessionId(),
                        "ttlSeconds", accessTtl,
                        "expiresAt", accessExp,
                        "sessionTicket", accessToken
                ));
    }

    private void validateIssueApiKey(String apiKey) {
        if (issueApiKey == null || issueApiKey.isBlank()) {
            return;
        }
        if (apiKey == null || !issueApiKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 API KEY입니다.");
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, String data) {
        try {
            String safeEvent = (eventName == null || eventName.isBlank()) ? "message" : eventName;
            emitter.send(SseEmitter.event().name(safeEvent).data(data == null ? "" : data));
        } catch (IOException ignored) {
        }
    }
}
