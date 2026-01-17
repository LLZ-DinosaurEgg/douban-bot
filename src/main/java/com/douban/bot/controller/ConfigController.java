package com.douban.bot.controller;

import com.douban.bot.db.CrawlerConfigDao;
import com.douban.bot.model.CrawlerConfig;
import com.douban.bot.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        try {
            CrawlerConfig config = parseConfigFromRequest(request);
            String groupId = extractGroupId(config.getGroupUrl());
            if (groupId == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "无法从小组链接中提取小组ID，请确保链接格式正确");
                return ResponseEntity.status(400).body(response);
            }
            config.setGroupId(groupId);

            CrawlerConfigDao dao = jdbi.onDemand(CrawlerConfigDao.class);
            CrawlerConfig created = dao.createConfig(config);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", created);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "创建配置失败: " + e.getMessage());
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
                            config.getExcludeKeywords() != null ? config.getExcludeKeywords() : List.of()
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
        List<String> keywords = parseStringList(request.get("keywords"));
        List<String> excludeKeywords = parseStringList(request.get("excludeKeywords"));

        return CrawlerConfig.builder()
                .name((String) request.get("name"))
                .groupUrl((String) request.get("groupUrl"))
                .keywords(keywords)
                .excludeKeywords(excludeKeywords)
                .pages(parseInteger(request.get("pages"), 10))
                .sleepSeconds(parseInteger(request.get("sleepSeconds"), 900))
                .enabled(parseBoolean(request.get("enabled"), true))
                .createdAt(java.time.LocalDateTime.now())
                .build();
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
