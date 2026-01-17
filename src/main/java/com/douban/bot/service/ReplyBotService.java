package com.douban.bot.service;

import com.douban.bot.config.AppConfig;
import com.douban.bot.db.BotConfigDao;
import com.douban.bot.db.RepositoryService;
import com.douban.bot.model.Comment;
import com.douban.bot.model.Group;
import com.douban.bot.model.Post;
import com.douban.bot.service.DoubanApiService;
import com.douban.bot.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplyBotService {

    private final RepositoryService repository;
    private final LlmClient llmClient;
    private final Jdbi jdbi;
    private final AppConfig appConfig;
    private final DoubanApiService doubanApiService;
    
    /**
     * 检查帖子是否已回复
     */
    public boolean hasReplied(Post post) {
        return post.getBotReplied() != null && post.getBotReplied();
    }

    @Async
    public void processNewPosts(String groupId) {
        log.info("开始处理自动回复检查: groupId={}", groupId);
        
        // 从数据库读取机器人配置
        BotConfigDao.BotConfigRow botConfig = jdbi.withExtension(BotConfigDao.class, BotConfigDao::findById);
        if (botConfig == null) {
            log.warn("机器人配置不存在，跳过自动回复: groupId={}", groupId);
            return;
        }
        if (!botConfig.enabled()) {
            log.warn("机器人未启用，跳过自动回复: groupId={}, enabled={}", groupId, botConfig.enabled());
            return;
        }
        log.info("机器人已启用，继续处理: groupId={}", groupId);

        try {
            Group group = repository.getGroupById(groupId);
            if (group == null) {
                log.error("小组不存在: {}", groupId);
                return;
            }

            // 构建系统提示词（支持自定义 prompt 和学习风格开关）
            String systemPrompt = buildSystemPrompt(group);

            // 获取最近匹配的帖子
            List<Post> posts = repository.getPostsByGroupId(groupId, 50);
            log.info("获取到 {} 个帖子，开始检查是否需要回复: groupId={}", posts.size(), groupId);
            
            int checkedCount = 0;
            int shouldReplyCount = 0;
            int alreadyRepliedCount = 0;
            
            for (Post post : posts) {
                checkedCount++;
                if (!shouldReply(post)) {
                    log.debug("帖子不需要回复: postId={}, isMatched={}", post.getPostId(), post.getIsMatched());
                    continue;
                }
                shouldReplyCount++;

                // 检查是否已经回复过
                if (hasReplied(post)) {
                    alreadyRepliedCount++;
                    log.debug("帖子已回复过，跳过: postId={}", post.getPostId());
                    continue;
                }
                
                log.info("准备为帖子生成回复: postId={}, title={}", post.getPostId(), post.getTitle());

                // 生成回复
                try {
                    String userPrompt = "请为以下帖子生成一个符合小组风格的回复：\n标题：" + post.getTitle() + "\n内容：" + post.getContent();
                    
                    // 使用数据库中的机器人配置来调用LLM
                    String reply = llmClient.generateReply(
                            systemPrompt, 
                            userPrompt,
                            botConfig.llmApiBase(),
                            botConfig.llmApiKey(),
                            botConfig.llmModel(),
                            botConfig.llmTemperature(),
                            botConfig.llmMaxTokens()
                    );
                    log.info("为帖子 {} 生成回复: {}", post.getPostId(), reply);
                    
                    // 获取机器人配置的cookie，用于发送评论
                    // 如果机器人配置中有cookie，优先使用；否则使用全局配置的cookie
                    String botCookie = botConfig.cookie() != null && !botConfig.cookie().trim().isEmpty() 
                            ? botConfig.cookie() 
                            : appConfig.getCookie();
                    
                    // 实际发送回复到豆瓣
                    boolean commentSent = false;
                    if (botCookie != null && !botCookie.trim().isEmpty()) {
                        try {
                            commentSent = doubanApiService.postComment(post.getPostId(), reply, botCookie);
                            if (commentSent) {
                                log.info("评论已成功发送到豆瓣: postId={}", post.getPostId());
                            } else {
                                log.warn("评论发送失败: postId={}", post.getPostId());
                            }
                        } catch (Exception e) {
                            log.error("发送评论到豆瓣时发生异常: postId={}, error={}", 
                                    post.getPostId(), e.getMessage(), e);
                        }
                    } else {
                        log.warn("Cookie未配置，无法发送评论到豆瓣: postId={}", post.getPostId());
                    }
                    
                    // 保存回复到数据库（无论是否成功发送都保存）
                    post.setBotReplied(true);
                    post.setBotReplyContent(reply);
                    post.setBotReplyAt(LocalDateTime.now());
                    repository.updatePostBotReply(post);
                    log.info("已保存回复到数据库: postId={}, 已发送到豆瓣={}", post.getPostId(), commentSent);
                    
                    // 从数据库读取延迟配置
                    BotConfigDao.BotConfigRow delayConfig = jdbi.withExtension(BotConfigDao.class, BotConfigDao::findById);
                    int minDelay = delayConfig != null && delayConfig.minReplyDelay() != null ? delayConfig.minReplyDelay() : 30;
                    int maxDelay = delayConfig != null && delayConfig.maxReplyDelay() != null ? delayConfig.maxReplyDelay() : 300;
                    double speedMultiplier = delayConfig != null && delayConfig.replySpeedMultiplier() != null && delayConfig.replySpeedMultiplier() > 0 
                            ? delayConfig.replySpeedMultiplier() 
                            : 1.0;
                    
                    // 根据速度倍数调整延迟（倍数越大，延迟越长；倍数越小，延迟越短）
                    int adjustedMinDelay = (int) (minDelay * speedMultiplier);
                    int adjustedMaxDelay = (int) (maxDelay * speedMultiplier);
                    
                    log.debug("回复延迟: 原始={}-{}秒, 速度倍数={}, 调整后={}-{}秒", 
                            minDelay, maxDelay, speedMultiplier, adjustedMinDelay, adjustedMaxDelay);
                    
                    // 随机延迟
                    HttpUtils.randomSleep(
                            adjustedMinDelay * 1000,
                            adjustedMaxDelay * 1000
                    );
                } catch (Exception e) {
                    log.error("生成回复失败: postId={}, error={}", post.getPostId(), e.getMessage(), e);
                }
            }
            
            log.info("自动回复检查完成: groupId={}, 检查帖子数={}, 需要回复={}, 已回复={}, 实际生成回复={}", 
                    groupId, checkedCount, shouldReplyCount, alreadyRepliedCount, shouldReplyCount - alreadyRepliedCount);
        } catch (Exception e) {
            log.error("处理自动回复失败: groupId={}, error={}", groupId, e.getMessage(), e);
        }
    }

    private String buildSystemPrompt(Group group) {
        // 读取机器人配置
        BotConfigDao.BotConfigRow botConfig = jdbi.withExtension(BotConfigDao.class, BotConfigDao::findById);
        boolean enableStyleLearning = botConfig != null && botConfig.enableStyleLearning() != null ? botConfig.enableStyleLearning() : true;
        String customPrompt = botConfig != null && botConfig.customPrompt() != null ? botConfig.customPrompt() : "";
        
        // 如果配置了自定义 prompt，优先使用自定义 prompt
        if (customPrompt != null && !customPrompt.trim().isEmpty()) {
            log.debug("使用自定义 prompt 生成回复: groupId={}, promptLength={}", group.getGroupId(), customPrompt.length());
            return customPrompt.trim();
        }
        
        // 如果启用学习风格，则学习小组风格
        if (enableStyleLearning) {
            int maxComments = botConfig != null && botConfig.maxHistoryComments() != null 
                    ? botConfig.maxHistoryComments() 
                    : 200;
            List<Comment> comments = repository.getCommentsByGroupId(group.getGroupId(), maxComments);
            
            String style = comments.stream()
                    .limit(10)
                    .map(Comment::getContent)
                    .filter(c -> c != null && c.length() > 5)
                    .collect(Collectors.joining("\n"));

            log.debug("使用学习风格生成回复: groupId={}, groupName={}, commentsCount={}", 
                    group.getGroupId(), group.getName(), comments.size());
            return String.format("你是一个豆瓣小组%s的成员。请根据以下示例评论的风格，生成一个自然、友好的回复。\n示例评论风格：\n%s\n请保持相似的语气和风格。", 
                    group.getName(), style.isEmpty() ? "友好、自然" : style);
        } else {
            // 未启用学习风格，使用默认提示词
            log.debug("学习风格已禁用，使用默认提示词: groupId={}", group.getGroupId());
            return "你是一个豆瓣小组的成员，请生成一个自然、友好的回复。";
        }
    }

    private boolean shouldReply(Post post) {
        if (!post.getIsMatched()) {
            log.debug("帖子未匹配关键词，不回复: postId={}", post.getPostId());
            return false;
        }

        // 从数据库读取回复关键词
        BotConfigDao.BotConfigRow botConfig = jdbi.withExtension(BotConfigDao.class, BotConfigDao::findById);
        List<String> replyKeywords = List.of();
        if (botConfig != null && botConfig.replyKeywords() != null && !botConfig.replyKeywords().isEmpty()) {
            try {
                replyKeywords = BotConfigDao.objectMapper.readValue(
                        botConfig.replyKeywords(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}
                );
            } catch (Exception e) {
                log.warn("解析回复关键词失败: {}", e.getMessage());
            }
        }
        
        if (replyKeywords == null || replyKeywords.isEmpty()) {
            return true; // 如果没有配置回复关键词，回复所有匹配的帖子
        }

        String title = post.getTitle().toLowerCase();
        String content = post.getContent() != null ? post.getContent().toLowerCase() : "";
        for (String keyword : replyKeywords) {
            if (title.contains(keyword.toLowerCase()) || content.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 处理一个未回复的帖子（用于定时任务）
     */
    public void processOneUnrepliedPost() {
        try {
            // 从数据库读取机器人配置
            BotConfigDao.BotConfigRow botConfig = jdbi.withExtension(BotConfigDao.class, BotConfigDao::findById);
            if (botConfig == null) {
                log.debug("机器人配置不存在，跳过处理");
                return;
            }
            if (!botConfig.enabled()) {
                log.debug("机器人未启用，跳过处理");
                return;
            }
            
            // 获取一个未回复的帖子
            Post post = repository.getOneUnrepliedPost();
            if (post == null) {
                log.debug("没有需要回复的帖子");
                return;
            }
            
            log.info("找到未回复的帖子，开始处理: postId={}, title={}", post.getPostId(), post.getTitle());
            
            // 获取小组信息
            Group group = repository.getGroupById(post.getGroupId());
            if (group == null) {
                log.error("小组不存在: groupId={}", post.getGroupId());
                return;
            }
            
            // 构建系统提示词
            String systemPrompt = buildSystemPrompt(group);
            
            // 检查是否需要回复（根据回复关键词配置）
            if (!shouldReply(post, botConfig)) {
                log.debug("帖子不符合回复条件，标记为已处理: postId={}", post.getPostId());
                // 标记为已回复，避免重复检查
                post.setBotReplied(true);
                post.setBotReplyContent("不符合回复条件");
                post.setBotReplyAt(LocalDateTime.now());
                repository.updatePostBotReply(post);
                return;
            }
            
            // 生成回复
            String userPrompt = "请为以下帖子生成一个符合小组风格的回复：\n标题：" + post.getTitle() + "\n内容：" + post.getContent();
            
            String reply = llmClient.generateReply(
                    systemPrompt, 
                    userPrompt,
                    botConfig.llmApiBase(),
                    botConfig.llmApiKey(),
                    botConfig.llmModel(),
                    botConfig.llmTemperature(),
                    botConfig.llmMaxTokens()
            );
            log.info("为帖子 {} 生成回复: {}", post.getPostId(), reply);
            
            // 获取机器人配置的cookie，用于发送评论
            String botCookie = botConfig.cookie() != null && !botConfig.cookie().trim().isEmpty() 
                    ? botConfig.cookie() 
                    : appConfig.getCookie();
            
            // 实际发送回复到豆瓣
            boolean commentSent = false;
            if (botCookie != null && !botCookie.trim().isEmpty()) {
                try {
                    commentSent = doubanApiService.postComment(post.getPostId(), reply, botCookie);
                    if (commentSent) {
                        log.info("评论已成功发送到豆瓣: postId={}", post.getPostId());
                    } else {
                        log.warn("评论发送失败: postId={}", post.getPostId());
                    }
                } catch (Exception e) {
                    log.error("发送评论到豆瓣时发生异常: postId={}, error={}", 
                            post.getPostId(), e.getMessage(), e);
                }
            } else {
                log.warn("Cookie未配置，无法发送评论到豆瓣: postId={}", post.getPostId());
            }
            
            // 保存回复到数据库（无论是否成功发送都保存）
            post.setBotReplied(true);
            post.setBotReplyContent(reply);
            post.setBotReplyAt(LocalDateTime.now());
            repository.updatePostBotReply(post);
            log.info("已保存回复到数据库: postId={}, 已发送到豆瓣={}", post.getPostId(), commentSent);
            
            // 根据配置的延迟和速度倍数，添加延迟
            int minDelay = botConfig.minReplyDelay() != null ? botConfig.minReplyDelay() : 30;
            int maxDelay = botConfig.maxReplyDelay() != null ? botConfig.maxReplyDelay() : 300;
            double speedMultiplier = botConfig.replySpeedMultiplier() != null && botConfig.replySpeedMultiplier() > 0 
                    ? botConfig.replySpeedMultiplier() 
                    : 1.0;
            
            int adjustedMinDelay = (int) (minDelay * speedMultiplier);
            int adjustedMaxDelay = (int) (maxDelay * speedMultiplier);
            
            log.debug("回复延迟: 原始={}-{}秒, 速度倍数={}, 调整后={}-{}秒", 
                    minDelay, maxDelay, speedMultiplier, adjustedMinDelay, adjustedMaxDelay);
            
            // 随机延迟（在定时任务中，这个延迟会在下次任务执行前完成）
            HttpUtils.randomSleep(adjustedMinDelay * 1000, adjustedMaxDelay * 1000);
            
        } catch (Exception e) {
            log.error("处理未回复帖子失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 检查帖子是否需要回复（考虑回复关键词配置）
     */
    private boolean shouldReply(Post post, BotConfigDao.BotConfigRow botConfig) {
        if (!post.getIsMatched()) {
            return false;
        }
        
        // 如果配置了回复关键词，检查帖子是否匹配
        String replyKeywordsStr = botConfig.replyKeywords();
        if (replyKeywordsStr != null && !replyKeywordsStr.trim().isEmpty() && !replyKeywordsStr.equals("[]")) {
            try {
                List<String> replyKeywords = BotConfigDao.objectMapper.readValue(
                        replyKeywordsStr, 
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}
                );
                if (!replyKeywords.isEmpty()) {
                    String title = post.getTitle() != null ? post.getTitle() : "";
                    String content = post.getContent() != null ? post.getContent() : "";
                    String text = title + " " + content;
                    
                    for (String keyword : replyKeywords) {
                        if (keyword != null && !keyword.trim().isEmpty()) {
                            if (text.contains(keyword)) {
                                log.debug("帖子匹配回复关键词: postId={}, keyword={}", post.getPostId(), keyword);
                                return true;
                            }
                        }
                    }
                    log.debug("帖子不匹配任何回复关键词: postId={}", post.getPostId());
                    return false;
                }
            } catch (Exception e) {
                log.warn("解析回复关键词失败: {}", e.getMessage());
            }
        }
        
        // 没有配置回复关键词，或配置为空，则回复所有匹配的帖子
        return true;
    }
}
