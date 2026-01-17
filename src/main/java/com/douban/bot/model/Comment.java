package com.douban.bot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {
    private Long id;
    private String commentId;
    private String postId;
    private String groupId;
    private Map<String, Object> authorInfo;
    private String content;
    private String replyToId;
    private Integer likeCount;
    private LocalDateTime created;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
