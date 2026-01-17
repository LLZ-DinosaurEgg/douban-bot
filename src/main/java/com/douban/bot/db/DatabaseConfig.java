package com.douban.bot.db;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlStatements;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
public class DatabaseConfig {

    @Value("${app.db-path:./db.sqlite3}")
    private String dbPath;

    @Bean
    public DataSource dataSource() throws SQLException {
        // 确保数据库目录存在
        File dbFile = new File(dbPath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        String url = "jdbc:sqlite:" + dbPath;
        
        // 初始化表结构
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            initTables(stmt);
        }
        
        // 返回 DataSource (使用简单的实现)
        return new SimpleDataSource(url);
    }

    @Bean
    public Jdbi jdbi(DataSource dataSource) {
        Jdbi jdbi = Jdbi.create(dataSource);
        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.getConfig(SqlStatements.class).setUnusedBindingAllowed(true);
        return jdbi;
    }

    private void initTables(Statement stmt) throws SQLException {
        // Group表
        String groupTableSQL = """
            CREATE TABLE IF NOT EXISTS "Group" (
                "id" TEXT PRIMARY KEY NOT NULL UNIQUE,
                "name" TEXT NOT NULL,
                "alt" TEXT NOT NULL,
                "member_count" INTEGER NOT NULL,
                "created" TEXT NOT NULL,
                "created_at" TEXT NOT NULL DEFAULT (datetime('now'))
            );
            """;
        stmt.execute(groupTableSQL);

        // Post表
        String postTableSQL = """
            CREATE TABLE IF NOT EXISTS "Post" (
                "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                "post_id" TEXT NOT NULL UNIQUE,
                "group_id" TEXT NOT NULL,
                "author_info" TEXT NOT NULL DEFAULT '{}',
                "alt" TEXT NOT NULL,
                "title" TEXT NOT NULL,
                "content" TEXT NOT NULL,
                "photo_list" TEXT NOT NULL DEFAULT '[]',
                "rent" REAL,
                "subway" TEXT,
                "contact" TEXT,
                "is_matched" INTEGER NOT NULL DEFAULT 0,
                "keyword_list" TEXT NOT NULL DEFAULT '[]',
                "comment" TEXT,
                "is_collected" INTEGER NOT NULL DEFAULT 0,
                "created" TEXT NOT NULL,
                "updated" TEXT NOT NULL,
                "created_at" TEXT NOT NULL DEFAULT (datetime('now')),
                FOREIGN KEY (group_id) REFERENCES "Group"(id) ON DELETE CASCADE
            );
            """;
        stmt.execute(postTableSQL);

        // Comment表
        String commentTableSQL = """
            CREATE TABLE IF NOT EXISTS "Comment" (
                "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                "comment_id" TEXT NOT NULL UNIQUE,
                "post_id" TEXT NOT NULL,
                "group_id" TEXT NOT NULL,
                "author_info" TEXT NOT NULL DEFAULT '{}',
                "content" TEXT NOT NULL,
                "reply_to_id" TEXT,
                "like_count" INTEGER NOT NULL DEFAULT 0,
                "created" TEXT NOT NULL,
                "created_at" TEXT NOT NULL DEFAULT (datetime('now')),
                "updated_at" TEXT,
                FOREIGN KEY (post_id) REFERENCES "Post"(post_id) ON DELETE CASCADE,
                FOREIGN KEY (group_id) REFERENCES "Group"(id) ON DELETE CASCADE
            );
            """;
        stmt.execute(commentTableSQL);

        // CrawlerConfig表 - 存储爬虫配置
        String configTableSQL = """
            CREATE TABLE IF NOT EXISTS "CrawlerConfig" (
                "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                "name" TEXT NOT NULL,
                "group_url" TEXT NOT NULL,
                "group_id" TEXT NOT NULL,
                "keywords" TEXT NOT NULL DEFAULT '[]',
                "exclude_keywords" TEXT NOT NULL DEFAULT '[]',
                "pages" INTEGER NOT NULL DEFAULT 10,
                "sleep_seconds" INTEGER NOT NULL DEFAULT 900,
                "enabled" INTEGER NOT NULL DEFAULT 1,
                "created_at" TEXT NOT NULL DEFAULT (datetime('now')),
                "updated_at" TEXT NOT NULL DEFAULT (datetime('now'))
            );
            """;
        stmt.execute(configTableSQL);

        // BotConfig表 - 存储机器人配置
        String botConfigTableSQL = """
            CREATE TABLE IF NOT EXISTS "BotConfig" (
                "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                "enabled" INTEGER NOT NULL DEFAULT 0,
                "llm_api_type" TEXT NOT NULL DEFAULT 'openai',
                "llm_api_base" TEXT NOT NULL DEFAULT 'https://api.openai.com/v1',
                "llm_api_key" TEXT NOT NULL DEFAULT '',
                "llm_model" TEXT NOT NULL DEFAULT 'gpt-3.5-turbo',
                "llm_temperature" REAL NOT NULL DEFAULT 0.7,
                "llm_max_tokens" INTEGER NOT NULL DEFAULT 500,
                "reply_keywords" TEXT NOT NULL DEFAULT '[]',
                "min_reply_delay" INTEGER NOT NULL DEFAULT 30,
                "max_reply_delay" INTEGER NOT NULL DEFAULT 300,
                "max_history_posts" INTEGER NOT NULL DEFAULT 50,
                "max_history_comments" INTEGER NOT NULL DEFAULT 200,
                "updated_at" TEXT NOT NULL DEFAULT (datetime('now'))
            );
            """;
        stmt.execute(botConfigTableSQL);

        // 初始化默认配置（如果不存在）
        stmt.execute("""
            INSERT OR IGNORE INTO "BotConfig" (id, enabled) 
            SELECT 1, 0 
            WHERE NOT EXISTS (SELECT 1 FROM "BotConfig" WHERE id = 1)
            """);
        
        // 添加新字段（如果不存在）
        try {
            stmt.execute("ALTER TABLE \"BotConfig\" ADD COLUMN \"enable_style_learning\" INTEGER NOT NULL DEFAULT 1");
        } catch (SQLException e) {
            // 字段已存在，忽略错误
        }
        try {
            stmt.execute("ALTER TABLE \"BotConfig\" ADD COLUMN \"custom_prompt\" TEXT NOT NULL DEFAULT ''");
        } catch (SQLException e) {
            // 字段已存在，忽略错误
        }

        // 创建索引
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_comment_post_id ON \"Comment\"(post_id);");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_comment_group_id ON \"Comment\"(group_id);");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_config_enabled ON \"CrawlerConfig\"(enabled);");
    }
}
