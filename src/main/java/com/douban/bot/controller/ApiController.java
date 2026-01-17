package com.douban.bot.controller;

import com.douban.bot.config.AppConfig;
import com.douban.bot.db.BotConfigDao;
import com.douban.bot.db.RepositoryService;
import com.douban.bot.model.Comment;
import com.douban.bot.model.Group;
import com.douban.bot.model.Post;
import com.douban.bot.service.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final RepositoryService repository;
    private final AppConfig appConfig;
    private final LlmClient llmClient;
    private final Jdbi jdbi;

    @GetMapping("/groups")
    public ResponseEntity<Map<String, Object>> getGroups() {
        try {
            List<Group> groups = repository.getAllGroups();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", groups);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取小组失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/posts")
    public ResponseEntity<Map<String, Object>> getPosts(
            @RequestParam(required = false) String group_id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        try {
            if (page < 1) page = 1;
            if (page_size < 1 || page_size > 100) page_size = 20;

            List<Post> posts = repository.getPostsWithPagination(group_id, page, page_size);
            int total = repository.getPostsCount(group_id);
            int pages = (total + page_size - 1) / page_size;

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", posts);
            Map<String, Object> pagination = new HashMap<>();
            pagination.put("page", page);
            pagination.put("page_size", page_size);
            pagination.put("total", total);
            pagination.put("pages", pages);
            response.put("pagination", pagination);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取帖子失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<Map<String, Object>> getPost(@PathVariable String postId) {
        try {
            Post post = repository.getPostByPostID(postId);
            if (post == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Post not found");
                return ResponseEntity.status(404).body(response);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", post);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取帖子失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/comments/{postId}")
    public ResponseEntity<Map<String, Object>> getComments(@PathVariable String postId) {
        try {
            List<Comment> comments = repository.getCommentsByPostID(postId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", comments);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取评论失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = repository.getStats();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", stats);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取统计信息失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/bot/config")
    public ResponseEntity<Map<String, Object>> getBotConfig() {
        try {
            // 优先从数据库读取配置
            BotConfigDao.BotConfigRow botConfigRow = jdbi.withExtension(BotConfigDao.class, BotConfigDao::findById);
            
            Map<String, Object> config = new HashMap<>();
            if (botConfigRow != null) {
                // 从数据库读取
                config.put("enabled", botConfigRow.enabled());
                config.put("apiType", botConfigRow.llmApiType());
                config.put("apiBase", botConfigRow.llmApiBase());
                config.put("hasApiKey", botConfigRow.llmApiKey() != null && !botConfigRow.llmApiKey().isEmpty());
                config.put("apiKey", botConfigRow.llmApiKey() != null && !botConfigRow.llmApiKey().isEmpty() ? "****" : "");
                config.put("model", botConfigRow.llmModel());
                config.put("temperature", botConfigRow.llmTemperature());
                config.put("maxTokens", botConfigRow.llmMaxTokens());
                
                // 解析回复关键词
                List<String> replyKeywords = List.of();
                if (botConfigRow.replyKeywords() != null && !botConfigRow.replyKeywords().isEmpty()) {
                    try {
                        replyKeywords = BotConfigDao.objectMapper.readValue(
                                botConfigRow.replyKeywords(), 
                                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}
                        );
                    } catch (Exception e) {
                        // 解析失败，使用空列表
                    }
                }
                config.put("replyKeywords", replyKeywords);
                
                config.put("minReplyDelay", botConfigRow.minReplyDelay());
                config.put("maxReplyDelay", botConfigRow.maxReplyDelay());
                config.put("maxHistoryPosts", botConfigRow.maxHistoryPosts());
                config.put("maxHistoryComments", botConfigRow.maxHistoryComments());
                config.put("enableStyleLearning", botConfigRow.enableStyleLearning() != null && botConfigRow.enableStyleLearning());
                config.put("customPrompt", botConfigRow.customPrompt() != null ? botConfigRow.customPrompt() : "");
            } else {
                // 从 AppConfig 读取（兼容旧配置）
                config.put("enabled", appConfig.getCrawlerBot() != null && appConfig.getCrawlerBot());
                config.put("apiType", appConfig.getLlmApiType());
                config.put("apiBase", appConfig.getLlmApiBase());
                config.put("hasApiKey", appConfig.getLlmApiKey() != null && !appConfig.getLlmApiKey().isEmpty());
                config.put("apiKey", "");
                config.put("model", appConfig.getLlmModel());
                config.put("temperature", appConfig.getLlmTemperature());
                config.put("maxTokens", appConfig.getLlmMaxTokens());
                config.put("replyKeywords", appConfig.getCrawlerReplyKeywords() != null 
                        ? appConfig.getCrawlerReplyKeywords() 
                        : List.of());
                config.put("minReplyDelay", appConfig.getCrawlerMinReplyDelay());
                config.put("maxReplyDelay", appConfig.getCrawlerMaxReplyDelay());
                config.put("maxHistoryPosts", appConfig.getCrawlerMaxHistoryPosts());
                config.put("maxHistoryComments", appConfig.getCrawlerMaxHistoryComments());
                config.put("enableStyleLearning", true); // 默认开启
                config.put("customPrompt", ""); // 默认为空
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", config);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取机器人配置失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/bot/test")
    public ResponseEntity<Map<String, Object>> testBotReply(
            @RequestBody Map<String, String> request) {
        String title = request.get("title");
        String content = request.get("content");
        String groupId = request.get("groupId");
        
        log.info("收到测试回复生成请求: title={}, contentLength={}, groupId={}", 
                title, content != null ? content.length() : 0, groupId);
        
        try {
            if (title == null || title.isEmpty()) {
                log.warn("测试回复生成失败: 帖子标题不能为空");
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "帖子标题不能为空");
                return ResponseEntity.status(400).body(response);
            }

            if (content == null || content.isEmpty()) {
                log.warn("测试回复生成失败: 帖子内容不能为空");
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "帖子内容不能为空");
                return ResponseEntity.status(400).body(response);
            }

            // 读取机器人配置
            BotConfigDao.BotConfigRow botConfig = jdbi.withExtension(BotConfigDao.class, BotConfigDao::findById);
            boolean enableStyleLearning = botConfig != null && botConfig.enableStyleLearning() != null ? botConfig.enableStyleLearning() : true;
            String customPrompt = botConfig != null && botConfig.customPrompt() != null ? botConfig.customPrompt() : "";
            
            // 构建系统提示词
            String systemPrompt;
            
            // 如果配置了自定义 prompt，优先使用自定义 prompt
            if (customPrompt != null && !customPrompt.trim().isEmpty()) {
                systemPrompt = customPrompt.trim();
                log.debug("使用自定义 prompt: length={}", systemPrompt.length());
            } else if (enableStyleLearning && groupId != null && !groupId.isEmpty()) {
                // 如果启用学习风格且有小组ID，则学习小组风格
                Group group = repository.getGroupById(groupId);
                if (group != null) {
                    log.debug("加载小组历史评论以学习风格: groupId={}, groupName={}", groupId, group.getName());
                    int maxComments = botConfig != null && botConfig.maxHistoryComments() != null 
                            ? botConfig.maxHistoryComments() 
                            : (appConfig.getCrawlerMaxHistoryComments() != null ? appConfig.getCrawlerMaxHistoryComments() : 200);
                    List<Comment> comments = repository.getCommentsByGroupId(groupId, maxComments);
                    String style = comments.stream()
                            .limit(10)
                            .map(Comment::getContent)
                            .filter(c -> c != null && c.length() > 5)
                            .collect(Collectors.joining("\n"));
                    systemPrompt = String.format("你是一个豆瓣小组%s的成员。请根据以下示例评论的风格，生成一个自然、友好的回复。\n示例评论风格：\n%s\n请保持相似的语气和风格。", 
                            group.getName(), style.isEmpty() ? "友好、自然" : style);
                    log.debug("已加载{}条历史评论用于风格学习", comments.size());
                } else {
                    log.warn("小组不存在: groupId={}, 使用默认提示词", groupId);
                    systemPrompt = "你是一个豆瓣小组的成员，请生成一个自然、友好的回复。";
                }
            } else {
                // 未启用学习风格或未提供小组ID，使用默认提示词
                if (!enableStyleLearning) {
                    log.debug("学习风格已禁用, 使用默认提示词");
                } else {
                    log.debug("未提供小组ID, 使用默认提示词");
                }
                systemPrompt = "你是一个豆瓣小组的成员，请生成一个自然、友好的回复。";
            }

            // 构建用户提示词
            String userPrompt = "请为以下帖子生成一个符合小组风格的回复：\n标题：" + title + "\n内容：" + content;
            
            log.info("开始调用LLM生成回复: model={}, systemPromptLength={}, userPromptLength={}", 
                    appConfig.getLlmModel(), systemPrompt.length(), userPrompt.length());

            // 生成回复
            String reply = llmClient.generateReply(systemPrompt, userPrompt);
            
            log.info("回复生成成功: replyLength={}", reply != null ? reply.length() : 0);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", Map.of("reply", reply));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("测试回复生成失败: title={}, contentLength={}, groupId={}", 
                    title, content != null ? content.length() : 0, groupId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "生成回复失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/bot/config")
    public ResponseEntity<Map<String, Object>> updateBotConfig(@RequestBody Map<String, Object> request) {
        try {
            // 读取当前配置
            BotConfigDao.BotConfigRow current = jdbi.withExtension(BotConfigDao.class, BotConfigDao::findById);
            
            // 读取当前值
            boolean enabledValue = current != null ? current.enabled() : false;
            String llmApiTypeValue = current != null ? current.llmApiType() : "openai";
            String llmApiBaseValue = current != null ? current.llmApiBase() : "https://api.openai.com/v1";
            String llmApiKeyValue = current != null ? current.llmApiKey() : "";
            String llmModelValue = current != null ? current.llmModel() : "gpt-3.5-turbo";
            Double llmTemperatureValue = current != null ? current.llmTemperature() : 0.7;
            Integer llmMaxTokensValue = current != null ? current.llmMaxTokens() : 500;
            String replyKeywordsValue = current != null ? current.replyKeywords() : "[]";
            Integer minReplyDelayValue = current != null ? current.minReplyDelay() : 30;
            Integer maxReplyDelayValue = current != null ? current.maxReplyDelay() : 300;
            Integer maxHistoryPostsValue = current != null ? current.maxHistoryPosts() : 50;
            Integer maxHistoryCommentsValue = current != null ? current.maxHistoryComments() : 200;
            boolean enableStyleLearningValue = current != null && current.enableStyleLearning() != null ? current.enableStyleLearning() : true;
            String customPromptValue = current != null && current.customPrompt() != null ? current.customPrompt() : "";
            
            // 更新值
            if (request.containsKey("enabled")) {
                enabledValue = Boolean.valueOf(request.get("enabled").toString());
            }
            
            if (request.containsKey("apiType")) {
                llmApiTypeValue = request.get("apiType").toString();
            }
            
            if (request.containsKey("apiBase")) {
                llmApiBaseValue = request.get("apiBase").toString();
            }
            
            if (request.containsKey("apiKey") && request.get("apiKey") != null) {
                String newApiKey = request.get("apiKey").toString();
                // 如果 API Key 不为空且不是掩码字符串，则更新
                // 只有当用户实际输入了新值时才更新，空字符串或占位符则保留原有值
                if (newApiKey != null && !newApiKey.isEmpty() && !newApiKey.equals("****") && !newApiKey.equals("null")) {
                    llmApiKeyValue = newApiKey;
                    log.debug("更新 API Key: 长度={}", newApiKey.length());
                } else {
                    log.debug("保留原有 API Key（未提供新值或值为空/占位符）");
                }
            } else {
                log.debug("未提供 apiKey 字段，保留原有 API Key");
            }
            
            if (request.containsKey("model")) {
                llmModelValue = request.get("model").toString();
            }
            
            if (request.containsKey("temperature")) {
                llmTemperatureValue = Double.valueOf(request.get("temperature").toString());
            }
            
            if (request.containsKey("maxTokens")) {
                llmMaxTokensValue = Integer.valueOf(request.get("maxTokens").toString());
            }
            
            if (request.containsKey("replyKeywords")) {
                Object keywordsObj = request.get("replyKeywords");
                if (keywordsObj instanceof String) {
                    String keywordsStr = (String) keywordsObj;
                    List<String> keywords;
                    if (keywordsStr.isEmpty()) {
                        keywords = List.of();
                    } else {
                        keywords = List.of(keywordsStr.split(",")).stream()
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(java.util.stream.Collectors.toList());
                    }
                    replyKeywordsValue = BotConfigDao.objectMapper.writeValueAsString(keywords);
                } else if (keywordsObj instanceof List) {
                    replyKeywordsValue = BotConfigDao.objectMapper.writeValueAsString(keywordsObj);
                }
            }
            
            if (request.containsKey("minReplyDelay")) {
                minReplyDelayValue = Integer.valueOf(request.get("minReplyDelay").toString());
            }
            
            if (request.containsKey("maxReplyDelay")) {
                maxReplyDelayValue = Integer.valueOf(request.get("maxReplyDelay").toString());
            }
            
            if (request.containsKey("maxHistoryPosts")) {
                maxHistoryPostsValue = Integer.valueOf(request.get("maxHistoryPosts").toString());
            }
            
            if (request.containsKey("maxHistoryComments")) {
                maxHistoryCommentsValue = Integer.valueOf(request.get("maxHistoryComments").toString());
            }
            
            if (request.containsKey("enableStyleLearning")) {
                enableStyleLearningValue = Boolean.valueOf(request.get("enableStyleLearning").toString());
            }
            
            if (request.containsKey("customPrompt")) {
                customPromptValue = request.get("customPrompt") != null ? request.get("customPrompt").toString() : "";
            }
            
            // 提取到 final 变量以便在 lambda 中使用
            final boolean enabled = enabledValue;
            final String llmApiType = llmApiTypeValue;
            final String llmApiBase = llmApiBaseValue;
            final String llmApiKey = llmApiKeyValue;
            final String llmModel = llmModelValue;
            final Double llmTemperature = llmTemperatureValue;
            final Integer llmMaxTokens = llmMaxTokensValue;
            final String replyKeywords = replyKeywordsValue;
            final Integer minReplyDelay = minReplyDelayValue;
            final Integer maxReplyDelay = maxReplyDelayValue;
            final Integer maxHistoryPosts = maxHistoryPostsValue;
            final Integer maxHistoryComments = maxHistoryCommentsValue;
            final boolean enableStyleLearning = enableStyleLearningValue;
            final String customPrompt = customPromptValue;
            
            // 保存到数据库
            final String updatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            jdbi.useExtension(BotConfigDao.class, dao -> 
                dao.update(enabled, llmApiType, llmApiBase, llmApiKey, llmModel, llmTemperature, 
                        llmMaxTokens, replyKeywords, minReplyDelay, maxReplyDelay, 
                        maxHistoryPosts, maxHistoryComments, enableStyleLearning, customPrompt, updatedAt)
            );
            
            // 同步更新 AppConfig（使配置立即生效）
            appConfig.setCrawlerBot(enabled);
            appConfig.setLlmApiType(llmApiType);
            appConfig.setLlmApiBase(llmApiBase);
            if (!llmApiKey.isEmpty()) {
                appConfig.setLlmApiKey(llmApiKey);
            }
            appConfig.setLlmModel(llmModel);
            appConfig.setLlmTemperature(llmTemperature);
            appConfig.setLlmMaxTokens(llmMaxTokens);
            try {
                List<String> keywords = BotConfigDao.objectMapper.readValue(
                        replyKeywords, 
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}
                );
                appConfig.setCrawlerReplyKeywords(keywords);
            } catch (Exception e) {
                appConfig.setCrawlerReplyKeywords(List.of());
            }
            appConfig.setCrawlerMinReplyDelay(minReplyDelay);
            appConfig.setCrawlerMaxReplyDelay(maxReplyDelay);
            appConfig.setCrawlerMaxHistoryPosts(maxHistoryPosts);
            appConfig.setCrawlerMaxHistoryComments(maxHistoryComments);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "配置更新成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "更新配置失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
