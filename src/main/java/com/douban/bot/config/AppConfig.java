package com.douban.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    
    // 豆瓣配置
    private String doubanBaseHost = "https://www.douban.com";
    private String cookie = "";
    private String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.71 Safari/537.36";
    private String datetimeFormat = "yyyy-MM-dd HH:mm:ss";
    private String dateFormat = "yyyy-MM-dd";
    
    // LLM配置
    private String llmApiType = "openai";
    private String llmApiBase = "https://api.openai.com/v1";
    private String llmApiKey = "";
    private String llmModel = "gpt-3.5-turbo";
    private Double llmTemperature = 0.7;
    private Integer llmMaxTokens = 500;
    
    // 爬虫配置
    private List<String> crawlerGroups = new ArrayList<>();
    private List<String> crawlerKeywords = new ArrayList<>();
    private List<String> crawlerExclude = new ArrayList<>();
    private Integer crawlerPages = 10;
    private Integer crawlerSleep = 900;
    private Boolean crawlerBot = false;
    private List<String> crawlerReplyKeywords = new ArrayList<>();
    private Integer crawlerMinReplyDelay = 30;
    private Integer crawlerMaxReplyDelay = 300;
    private Integer crawlerMaxHistoryPosts = 50;
    private Integer crawlerMaxHistoryComments = 200;
    private Boolean crawlerDebug = false;
    
    // Web配置
    private Integer webPort = 8080;
    private String dbPath = "./db.sqlite3";
    
    public String getGroupTopicsBaseUrl() {
        return doubanBaseHost + "/group/%s/discussion";
    }
    
    public String getGroupInfoBaseUrl() {
        return doubanBaseHost + "/group/%s/";
    }
}
