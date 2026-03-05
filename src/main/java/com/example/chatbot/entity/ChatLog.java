package com.example.chatbot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_log")
public class ChatLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String mode;

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String question;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String answer;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getMode() { return mode; }
    public String getSessionId() { return sessionId; }
    public void setMode(String mode) { this.mode = mode; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
