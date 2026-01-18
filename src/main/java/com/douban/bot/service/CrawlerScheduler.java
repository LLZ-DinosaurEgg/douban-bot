package com.douban.bot.service;

import com.douban.bot.config.AppConfig;
import com.douban.bot.db.RepositoryService;
import com.douban.bot.model.CrawlerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final CrawlerService crawlerService;
    private final RepositoryService repository;
    private final AppConfig appConfig;
    
    // 记录每个爬虫配置的上次执行时间
    private final ConcurrentHashMap<Long, Long> lastExecutionTime = new ConcurrentHashMap<>();

    /**
     * 定时检查并执行已启用的爬虫任务
     * 每30秒检查一次，根据每个爬虫配置的 sleepSeconds 决定是否执行
     */
    @Scheduled(fixedDelay = 30000) // 每30秒检查一次
    public void scheduledCrawl() {
        List<CrawlerConfig> configs = repository.getAllCrawlerConfigs();
        
        if (configs == null || configs.isEmpty()) {
            log.debug("没有配置爬虫任务");
            return;
        }
        
        // 筛选已启用的配置
        List<CrawlerConfig> enabledConfigs = configs.stream()
                .filter(c -> c.getEnabled() != null && c.getEnabled())
                .toList();
        
        if (enabledConfigs.isEmpty()) {
            log.debug("没有已启用的爬虫任务");
            return;
        }
        
        long now = System.currentTimeMillis();
        
        for (CrawlerConfig config : enabledConfigs) {
            try {
                // 获取配置的间隔时间（秒），默认900秒（15分钟）
                int sleepSeconds = config.getSleepSeconds() != null ? config.getSleepSeconds() : 900;
                long intervalMs = sleepSeconds * 1000L;
                
                // 检查是否到了执行时间
                Long lastExec = lastExecutionTime.get(config.getId());
                if (lastExec != null && (now - lastExec) < intervalMs) {
                    // 还没到执行时间，跳过
                    continue;
                }
                
                // 更新执行时间
                lastExecutionTime.put(config.getId(), now);
                
                log.info("开始执行定时爬虫任务: id={}, name={}, groupId={}, interval={}秒", 
                        config.getId(), config.getName(), config.getGroupId(), sleepSeconds);
                
                // 获取cookie，优先使用爬虫配置的cookie，否则使用全局配置
                String cookie = config.getCookie() != null && !config.getCookie().isEmpty() 
                        ? config.getCookie() 
                        : appConfig.getCookie();
                
                // 执行爬虫
                crawlerService.crawl(
                        config.getGroupId(),
                        config.getPages() != null ? config.getPages() : 10,
                        config.getKeywords() != null ? config.getKeywords() : List.of(),
                        config.getExcludeKeywords() != null ? config.getExcludeKeywords() : List.of(),
                        cookie,
                        config.getCrawlComments() != null ? config.getCrawlComments() : true
                );
                
                log.info("定时爬虫任务执行完成: id={}, name={}", config.getId(), config.getName());
                
            } catch (Exception e) {
                log.error("定时爬虫任务执行失败: id={}, name={}, error={}", 
                        config.getId(), config.getName(), e.getMessage(), e);
            }
        }
    }
}
