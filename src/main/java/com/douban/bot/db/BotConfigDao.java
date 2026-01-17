package com.douban.bot.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public interface BotConfigDao {
    
    DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    ObjectMapper objectMapper = new ObjectMapper();

    @SqlQuery("SELECT id, enabled, llm_api_type as llmApiType, llm_api_base as llmApiBase, llm_api_key as llmApiKey, " +
            "llm_model as llmModel, llm_temperature as llmTemperature, llm_max_tokens as llmMaxTokens, " +
            "reply_keywords as replyKeywords, min_reply_delay as minReplyDelay, max_reply_delay as maxReplyDelay, " +
            "max_history_posts as maxHistoryPosts, max_history_comments as maxHistoryComments, " +
            "enable_style_learning as enableStyleLearning, custom_prompt as customPrompt, cookie, " +
            "reply_speed_multiplier as replySpeedMultiplier, reply_task_interval as replyTaskInterval, " +
            "updated_at as updatedAt FROM BotConfig WHERE id = 1 LIMIT 1")
    @RegisterConstructorMapper(BotConfigRow.class)
    BotConfigRow findById();

    @SqlUpdate("UPDATE BotConfig SET enabled = :enabled, llm_api_type = :llmApiType, llm_api_base = :llmApiBase, " +
            "llm_api_key = :llmApiKey, llm_model = :llmModel, llm_temperature = :llmTemperature, " +
            "llm_max_tokens = :llmMaxTokens, reply_keywords = :replyKeywords, min_reply_delay = :minReplyDelay, " +
            "max_reply_delay = :maxReplyDelay, max_history_posts = :maxHistoryPosts, " +
            "max_history_comments = :maxHistoryComments, enable_style_learning = :enableStyleLearning, " +
            "custom_prompt = :customPrompt, cookie = :cookie, reply_speed_multiplier = :replySpeedMultiplier, " +
            "reply_task_interval = :replyTaskInterval, updated_at = :updatedAt WHERE id = 1")
    @Transaction
    void update(@Bind("enabled") boolean enabled,
                @Bind("llmApiType") String llmApiType,
                @Bind("llmApiBase") String llmApiBase,
                @Bind("llmApiKey") String llmApiKey,
                @Bind("llmModel") String llmModel,
                @Bind("llmTemperature") Double llmTemperature,
                @Bind("llmMaxTokens") Integer llmMaxTokens,
                @Bind("replyKeywords") String replyKeywords,
                @Bind("minReplyDelay") Integer minReplyDelay,
                @Bind("maxReplyDelay") Integer maxReplyDelay,
                @Bind("maxHistoryPosts") Integer maxHistoryPosts,
                @Bind("maxHistoryComments") Integer maxHistoryComments,
                @Bind("enableStyleLearning") boolean enableStyleLearning,
                @Bind("customPrompt") String customPrompt,
                @Bind("cookie") String cookie,
                @Bind("replySpeedMultiplier") Double replySpeedMultiplier,
                @Bind("replyTaskInterval") Integer replyTaskInterval,
                @Bind("updatedAt") String updatedAt);

    record BotConfigRow(
            Long id,
            boolean enabled,
            String llmApiType,
            String llmApiBase,
            String llmApiKey,
            String llmModel,
            Double llmTemperature,
            Integer llmMaxTokens,
            String replyKeywords,
            Integer minReplyDelay,
            Integer maxReplyDelay,
            Integer maxHistoryPosts,
            Integer maxHistoryComments,
            Boolean enableStyleLearning,
            String customPrompt,
            String cookie,
            Double replySpeedMultiplier,
            Integer replyTaskInterval,
            String updatedAt
    ) {}
}
