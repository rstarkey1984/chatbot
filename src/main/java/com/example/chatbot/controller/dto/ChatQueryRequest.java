package com.example.chatbot.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatQueryRequest(
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 100, message = "질문은 100자 이내로 입력해 주세요.")
        String question,

        @NotBlank(message = "사용자 이름을 입력해 주세요.")
        @Size(max = 20, message = "사용자 이름은 20자 이내로 입력해 주세요.")
        String userName,

        @NotBlank(message = "학습 자료를 먼저 불러와 주세요.")
        @Size(max = 4000, message = "학습 자료는 4000자 이내로 입력해 주세요.")
        String ragContext,

        String sessionTicket
) {}
