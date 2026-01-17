package com.douban.bot.db;

import com.douban.bot.model.CrawlerConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface CrawlerConfigDao {
    
    DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    ObjectMapper objectMapper = new ObjectMapper();

    @SqlQuery("SELECT id, name, group_url as groupUrl, group_id as groupId, keywords, exclude_keywords as excludeKeywords, " +
            "pages, sleep_seconds as sleepSeconds, enabled, created_at as createdAt, updated_at as updatedAt " +
            "FROM CrawlerConfig WHERE id = :id")
    Optional<CrawlerConfigRow> findById(@Bind("id") Long id);

    @SqlQuery("SELECT id, name, group_url as groupUrl, group_id as groupId, keywords, exclude_keywords as excludeKeywords, " +
            "pages, sleep_seconds as sleepSeconds, enabled, created_at as createdAt, updated_at as updatedAt " +
            "FROM CrawlerConfig ORDER BY created_at DESC")
    @RegisterBeanMapper(CrawlerConfigRow.class)
    List<CrawlerConfigRow> findAll();

    @SqlUpdate("INSERT INTO CrawlerConfig (name, group_url, group_id, keywords, exclude_keywords, pages, sleep_seconds, enabled, created_at, updated_at) " +
            "VALUES (:name, :groupUrl, :groupId, :keywords, :excludeKeywords, :pages, :sleepSeconds, :enabled, :createdAt, :updatedAt)")
    @GetGeneratedKeys("id")
    @Transaction
    long insert(CrawlerConfigRow row);

    @SqlUpdate("UPDATE CrawlerConfig SET name = :name, group_url = :groupUrl, group_id = :groupId, keywords = :keywords, " +
            "exclude_keywords = :excludeKeywords, pages = :pages, sleep_seconds = :sleepSeconds, enabled = :enabled, " +
            "updated_at = :updatedAt WHERE id = :id")
    @Transaction
    void update(CrawlerConfigRow row);

    @SqlUpdate("DELETE FROM CrawlerConfig WHERE id = :id")
    @Transaction
    void delete(@Bind("id") Long id);

    default List<CrawlerConfig> getAllConfigs() {
        List<CrawlerConfigRow> rows = findAll();
        return rows.stream().map(this::toCrawlerConfig).toList();
    }

    default Optional<CrawlerConfig> getConfigById(Long id) {
        return findById(id).map(this::toCrawlerConfig);
    }

    default CrawlerConfig createConfig(CrawlerConfig config) {
        CrawlerConfigRow row = toCrawlerConfigRow(config);
        long id = insert(row);
        config.setId(id);
        return config;
    }

    default void updateConfig(CrawlerConfig config) {
        CrawlerConfigRow row = toCrawlerConfigRow(config);
        update(row);
    }

    default void deleteConfig(Long id) {
        delete(id);
    }

    private CrawlerConfigRow toCrawlerConfigRow(CrawlerConfig config) {
        try {
            String keywordsJson = config.getKeywords() != null && !config.getKeywords().isEmpty()
                    ? objectMapper.writeValueAsString(config.getKeywords()) : "[]";
            String excludeKeywordsJson = config.getExcludeKeywords() != null && !config.getExcludeKeywords().isEmpty()
                    ? objectMapper.writeValueAsString(config.getExcludeKeywords()) : "[]";
            String createdAt = config.getCreatedAt() != null 
                    ? config.getCreatedAt().format(DATETIME_FORMAT) 
                    : LocalDateTime.now().format(DATETIME_FORMAT);
            String updatedAt = LocalDateTime.now().format(DATETIME_FORMAT);

            return new CrawlerConfigRow(
                    config.getId(),
                    config.getName(),
                    config.getGroupUrl(),
                    config.getGroupId(),
                    keywordsJson,
                    excludeKeywordsJson,
                    config.getPages() != null ? config.getPages() : 10,
                    config.getSleepSeconds() != null ? config.getSleepSeconds() : 900,
                    config.getEnabled() != null && config.getEnabled(),
                    createdAt,
                    updatedAt
            );
        } catch (Exception e) {
            throw new RuntimeException("Error converting CrawlerConfig to CrawlerConfigRow", e);
        }
    }

    private CrawlerConfig toCrawlerConfig(CrawlerConfigRow row) {
        try {
            List<String> keywords = row.keywords() != null && !row.keywords().isEmpty() && !row.keywords().equals("[]")
                    ? objectMapper.readValue(row.keywords(), new TypeReference<List<String>>() {})
                    : new ArrayList<>();
            List<String> excludeKeywords = row.excludeKeywords() != null && !row.excludeKeywords().isEmpty() && !row.excludeKeywords().equals("[]")
                    ? objectMapper.readValue(row.excludeKeywords(), new TypeReference<List<String>>() {})
                    : new ArrayList<>();
            
            LocalDateTime createdAt = row.createdAt() != null 
                    ? LocalDateTime.parse(row.createdAt(), DATETIME_FORMAT) 
                    : LocalDateTime.now();
            LocalDateTime updatedAt = row.updatedAt() != null 
                    ? LocalDateTime.parse(row.updatedAt(), DATETIME_FORMAT) 
                    : LocalDateTime.now();

            return CrawlerConfig.builder()
                    .id(row.id())
                    .name(row.name())
                    .groupUrl(row.groupUrl())
                    .groupId(row.groupId())
                    .keywords(keywords)
                    .excludeKeywords(excludeKeywords)
                    .pages(row.pages())
                    .sleepSeconds(row.sleepSeconds())
                    .enabled(row.enabled())
                    .createdAt(createdAt)
                    .updatedAt(updatedAt)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Error converting CrawlerConfigRow to CrawlerConfig", e);
        }
    }

    record CrawlerConfigRow(
            Long id, String name, String groupUrl, String groupId, String keywords, 
            String excludeKeywords, Integer pages, Integer sleepSeconds, boolean enabled,
            String createdAt, String updatedAt
    ) {}
}
