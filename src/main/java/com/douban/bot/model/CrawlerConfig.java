package com.douban.bot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerConfig {
    private Long id;
    private String name;
    private String groupUrl;
    private String groupId;  // 从URL中提取
    private List<String> keywords;
    private List<String> excludeKeywords;
    private Integer pages;
    private Integer sleepSeconds;
    private Boolean enabled;
    private String cookie;  // 豆瓣Cookie，留空则使用全局配置
    private Boolean crawlComments;  // 是否爬取评论，默认为true
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
