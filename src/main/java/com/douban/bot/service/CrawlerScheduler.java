package com.douban.bot.service;

import com.douban.bot.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.crawler-bot", havingValue = "true", matchIfMissing = false)
public class CrawlerScheduler {

    private final CrawlerService crawlerService;
    private final AppConfig config;

    @Scheduled(fixedDelayString = "${app.crawler-sleep:900000}") // 默认900秒 = 15分钟
    public void scheduledCrawl() {
        if (config.getCrawlerGroups() == null || config.getCrawlerGroups().isEmpty()) {
            log.warn("未配置爬虫小组，跳过执行");
            return;
        }

        List<String> groups = config.getCrawlerGroups();
        List<String> keywords = config.getCrawlerKeywords() != null ? config.getCrawlerKeywords() : List.of();
        List<String> exclude = config.getCrawlerExclude() != null ? config.getCrawlerExclude() : List.of();
        int pages = config.getCrawlerPages() != null ? config.getCrawlerPages() : 10;

        for (String groupId : groups) {
            try {
                // 使用全局配置的cookie，默认爬取评论
                crawlerService.crawl(groupId, pages, keywords, exclude, config.getCookie(), true);
            } catch (Exception e) {
                log.error("爬取小组 {} 失败: {}", groupId, e.getMessage(), e);
            }
        }
    }
}
