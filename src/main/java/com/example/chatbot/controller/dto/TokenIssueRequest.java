package com.example.chatbot.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TokenIssueRequest(
        @NotBlank(message = "세션 ID를 입력해 주세요.") String sessionId,
        @Min(value = 1, message = "유효 시간은 1초 이상이어야 합니다.") Long ttlSeconds
) {
}
