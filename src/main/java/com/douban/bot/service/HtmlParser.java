package com.douban.bot.service;

import com.douban.bot.model.Group;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlParser {

    private static final Pattern POST_ID_PATTERN = Pattern.compile("https?://www\\.douban\\.com/group/topic/(\\d+)/");
    private static final Pattern MEMBER_COUNT_PATTERN = Pattern.compile("\\(([\\d万\\+]+)\\)");
    private static final Pattern CREATED_PATTERN = Pattern.compile("创建于(.+?)\\s");
    private static final Pattern COMMENT_ID_PATTERN = Pattern.compile("comment/(\\d+)");
    private static final Pattern REPLY_TO_ID_PATTERN = Pattern.compile("#comment-(\\d+)");

    public static Group parseGroupInfo(Document doc, String groupId, String baseUrl) {
        // 获取小组名称
        String name = doc.selectFirst("h1") != null ? doc.selectFirst("h1").text().trim() : "";

        // 获取成员数
        int memberCount = 0;
        Elements memberLinks = doc.select("a[href=\"https://www.douban.com/group/" + groupId + "/members\"]");
        for (Element link : memberLinks) {
            String text = link.text();
            Matcher matcher = MEMBER_COUNT_PATTERN.matcher(text);
            if (matcher.find()) {
                String numStr = matcher.group(1).replace("+", "");
                if (numStr.contains("万")) {
                    numStr = numStr.replace("万", "");
                    try {
                        double f = Double.parseDouble(numStr);
                        memberCount = (int) (f * 10000);
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    try {
                        memberCount = Integer.parseInt(numStr);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        // 获取创建时间
        LocalDate created = LocalDate.now();
        Elements groupLoc = doc.select(".group-loc");
        for (Element loc : groupLoc) {
            String text = loc.text();
            Matcher matcher = CREATED_PATTERN.matcher(text);
            if (matcher.find()) {
                String dateStr = matcher.group(1).trim();
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    created = LocalDate.parse(dateStr, formatter);
                } catch (Exception ignored) {
                }
            }
        }

        return Group.builder()
                .groupId(groupId)
                .name(name)
                .alt(String.format(baseUrl, groupId))
                .memberCount(memberCount)
                .created(created)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static List<Map<String, Object>> parsePosts(Document doc) {
        List<Map<String, Object>> posts = new ArrayList<>();
        Elements rows = doc.select("table.olt tr");

        if (rows != null && !rows.isEmpty()) {
            for (Element row : rows) {
                Element link = row.selectFirst("td.title a");
                if (link == null) continue;

                String href = link.attr("href");
                String title = link.text().trim();

                Matcher matcher = POST_ID_PATTERN.matcher(href);
                if (!matcher.find()) continue;

                String postId = matcher.group(1);

                // 获取作者信息
                Elements tds = row.select("td");
                String authorName = "";
                String authorHref = "";
                if (tds.size() > 1) {
                    Element authorLink = tds.get(1).selectFirst("a");
                    if (authorLink != null) {
                        authorName = authorLink.text().trim();
                        authorHref = authorLink.attr("href");
                    }
                }

                // 获取更新时间
                String updateTime = "";
                if (tds.size() > 3) {
                    updateTime = tds.get(3).text().trim();
                }
                String updated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " " + updateTime + ":00";

                Map<String, Object> post = new HashMap<>();
                post.put("id", postId);
                post.put("title", title);
                post.put("alt", href);
                Map<String, String> author = new HashMap<>();
                author.put("name", authorName);
                author.put("alt", authorHref);
                post.put("author", author);
                post.put("updated", updated);

                posts.add(post);
            }
            return posts;
        }

        // 兜底解析：页面结构变化或被反爬时尝试从链接中提取
        Elements links = doc.select("#content a[href^=\"https://www.douban.com/group/topic/\"]");
        for (Element link : links) {
            String href = link.attr("href");
            String title = link.text().trim();
            if (title.isEmpty()) {
                continue;
            }

            Matcher matcher = POST_ID_PATTERN.matcher(href);
            if (!matcher.find()) continue;

            String postId = matcher.group(1);
            Map<String, Object> post = new HashMap<>();
            post.put("id", postId);
            post.put("title", title);
            post.put("alt", href);
            Map<String, String> author = new HashMap<>();
            author.put("name", "");
            author.put("alt", "");
            post.put("author", author);
            post.put("updated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            posts.add(post);
        }

        return posts;
    }

    public static Map<String, Object> parsePostDetail(Document doc) {
        Map<String, Object> detail = new HashMap<>();

        // 获取内容
        Element contentEl = doc.selectFirst("div.topic-content");
        String content = contentEl != null ? contentEl.text().trim() : "";
        detail.put("content", content);

        // 获取图片列表
        List<String> photos = new ArrayList<>();
        Elements images = doc.select("div.topic-content img");
        for (Element img : images) {
            String src = img.attr("src");
            if (!src.isEmpty()) {
                photos.add(src);
            }
        }
        detail.put("photos", photos);

        // 获取创建时间
        Element timeEl = doc.selectFirst(".create-time");
        String created = timeEl != null ? timeEl.text().trim() : "";
        detail.put("created", created);

        return detail;
    }

    public static List<Map<String, Object>> parseComments(Document doc) {
        List<Map<String, Object>> comments = new ArrayList<>();
        Elements commentItems = doc.select(".comment-item, .reply-item");

        int index = 0;
        for (Element item : commentItems) {
            // 获取评论ID
            String commentId = "";
            if (item.hasAttr("data-id")) {
                commentId = item.attr("data-id");
            } else {
                Element link = item.selectFirst("a");
                if (link != null) {
                    String href = link.attr("href");
                    Matcher matcher = COMMENT_ID_PATTERN.matcher(href);
                    if (matcher.find()) {
                        commentId = matcher.group(1);
                    }
                }
            }
            if (commentId.isEmpty()) {
                commentId = "temp_" + System.currentTimeMillis() + "_" + index;
            }

            // 获取评论内容
            Element contentEl = item.selectFirst(".reply-content, .comment-content, p");
            String content = "";
            if (contentEl != null) {
                content = contentEl.text().trim();
            }
            if (content.isEmpty()) {
                Elements divs = item.select("div").not(".author, .time");
                content = divs.text().trim();
            }

            // 获取作者信息
            Element authorEl = item.selectFirst(".author, .comment-author, a[href*='/people/']");
            String authorName = authorEl != null ? authorEl.text().trim() : "";
            Element authorLink = item.selectFirst("a[href*='/people/']");
            String authorHref = authorLink != null ? authorLink.attr("href") : "";

            // 获取回复的评论ID
            String replyToId = "";
            Element replyLink = item.selectFirst("a[href*='#comment']");
            if (replyLink != null) {
                String href = replyLink.attr("href");
                Matcher matcher = REPLY_TO_ID_PATTERN.matcher(href);
                if (matcher.find()) {
                    replyToId = matcher.group(1);
                }
            }

            // 获取点赞数
            int likeCount = 0;
            Element likeEl = item.selectFirst(".like-count, .vote-count");
            if (likeEl != null) {
                String likeText = likeEl.text().trim();
                try {
                    likeCount = Integer.parseInt(likeText);
                } catch (NumberFormatException ignored) {
                }
            }

            // 获取创建时间
            Element timeEl = item.selectFirst(".time, .comment-time, .pubtime");
            String created = timeEl != null ? timeEl.text().trim() : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            if (!content.isEmpty()) {
                Map<String, Object> comment = new HashMap<>();
                comment.put("id", commentId);
                comment.put("content", content);
                Map<String, String> author = new HashMap<>();
                author.put("name", authorName);
                author.put("alt", authorHref);
                comment.put("author", author);
                comment.put("reply_to_id", replyToId);
                comment.put("like_count", likeCount);
                comment.put("created", created);
                comments.add(comment);
            }

            index++;
        }

        return comments;
    }
}
