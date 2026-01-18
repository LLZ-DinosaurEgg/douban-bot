package com.douban.bot.service;

import com.douban.bot.db.BotConfigDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReplyScheduler {
    
    private final Jdbi jdbi;
    private final ReplyBotService replyBotService;
    
    /**
     * 定时检查并回复未回复的帖子
     * 默认每5分钟执行一次，实际间隔从数据库配置中读取
     */
    @Scheduled(fixedDelayString = "60000") // 每分钟检查一次配置，但实际执行间隔由配置决定
    public void scheduledReplyCheck() {
        try {
            // 从数据库读取机器人配置
            BotConfigDao.BotConfigRow botConfig = jdbi.withExtension(BotConfigDao.class, BotConfigDao::findById);
            if (botConfig == null || !botConfig.enabled()) {
                // 机器人未启用，不执行
                log.info("定时回复检查跳过：机器人未启用或配置不存在");
                return;
            }
            
            // 获取回复检查间隔（秒），默认300秒（5分钟）
            int checkInterval = botConfig.replyTaskInterval() != null && botConfig.replyTaskInterval() > 0 
                    ? botConfig.replyTaskInterval() 
                    : 300;
            
            // 检查是否到了执行时间（使用简单的静态变量记录上次执行时间）
            long currentTime = System.currentTimeMillis();
            long lastExecuteTime = lastExecuteTimeMs.get();
            if (lastExecuteTime > 0 && (currentTime - lastExecuteTime) < (checkInterval * 1000L)) {
                // 还没到执行时间，跳过
                log.info("定时回复检查未到间隔，跳过: interval={}秒", checkInterval);
                return;
            }
            
            log.info("开始定时检查未回复的帖子，检查间隔={}秒", checkInterval);
            
            // 调用ReplyBotService处理一个未回复的帖子
            // 该方法内部会处理所有逻辑：查找帖子、检查条件、生成回复、发送评论等
            int cooldownSeconds = Math.max(checkInterval, 60);
            replyBotService.processOneUnrepliedPost(cooldownSeconds);
            
            // 更新执行时间
            lastExecuteTimeMs.set(currentTime);
            
        } catch (Exception e) {
            log.error("定时检查未回复帖子时发生错误: {}", e.getMessage(), e);
        }
    }

    private static final AtomicLong lastExecuteTimeMs = new AtomicLong(0);
}
