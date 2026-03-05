package com.example.chatbot.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record RawChatRequest(
        @NotBlank(message = "질문을 입력해 주세요.") String question
) {}
