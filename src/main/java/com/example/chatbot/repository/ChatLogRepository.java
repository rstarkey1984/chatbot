package com.example.chatbot.repository;

import com.example.chatbot.entity.ChatLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {
    List<ChatLog> findTop20BySessionIdOrderByCreatedAtDesc(String sessionId);
}
