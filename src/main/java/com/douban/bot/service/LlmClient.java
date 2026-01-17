package com.douban.bot.service;

import com.douban.bot.config.AppConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
        return generateReply(systemPrompt, userPrompt, null, null, null, null, null);
    }
    
    public String generateReply(String systemPrompt, String userPrompt, 
                                String apiBase, String apiKey, String model, 
                                Double temperature, Integer maxTokens) throws IOException {
        // 使用传入的配置，如果没有则使用 AppConfig 的默认值
        String useApiBase = (apiBase != null && !apiBase.trim().isEmpty()) 
                ? apiBase 
                : (config.getLlmApiBase() != null && !config.getLlmApiBase().trim().isEmpty() 
                    ? config.getLlmApiBase() 
                    : "https://api.openai.com/v1");
        String useApiKey = (apiKey != null && !apiKey.trim().isEmpty()) 
                ? apiKey 
                : config.getLlmApiKey();
        String useModel = (model != null && !model.trim().isEmpty()) 
                ? model 
                : config.getLlmModel();
        Double useTemperature = temperature != null ? temperature : config.getLlmTemperature();
        Integer useMaxTokens = maxTokens != null ? maxTokens : config.getLlmMaxTokens();
        
        if (useApiKey == null || useApiKey.isEmpty()) {
            throw new IOException("API密钥未配置");
        }
        
        if (useApiBase == null || useApiBase.trim().isEmpty()) {
            throw new IOException("API Base URL未配置");
        }

        ChatRequest request = new ChatRequest();
        request.setModel(useModel);
        request.setMessages(List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
        ));
        request.setTemperature(useTemperature);
        request.setMaxTokens(useMaxTokens);

        try {
            String jsonRequest = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request);
            String apiUrl = useApiBase.endsWith("/") 
                    ? useApiBase + "chat/completions" 
                    : useApiBase + "/chat/completions";
            
            log.debug("调用LLM API: url={}, model={}", apiUrl, useModel);
            
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + useApiKey)
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatResponse {
        private List<Choice> choices;
        private Error error;

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Choice {
            private Message message;
            private String finishReason;
        }

        @Data
        @NoArgsConstructor
        public static class Error {
            private String message;
        }
    }
}
