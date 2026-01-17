package com.douban.bot.service;

import com.douban.bot.db.BotConfigDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReplyTaskScheduler {
    
    private final ReplyBotService replyBotService;
    private final Jdbi jdbi;
    
    /**
     * 定时任务：检查并回复未回复的帖子
     * 默认每5分钟执行一次，实际间隔从数据库配置中读取
     */
    @Scheduled(fixedDelayString = "${app.reply-task-interval:300000}") // 默认300秒 = 5分钟
    public void scheduledReplyCheck() {
        try {
            // 从数据库读取机器人配置
            BotConfigDao.BotConfigRow botConfig = jdbi.withExtension(BotConfigDao.class, BotConfigDao::findById);
            if (botConfig == null) {
                log.debug("机器人配置不存在，跳过定时回复检查");
                return;
            }
            
            if (!botConfig.enabled()) {
                log.debug("机器人未启用，跳过定时回复检查");
                return;
            }
            
            log.debug("开始定时回复检查任务");
            
            // 处理一个未回复的帖子
            replyBotService.processOneUnrepliedPost();
            
        } catch (Exception e) {
            log.error("定时回复检查任务执行失败: {}", e.getMessage(), e);
        }
    }
}
