package com.douban.bot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    private Long id;
    private String postId;
    private String groupId;
    private Map<String, Object> authorInfo;
    private String alt;
    private String title;
    private String content;
    private List<String> photoList;
    private Double rent;
    private String subway;
    private String contact;
    private Boolean isMatched;
    private List<String> keywordList;
    private String comment;
    private Boolean isCollected;
    private Boolean botReplied;  // 是否已自动回复
    private String botReplyContent;  // 自动回复内容
    private LocalDateTime botReplyAt;  // 自动回复时间
    private LocalDateTime created;
    private LocalDateTime updated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
