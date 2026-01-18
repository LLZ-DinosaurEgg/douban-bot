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

    public void crawl(String groupId, int pages, List<String> keywords, List<String> exclude, String cookie, boolean crawlComments) {
        int effectivePages = pages > 0 ? pages : 1;
        if (pages <= 0) {
            log.warn("爬取页数配置无效，已回退为1: groupId={}, pages={}", groupId, pages);
        }
        log.info("开始爬取小组: {}, 爬取评论: {}, pages={}", groupId, crawlComments, effectivePages);
        
        // 如果配置中没有cookie，使用全局配置的cookie
        String useCookie = (cookie != null && !cookie.trim().isEmpty()) ? cookie : config.getCookie();
        if (useCookie == null || useCookie.trim().isEmpty()) {
            log.warn("当前未配置Cookie，可能会触发403反爬: groupId={}", groupId);
        }

        // 检查小组是否存在
        Group group = repository.getGroupById(groupId);
        if (group == null) {
            // 爬取小组信息
            group = crawlGroupInfo(groupId, useCookie);
            if (group != null) {
                repository.createGroup(group);
                log.info("创建小组: {} 成功", groupId);
            } else {
                log.error("爬取小组信息失败: {}", groupId);
                return;
            }
        }

        // 爬取帖子
        String groupHomeUrl = String.format(config.getGroupInfoBaseUrl(), groupId);
        warmUpSession(groupHomeUrl, useCookie);
        int successPages = 0;
        int failedPages = 0;
        for (int page = 0; page < effectivePages; page++) {
            HttpUtils.randomSleep(5000, 8000);

            String url = String.format(config.getGroupTopicsBaseUrl(), groupId) + "?start=" + (page * 25);
            try {
                String html = fetchWithRetry(url, useCookie, groupHomeUrl, "小组帖子列表");
                if (html == null) {
                    log.warn("爬取第 {} 页失败（返回空内容）", page + 1);
                    failedPages++;
                    continue;
                }
                Document doc = Jsoup.parse(html);
                List<Map<String, Object>> posts = HtmlParser.parsePosts(doc);
                if (posts == null || posts.isEmpty()) {
                    String title = doc.title();
                    String bodyText = doc.body() != null ? doc.body().text() : "";
                    String snippet = bodyText.length() > 120 ? bodyText.substring(0, 120) + "..." : bodyText;
                    log.warn("小组帖子列表为空，可能被反爬或页面结构变化: groupId={}, page={}, title={}, snippet={}",
                            groupId, page + 1, title, snippet);
                }
                successPages++;

                for (Map<String, Object> postMap : posts) {
                    processPost(postMap, group, keywords, exclude, useCookie, crawlComments);
                }
            } catch (IOException e) {
                log.error("爬取第 {} 页失败: {}", page + 1, e.getMessage());
                failedPages++;
            }
        }
        log.info("小组爬取完成: groupId={}, successPages={}, failedPages={}", groupId, successPages, failedPages);
    }

    private Group crawlGroupInfo(String groupId, String cookie) {
        try {
            HttpUtils.randomSleep(2000, 5000);
            String url = String.format(config.getGroupInfoBaseUrl(), groupId);
            String useCookie = (cookie != null && !cookie.trim().isEmpty()) ? cookie : config.getCookie();
            String html = fetchWithRetry(url, useCookie, url, "小组信息");
            if (html == null) {
                return null;
            }
            Document doc = Jsoup.parse(html);
            return HtmlParser.parseGroupInfo(doc, groupId, config.getGroupInfoBaseUrl());
        } catch (IOException e) {
            log.error("爬取小组信息失败: {}", e.getMessage());
            return null;
        }
    }

    private void processPost(Map<String, Object> postMap, Group group, List<String> keywords, List<String> exclude, String cookie, boolean crawlComments) {
        String title = (String) postMap.get("title");
        String postUrl = (String) postMap.get("alt");
        String postId = (String) postMap.get("id");

        // 如果帖子已存在且已自动回复，则不再爬取和更新
        Post existing = repository.getPostByPostID(postId);
        if (existing != null && existing.getBotReplied() != null && existing.getBotReplied()) {
            log.debug("帖子已自动回复，跳过爬取和更新: postId={}", postId);
            return;
        }

        // 爬取帖子详情
        Map<String, Object> detail;
        try {
            HttpUtils.randomSleep(2500, 7500);
            String useCookie = (cookie != null && !cookie.trim().isEmpty()) ? cookie : config.getCookie();
            String html = fetchWithRetry(postUrl, useCookie, postUrl, "帖子详情");
            if (html == null) {
                return;
            }
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
        existing = repository.getPostByPostID(postId);
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
        
        // 过滤空关键词
        List<String> validKeywords = keywords.stream()
                .filter(k -> k != null && !k.trim().isEmpty())
                .toList();
        
        // 如果没有配置关键词，默认所有帖子都匹配
        if (validKeywords.isEmpty()) {
            isMatched = true;
            log.debug("未配置关键词，默认匹配所有帖子: 帖子={}", postId);
        } else {
            // 有关键词时，进行匹配
            for (String keyword : validKeywords) {
            String pattern = keyword.replaceAll("(.)", "$1.?");
            Pattern p = Pattern.compile(pattern);
            if (p.matcher(title).find() || p.matcher(content).find()) {
                matchedKeywords.add(keyword);
                isMatched = true;
                }
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

        // 根据配置决定是否爬取并保存评论
        if (crawlComments) {
            String useCookie = (cookie != null && !cookie.trim().isEmpty()) ? cookie : config.getCookie();
            crawlAndSaveComments(postId, group.getGroupId(), postUrl, useCookie);
        } else {
            log.debug("跳过爬取评论: 帖子={}, 配置中已禁用", postId);
        }
        
        // 不再在爬取时触发自动回复，改为由定时任务统一处理
        if (isMatched) {
            log.debug("帖子已保存，等待定时任务处理回复: 小组={}, 帖子={}, 匹配关键词={}", 
                    group.getGroupId(), postId, matchedKeywords);
        } else {
            log.debug("帖子未匹配关键词: 小组={}, 帖子={}, 关键词={}", 
                    group.getGroupId(), postId, validKeywords);
        }
    }

    private void crawlAndSaveComments(String postId, String groupId, String postUrl, String cookie) {
        try {
            HttpUtils.randomSleep(2000, 5000);
            String useCookie = (cookie != null && !cookie.trim().isEmpty()) ? cookie : config.getCookie();
            String html = fetchWithRetry(postUrl, useCookie, postUrl, "帖子评论");
            if (html == null) {
                return;
            }
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

    private String fetchWithRetry(String url, String cookie, String referer, String context) throws IOException {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpUtils.GetResponse response = HttpUtils.fetchContentWithStatus(url, cookie, referer);
            int status = response.statusCode;
            String body = response.body;

            if (status == 200 && isBlockedResponse(body)) {
                warmUpSession(referer, cookie);
                int delayMs = 12000 + (int) (Math.random() * 8000);
                log.warn("{} 命中反爬页面: url={}, status={}, attempt={}/{}，等待 {}ms 后重试",
                        context, url, status, attempt, maxAttempts, delayMs);
                HttpUtils.randomSleep(delayMs, delayMs + 500);
                continue;
            }

            if (status == 200 || status == 302) {
                return body;
            }

            if (status == 403 || status == 429) {
                warmUpSession(referer, cookie);
                int delayMs = 12000 + (int) (Math.random() * 8000);
                log.warn("{} 请求被限制: url={}, status={}, attempt={}/{}，等待 {}ms 后重试",
                        context, url, status, attempt, maxAttempts, delayMs);
                HttpUtils.randomSleep(delayMs, delayMs + 500);
                continue;
            }

            if (status >= 500 && status < 600) {
                int delayMs = 3000 + (int) (Math.random() * 3000);
                log.warn("{} 服务端错误: url={}, status={}, attempt={}/{}，等待 {}ms 后重试",
                        context, url, status, attempt, maxAttempts, delayMs);
                HttpUtils.randomSleep(delayMs, delayMs + 500);
                continue;
            }

            String preview = body != null && body.length() > 200 ? body.substring(0, 200) + "..." : body;
            throw new IOException("HTTP request failed with status: " + status + ", response: " + preview);
        }
        return null;
    }

    private void warmUpSession(String referer, String cookie) {
        if (referer == null || referer.isBlank()) {
            return;
        }
        try {
            HttpUtils.randomSleep(1000, 2000);
            HttpUtils.fetchContentWithStatus(referer, cookie, referer);
            log.debug("已预热会话: referer={}", referer);
        } catch (Exception e) {
            log.debug("预热会话失败: referer={}, error={}", referer, e.getMessage());
        }
    }

    private boolean isBlockedResponse(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        String lower = html.toLowerCase();
        return lower.contains("captcha")
                || lower.contains("forbidden")
                || html.contains("验证码")
                || html.contains("访问过于频繁")
                || html.contains("检测到有异常请求")
                || html.contains("异常访问")
                || html.contains("请先登录")
                || html.contains("登录")
                || html.contains("权限不足");
    }
}
