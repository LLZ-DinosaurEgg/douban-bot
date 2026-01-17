package com.douban.bot.service;

import com.douban.bot.config.AppConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmClient {

    private final AppConfig config;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();

    public String generateReply(String systemPrompt, String userPrompt) throws IOException {
        if (config.getLlmApiKey() == null || config.getLlmApiKey().isEmpty()) {
            throw new IOException("API密钥未配置");
        }

        ChatRequest request = new ChatRequest();
        request.setModel(config.getLlmModel());
        request.setMessages(List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
        ));
        request.setTemperature(config.getLlmTemperature());
        request.setMaxTokens(config.getLlmMaxTokens());

        try {
            String jsonRequest = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.getLlmApiBase() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getLlmApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new IOException("API请求失败，状态码: " + response.statusCode() + ", 响应: " + response.body());
            }

            ChatResponse chatResponse = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(response.body(), ChatResponse.class);

            if (chatResponse.getError() != null && chatResponse.getError().getMessage() != null) {
                throw new IOException("API错误: " + chatResponse.getError().getMessage());
            }

            if (chatResponse.getChoices() == null || chatResponse.getChoices().isEmpty()) {
                throw new IOException("未收到有效回复");
            }

            return chatResponse.getChoices().get(0).getMessage().getContent();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatRequest {
        private String model;
        private List<Message> messages;
        private Double temperature;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
    }

    @Data
    @NoArgsConstructor
    public static class ChatResponse {
        private List<Choice> choices;
        private Error error;

        @Data
        @NoArgsConstructor
        public static class Choice {
            private Message message;
        }

        @Data
        @NoArgsConstructor
        public static class Error {
            private String message;
        }
    }
}
