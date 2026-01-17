package com.douban.bot.service;

import com.douban.bot.config.AppConfig;
import com.douban.bot.db.RepositoryService;
import com.douban.bot.model.Comment;
import com.douban.bot.model.Group;
import com.douban.bot.model.Post;
import com.douban.bot.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.crawler-bot", havingValue = "true", matchIfMissing = false)
public class ReplyBotService {

    private final RepositoryService repository;
    private final LlmClient llmClient;
    private final AppConfig config;

    @Async
    public void processNewPosts(String groupId) {
        if (!config.getCrawlerBot()) {
            return;
        }

        try {
            Group group = repository.getGroupById(groupId);
            if (group == null) {
                log.error("小组不存在: {}", groupId);
                return;
            }

            // 学习小组风格（简化版）
            String systemPrompt = buildSystemPrompt(group);

            // 获取最近匹配的帖子
            List<Post> posts = repository.getPostsByGroupId(groupId, 50);
            for (Post post : posts) {
                if (!shouldReply(post)) {
                    continue;
                }

                // 检查是否已经回复过
                List<Comment> comments = repository.getCommentsByPostID(post.getPostId());
                if (hasReplied(comments)) {
                    continue;
                }

                // 生成回复
                try {
                    String userPrompt = "请为以下帖子生成一个符合小组风格的回复：\n标题：" + post.getTitle() + "\n内容：" + post.getContent();
                    String reply = llmClient.generateReply(systemPrompt, userPrompt);
                    log.info("为帖子 {} 生成回复: {}", post.getPostId(), reply);

                    // TODO: 实际发送回复到豆瓣（需要实现豆瓣API调用）
                    // 这里只是记录生成的回复
                    
                    // 随机延迟
                    HttpUtils.randomSleep(
                            config.getCrawlerMinReplyDelay() * 1000,
                            config.getCrawlerMaxReplyDelay() * 1000
                    );
                } catch (Exception e) {
                    log.error("生成回复失败: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("处理自动回复失败: {}", e.getMessage(), e);
        }
    }

    private String buildSystemPrompt(Group group) {
        // 获取历史评论学习风格（简化版）
        List<Comment> comments = repository.getCommentsByGroupId(group.getGroupId(), 
                config.getCrawlerMaxHistoryComments() != null ? config.getCrawlerMaxHistoryComments() : 200);
        
        String style = comments.stream()
                .limit(10)
                .map(Comment::getContent)
                .filter(c -> c != null && c.length() > 5)
                .collect(Collectors.joining("\n"));

        return String.format("你是一个豆瓣小组%s的成员。请根据以下示例评论的风格，生成一个自然、友好的回复。\n示例评论风格：\n%s\n请保持相似的语气和风格。", 
                group.getName(), style.isEmpty() ? "友好、自然" : style);
    }

    private boolean shouldReply(Post post) {
        if (!post.getIsMatched()) {
            return false;
        }

        List<String> replyKeywords = config.getCrawlerReplyKeywords();
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

    private boolean hasReplied(List<Comment> comments) {
        // TODO: 检查评论列表中是否包含我们生成的回复
        // 这里简化处理，可以根据实际需求实现
        return false;
    }
}
