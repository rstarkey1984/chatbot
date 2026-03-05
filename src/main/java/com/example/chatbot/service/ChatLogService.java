package com.example.chatbot.service;

import com.example.chatbot.entity.ChatLog;
import com.example.chatbot.repository.ChatLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChatLogService {
    private final ChatLogRepository chatLogRepository;

    public ChatLogService(ChatLogRepository chatLogRepository) {
        this.chatLogRepository = chatLogRepository;
    }

    public void save(String mode, String sessionId, String question, String answer) {
        ChatLog log = new ChatLog();
        log.setMode(mode);
        log.setSessionId(sessionId == null || sessionId.isBlank() ? "anonymous" : sessionId);
        log.setQuestion(question);
        log.setAnswer(answer);
        chatLogRepository.save(log);
    }

    public List<Map<String, String>> recentHistory(String sessionId) {
        String safeSessionId = (sessionId == null || sessionId.isBlank()) ? "anonymous" : sessionId;
        return chatLogRepository.findTop20BySessionIdOrderByCreatedAtDesc(safeSessionId)
                .stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(log -> Map.of(
                        "question", log.getQuestion() == null ? "" : log.getQuestion(),
                        "answer", log.getAnswer() == null ? "" : log.getAnswer()
                ))
                .toList();
    }
}
