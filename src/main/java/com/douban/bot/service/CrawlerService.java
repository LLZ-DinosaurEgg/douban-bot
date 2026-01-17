package com.douban.bot.service;

import com.douban.bot.config.AppConfig;
import com.douban.bot.db.RepositoryService;
import com.douban.bot.model.Comment;
import com.douban.bot.model.Group;
import com.douban.bot.model.Post;
import com.douban.bot.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final RepositoryService repository;
    private final AppConfig config;
    
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern KEYWORD_PATTERN_BASE = Pattern.compile("(.?)");

    public void crawl(String groupId, int pages, List<String> keywords, List<String> exclude) {
        log.info("开始爬取小组: {}", groupId);

        // 检查小组是否存在
        Group group = repository.getGroupById(groupId);
        if (group == null) {
            // 爬取小组信息
            group = crawlGroupInfo(groupId);
            if (group != null) {
                repository.createGroup(group);
                log.info("创建小组: {} 成功", groupId);
            } else {
                log.error("爬取小组信息失败: {}", groupId);
                return;
            }
        }

        // 爬取帖子
        for (int page = 0; page < pages; page++) {
            HttpUtils.randomSleep(5000, 8000);
            
            String url = String.format(config.getGroupTopicsBaseUrl(), groupId) + "?start=" + (page * 25);
            try {
                String html = HttpUtils.fetchContent(url, config.getCookie());
                Document doc = Jsoup.parse(html);
                List<Map<String, Object>> posts = HtmlParser.parsePosts(doc);
                
                for (Map<String, Object> postMap : posts) {
                    processPost(postMap, group, keywords, exclude);
                }
            } catch (IOException e) {
                log.error("爬取第 {} 页失败: {}", page + 1, e.getMessage());
            }
        }
    }

    private Group crawlGroupInfo(String groupId) {
        try {
            HttpUtils.randomSleep(2000, 5000);
            String url = String.format(config.getGroupInfoBaseUrl(), groupId);
            String html = HttpUtils.fetchContent(url, config.getCookie());
            Document doc = Jsoup.parse(html);
            return HtmlParser.parseGroupInfo(doc, groupId, config.getGroupInfoBaseUrl());
        } catch (IOException e) {
            log.error("爬取小组信息失败: {}", e.getMessage());
            return null;
        }
    }

    private void processPost(Map<String, Object> postMap, Group group, List<String> keywords, List<String> exclude) {
        String title = (String) postMap.get("title");
        String postUrl = (String) postMap.get("alt");
        String postId = (String) postMap.get("id");

        // 爬取帖子详情
        Map<String, Object> detail;
        try {
            HttpUtils.randomSleep(2500, 7500);
            String html = HttpUtils.fetchContent(postUrl, config.getCookie());
            Document doc = Jsoup.parse(html);
            detail = HtmlParser.parsePostDetail(doc);
        } catch (IOException e) {
            log.error("爬取帖子详情失败: {}", e.getMessage());
            return;
        }

        String content = (String) detail.getOrDefault("content", "");

        // 检查排除关键词
        for (String e : exclude) {
            if (!e.isEmpty() && (title.contains(e) || content.contains(e))) {
                return;
            }
        }

        // 检查帖子是否已存在
        Post existing = repository.getPostByPostID(postId);
        if (existing != null) {
            // 更新帖子
            existing.setTitle(title);
            String updatedStr = (String) postMap.get("updated");
            if (updatedStr != null) {
                try {
                    existing.setUpdated(LocalDateTime.parse(updatedStr, DATETIME_FORMAT));
                } catch (Exception ignored) {
                    existing.setUpdated(LocalDateTime.now());
                }
            }
            repository.updatePost(existing);
            log.info("更新帖子: {}", postId);
            return;
        }

        // 检查标题是否重复
        if (repository.checkPostTitleExists(title)) {
            log.info("标题重复，忽略: {}", title);
            return;
        }

        // 匹配关键词
        List<String> matchedKeywords = new java.util.ArrayList<>();
        boolean isMatched = false;
        for (String keyword : keywords) {
            if (keyword.isEmpty()) continue;
            String pattern = keyword.replaceAll("(.)", "$1.?");
            Pattern p = Pattern.compile(pattern);
            if (p.matcher(title).find() || p.matcher(content).find()) {
                matchedKeywords.add(keyword);
                isMatched = true;
            }
        }

        // 解析时间
        String createdStr = (String) detail.getOrDefault("created", "");
        LocalDateTime created = LocalDateTime.now();
        if (!createdStr.isEmpty()) {
            try {
                created = LocalDateTime.parse(createdStr, DATETIME_FORMAT);
            } catch (Exception ignored) {
            }
        }
        String updatedStr = (String) postMap.getOrDefault("updated", "");
        LocalDateTime updated = LocalDateTime.now();
        if (!updatedStr.isEmpty()) {
            try {
                updated = LocalDateTime.parse(updatedStr, DATETIME_FORMAT);
            } catch (Exception ignored) {
            }
        }

        // 构建帖子对象
        @SuppressWarnings("unchecked")
        Map<String, String> authorInfoMap = (Map<String, String>) postMap.getOrDefault("author", Map.of());
        @SuppressWarnings("unchecked")
        List<String> photos = (List<String>) detail.getOrDefault("photos", List.of());

        Post post = Post.builder()
                .postId(postId)
                .groupId(group.getGroupId())
                .authorInfo(Map.copyOf(authorInfoMap))
                .alt(postUrl)
                .title(title)
                .content(content)
                .photoList(photos)
                .isMatched(isMatched)
                .keywordList(matchedKeywords)
                .created(created)
                .updated(updated)
                .build();

        // 保存帖子
        repository.createPost(post);
        log.info("保存帖子: {}", postId);

        // 爬取并保存评论
        crawlAndSaveComments(postId, group.getGroupId(), postUrl);
    }

    private void crawlAndSaveComments(String postId, String groupId, String postUrl) {
        try {
            HttpUtils.randomSleep(2000, 5000);
            String html = HttpUtils.fetchContent(postUrl, config.getCookie());
            Document doc = Jsoup.parse(html);
            List<Map<String, Object>> comments = HtmlParser.parseComments(doc);

            for (Map<String, Object> commentMap : comments) {
                String commentId = (String) commentMap.get("id");

                // 检查评论是否已存在
                Comment existing = repository.getCommentByCommentID(commentId);
                if (existing != null) {
                    continue;
                }

                // 解析时间
                String createdStr = (String) commentMap.getOrDefault("created", "");
                LocalDateTime created = LocalDateTime.now();
                if (!createdStr.isEmpty()) {
                    try {
                        created = LocalDateTime.parse(createdStr, DATETIME_FORMAT);
                    } catch (Exception ignored) {
                    }
                }

                @SuppressWarnings("unchecked")
                Map<String, String> authorInfoMap = (Map<String, String>) commentMap.getOrDefault("author", Map.of());

                Comment comment = Comment.builder()
                        .commentId(commentId)
                        .postId(postId)
                        .groupId(groupId)
                        .authorInfo(Map.copyOf(authorInfoMap))
                        .content((String) commentMap.getOrDefault("content", ""))
                        .replyToId((String) commentMap.getOrDefault("reply_to_id", null))
                        .likeCount((Integer) commentMap.getOrDefault("like_count", 0))
                        .created(created)
                        .build();

                repository.createComment(comment);
                log.info("保存评论: {} (帖子: {})", commentId, postId);
            }
        } catch (IOException e) {
            log.error("爬取评论失败: {}", e.getMessage());
        }
    }
}
