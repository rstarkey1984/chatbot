package com.example.chatbot.service;

import com.example.chatbot.controller.dto.ChatQueryRequest;
import com.example.chatbot.controller.dto.ChatQueryResponse;
import com.example.chatbot.common.exception.RateLimitExceededException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
public class ChatQueryService {
    // 상세 프롬프트 정책은 OpenClaw agent(chatbot)에서 관리하고,
    // 애플리케이션 코드는 최소 fallback만 유지합니다.
    private static final String SYSTEM_PROMPT = "제공된 참고데이터(ragContext)를 우선 근거로 답변하세요.";

    private final GatewayClient gatewayClient;
    private final ChatLogService chatLogService;
    private final TokenRateLimitService tokenRateLimitService;
    private final SessionRoutingService sessionRoutingService;
    private final Set<String> inFlightSessions = ConcurrentHashMap.newKeySet();

    public ChatQueryService(
            GatewayClient gatewayClient,
            ChatLogService chatLogService,
            TokenRateLimitService tokenRateLimitService,
            SessionRoutingService sessionRoutingService
    ) {
        this.gatewayClient = gatewayClient;
        this.chatLogService = chatLogService;
        this.tokenRateLimitService = tokenRateLimitService;
        this.sessionRoutingService = sessionRoutingService;
    }

    public ChatQueryResponse query(ChatQueryRequest request) {
        validate(request.question(), request.userName(), request.ragContext());

        SessionRoutingService.SessionContext context = sessionRoutingService.resolveContextNullable(request.sessionTicket());
        String inflightKey = toInflightKey(context.sessionId());
        if (!inFlightSessions.add(inflightKey)) {
            throw new IllegalArgumentException("같은 세션에서 이전 요청 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            enforceRateLimit(context.sessionId());

            String answer = gatewayClient.generateAnswer(
                    SYSTEM_PROMPT,
                    buildUserPrompt(request.question(), request.userName(), request.ragContext()),
                    context.sessionKey()
            );
            if (answer == null) answer = "";
            chatLogService.save("query-rag-only", inflightKey, request.question(), answer);
            return new ChatQueryResponse(answer);
        } finally {
            inFlightSessions.remove(inflightKey);
        }
    }

    public void streamQuery(ChatQueryRequest request, Consumer<String> onChunk) {
        validate(request.question(), request.userName(), request.ragContext());

        SessionRoutingService.SessionContext context = sessionRoutingService.resolveContextNullable(request.sessionTicket());
        String inflightKey = toInflightKey(context.sessionId());
        if (!inFlightSessions.add(inflightKey)) {
            throw new IllegalArgumentException("같은 세션에서 이전 요청 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            enforceRateLimit(context.sessionId());

            AtomicInteger chunkCount = new AtomicInteger(0);
            String raw = gatewayClient.streamAnswer(
                    SYSTEM_PROMPT,
                    buildUserPrompt(request.question(), request.userName(), request.ragContext()),
                    context.sessionKey(),
                    chunk -> {
                        if (onChunk != null && chunk != null && !chunk.isEmpty()) {
                            chunkCount.incrementAndGet();
                            onChunk.accept(chunk);
                        }
                    }
            );

            String answer = raw == null ? "" : raw;
            if (chunkCount.get() == 0 && onChunk != null && !answer.isBlank()) {
                onChunk.accept(answer);
            }
            chatLogService.save("query-rag-stream", inflightKey, request.question(), answer);
        } finally {
            inFlightSessions.remove(inflightKey);
        }
    }

    private void validate(String question, String userName, String ragContext) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("질문을 입력해 주세요.");
        }
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("사용자 이름을 입력해 주세요.");
        }
        if (ragContext == null || ragContext.isBlank()) {
            throw new IllegalArgumentException("학습 자료를 먼저 불러와 주세요.");
        }
    }

    private String buildUserPrompt(String question, String userName, String ragContext) {
        return "질문:\n" + question
                + "\n\n사용자 정보:\n"
                + "- 이름: " + userName
                + "\n\n참고데이터(ragContext):\n"
                + ragContext;
    }


    private void enforceRateLimit(String sessionId) {
        String rateLimitKey = sessionId == null ? "anonymous" : ("sid:" + sessionId);
        if (!tokenRateLimitService.tryAcquire(rateLimitKey)) {
            throw new RateLimitExceededException(
                    "요청이 너무 많습니다. 토큰당 분당 " + tokenRateLimitService.getRequestsPerMinute() + "회까지 요청할 수 있습니다."
            );
        }
    }

    private String toInflightKey(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "anonymous" : sessionId;
    }
}
