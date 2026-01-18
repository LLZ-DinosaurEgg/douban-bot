package com.douban.bot.controller;

import com.douban.bot.db.CrawlerConfigDao;
import com.douban.bot.model.CrawlerConfig;
import com.douban.bot.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final Jdbi jdbi;
    private final CrawlerService crawlerService;
    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("/group/([^/]+)/");

    @GetMapping("/crawler")
    public ResponseEntity<Map<String, Object>> getAllConfigs() {
        try {
            CrawlerConfigDao dao = jdbi.onDemand(CrawlerConfigDao.class);
            List<CrawlerConfig> configs = dao.getAllConfigs();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", configs);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取配置失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/crawler/{id}")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable Long id) {
        try {
            CrawlerConfigDao dao = jdbi.onDemand(CrawlerConfigDao.class);
            CrawlerConfig config = dao.getConfigById(id).orElse(null);
            if (config == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "配置不存在");
                return ResponseEntity.status(404).body(response);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", config);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取配置失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/crawler")
    public ResponseEntity<Map<String, Object>> createConfig(@RequestBody Map<String, Object> request) {
        log.info("收到创建配置请求: {}", request);
        try {
            CrawlerConfig config = parseConfigFromRequest(request);
            log.debug("解析后的配置: name={}, groupUrl={}, pages={}", 
                config.getName(), config.getGroupUrl(), config.getPages());
            
            String groupId = extractGroupId(config.getGroupUrl());
            if (groupId == null) {
                log.warn("无法从小组链接中提取小组ID: {}", config.getGroupUrl());
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "无法从小组链接中提取小组ID，请确保链接格式正确");
                return ResponseEntity.status(400).body(response);
            }
            config.setGroupId(groupId);
            log.debug("提取的小组ID: {}", groupId);

            // 使用 handle 在同一连接中执行插入和获取 ID
            CrawlerConfig created = jdbi.inTransaction(handle -> {
                CrawlerConfigDao dao = handle.attach(CrawlerConfigDao.class);
                
                // 准备数据
                com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String keywordsJson = config.getKeywords() != null && !config.getKeywords().isEmpty()
                        ? objectMapper.writeValueAsString(config.getKeywords()) : "[]";
                String excludeKeywordsJson = config.getExcludeKeywords() != null && !config.getExcludeKeywords().isEmpty()
                        ? objectMapper.writeValueAsString(config.getExcludeKeywords()) : "[]";
                String createdAt = config.getCreatedAt() != null 
                        ? config.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        : java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String updatedAt = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                
                // 执行插入
                dao.insert(config.getName(), config.getGroupUrl(), config.getGroupId(), keywordsJson, 
                          excludeKeywordsJson, config.getPages() != null ? config.getPages() : 10, 
                          config.getSleepSeconds() != null ? config.getSleepSeconds() : 900, 
                          config.getEnabled() != null && config.getEnabled(), 
                          config.getCookie() != null ? config.getCookie() : "", 
                          config.getCrawlComments() != null ? config.getCrawlComments() : true,
                          createdAt, updatedAt);
                
                // 在同一连接中获取生成的 ID
                long id = handle.createQuery("SELECT last_insert_rowid()")
                        .mapTo(long.class)
                        .one();
                
                config.setId(id);
                return config;
            });
            
            log.info("配置创建成功: id={}, name={}", created.getId(), created.getName());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", created);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建配置失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "创建配置失败: " + e.getMessage());
            log.error("错误详情: 请求数据={}, 异常类型={}, 异常消息={}", 
                request, e.getClass().getName(), e.getMessage(), e);
            return ResponseEntity.status(500).body(response);
        }
    }

    @PutMapping("/crawler/{id}")
    public ResponseEntity<Map<String, Object>> updateConfig(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            CrawlerConfigDao dao = jdbi.onDemand(CrawlerConfigDao.class);
            CrawlerConfig existing = dao.getConfigById(id).orElse(null);
            if (existing == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "配置不存在");
                return ResponseEntity.status(404).body(response);
            }

            CrawlerConfig config = parseConfigFromRequest(request);
            config.setId(id);
            
            String groupId = extractGroupId(config.getGroupUrl());
            if (groupId == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "无法从小组链接中提取小组ID，请确保链接格式正确");
                return ResponseEntity.status(400).body(response);
            }
            config.setGroupId(groupId);

            dao.updateConfig(config);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", config);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "更新配置失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/crawler/{id}")
    public ResponseEntity<Map<String, Object>> deleteConfig(@PathVariable Long id) {
        try {
            CrawlerConfigDao dao = jdbi.onDemand(CrawlerConfigDao.class);
            CrawlerConfig existing = dao.getConfigById(id).orElse(null);
            if (existing == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "配置不存在");
                return ResponseEntity.status(404).body(response);
            }

            dao.deleteConfig(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "删除配置失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/crawler/{id}/run")
    public ResponseEntity<Map<String, Object>> runCrawler(@PathVariable Long id) {
        try {
            CrawlerConfigDao dao = jdbi.onDemand(CrawlerConfigDao.class);
            CrawlerConfig config = dao.getConfigById(id).orElse(null);
            if (config == null || !config.getEnabled()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "配置不存在或未启用");
                return ResponseEntity.status(400).body(response);
            }

            // 在新线程中运行爬虫，避免阻塞请求
            new Thread(() -> {
                try {
                    crawlerService.crawl(
                            config.getGroupId(),
                            config.getPages(),
                            config.getKeywords() != null ? config.getKeywords() : List.of(),
                            config.getExcludeKeywords() != null ? config.getExcludeKeywords() : List.of(),
                            config.getCookie() != null ? config.getCookie() : "",
                            config.getCrawlComments() != null ? config.getCrawlComments() : true
                    );
                } catch (Exception e) {
                    System.err.println("爬虫执行失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "爬虫任务已启动");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "启动爬虫失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    private CrawlerConfig parseConfigFromRequest(Map<String, Object> request) {
        try {
            String name = (String) request.get("name");
            String groupUrl = (String) request.get("groupUrl");
            
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("配置名称不能为空");
            }
            if (groupUrl == null || groupUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("小组链接不能为空");
            }
            
            List<String> keywords = parseStringList(request.get("keywords"));
            List<String> excludeKeywords = parseStringList(request.get("excludeKeywords"));
            Integer pages = parseInteger(request.get("pages"), 10);
            Integer sleepSeconds = parseInteger(request.get("sleepSeconds"), 900);
            Boolean enabled = parseBoolean(request.get("enabled"), true);
            String cookie = request.get("cookie") != null ? request.get("cookie").toString().trim() : "";
            Boolean crawlComments = parseBoolean(request.get("crawlComments"), true);

            log.debug("解析配置参数: name={}, groupUrl={}, keywords={}, excludeKeywords={}, pages={}, sleepSeconds={}, enabled={}, hasCookie={}, crawlComments={}",
                name, groupUrl, keywords, excludeKeywords, pages, sleepSeconds, enabled, !cookie.isEmpty(), crawlComments);

            return CrawlerConfig.builder()
                    .name(name.trim())
                    .groupUrl(groupUrl.trim())
                    .keywords(keywords)
                    .excludeKeywords(excludeKeywords)
                    .pages(pages)
                    .sleepSeconds(sleepSeconds)
                    .enabled(enabled)
                    .cookie(cookie)
                    .crawlComments(crawlComments)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("解析配置请求失败", e);
            throw new IllegalArgumentException("解析配置参数失败: " + e.getMessage(), e);
        }
    }

    private List<String> parseStringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List) {
            return ((List<?>) value).stream()
                    .map(Object::toString)
                    .filter(s -> !s.trim().isEmpty())
                    .toList();
        }
        String str = value.toString().trim();
        if (str.isEmpty()) return List.of();
        return List.of(str.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private Integer parseInteger(Object value, Integer defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Boolean parseBoolean(Object value, Boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    private String extractGroupId(String groupUrl) {
        if (groupUrl == null || groupUrl.isEmpty()) return null;
        Matcher matcher = GROUP_ID_PATTERN.matcher(groupUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
