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
        
        // 初始化表结构，如果数据库损坏则自动重建
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            // 先尝试执行一个简单查询来检测数据库是否损坏
            try {
                stmt.execute("PRAGMA integrity_check;");
            } catch (SQLException e) {
                // 如果数据库损坏，备份并重建
                if (e.getMessage().contains("malformed") || e.getMessage().contains("corrupt")) {
                    System.err.println("检测到数据库损坏，正在备份并重建数据库...");
                    backupAndRecreateDatabase(dbFile);
                    // 重新连接
                    try (Connection newConn = DriverManager.getConnection(url);
                         Statement newStmt = newConn.createStatement()) {
                        initTables(newStmt);
                    }
                    System.out.println("数据库重建完成");
                    return new SimpleDataSource(url);
                } else {
                    throw e;
                }
            }
            initTables(stmt);
        } catch (SQLException e) {
            // 如果初始化表时出错，可能是数据库损坏
            if (e.getMessage().contains("malformed") || e.getMessage().contains("corrupt")) {
                System.err.println("检测到数据库损坏，正在备份并重建数据库...");
                backupAndRecreateDatabase(dbFile);
                // 重新连接并初始化
                try (Connection conn = DriverManager.getConnection(url);
                     Statement stmt = conn.createStatement()) {
                    initTables(stmt);
                }
                System.out.println("数据库重建完成");
            } else {
                throw e;
            }
        }
        
        // 返回 DataSource (使用简单的实现)
        return new SimpleDataSource(url);
    }
    
    private void backupAndRecreateDatabase(File dbFile) {
        if (dbFile.exists()) {
            try {
                // 备份损坏的数据库文件
                File backupFile = new File(dbFile.getParent(), dbFile.getName() + ".corrupt." + System.currentTimeMillis());
                if (dbFile.renameTo(backupFile)) {
                    System.out.println("已备份损坏的数据库文件到: " + backupFile.getName());
                } else {
                    // 如果重命名失败，尝试复制
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(dbFile);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(backupFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    dbFile.delete();
                    System.out.println("已备份损坏的数据库文件到: " + backupFile.getName());
                }
            } catch (Exception e) {
                System.err.println("备份数据库文件失败: " + e.getMessage());
                // 如果备份失败，直接删除损坏的文件
                if (dbFile.exists()) {
                    dbFile.delete();
                    System.out.println("已删除损坏的数据库文件");
                }
            }
        }
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
        try {
            stmt.execute("ALTER TABLE \"CrawlerConfig\" ADD COLUMN \"cookie\" TEXT NOT NULL DEFAULT ''");
        } catch (SQLException e) {
            // 字段已存在，忽略错误
        }
        try {
            stmt.execute("ALTER TABLE \"BotConfig\" ADD COLUMN \"cookie\" TEXT NOT NULL DEFAULT ''");
        } catch (SQLException e) {
            // 字段已存在，忽略错误
        }
        try {
            stmt.execute("ALTER TABLE \"CrawlerConfig\" ADD COLUMN \"crawl_comments\" INTEGER NOT NULL DEFAULT 1");
        } catch (SQLException e) {
            // 字段已存在，忽略错误
        }
        try {
            stmt.execute("ALTER TABLE \"Post\" ADD COLUMN \"bot_replied\" INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            // 字段已存在，忽略错误
        }
        try {
            stmt.execute("ALTER TABLE \"Post\" ADD COLUMN \"bot_reply_content\" TEXT");
        } catch (SQLException e) {
            // 字段已存在，忽略错误
        }
        try {
            stmt.execute("ALTER TABLE \"Post\" ADD COLUMN \"bot_reply_at\" TEXT");
        } catch (SQLException e) {
            // 字段已存在，忽略错误
        }
        try {
            stmt.execute("ALTER TABLE \"BotConfig\" ADD COLUMN \"reply_speed_multiplier\" REAL NOT NULL DEFAULT 1.0");
        } catch (SQLException e) {
            // 字段已存在，忽略错误
        }
        try {
            stmt.execute("ALTER TABLE \"BotConfig\" ADD COLUMN \"reply_check_interval\" INTEGER NOT NULL DEFAULT 300");
        } catch (SQLException e) {
            // 字段已存在，忽略错误
        }
        try {
            stmt.execute("ALTER TABLE \"BotConfig\" ADD COLUMN \"reply_task_interval\" INTEGER NOT NULL DEFAULT 300");
        } catch (SQLException e) {
            // 字段已存在，忽略错误
        }
        try {
            stmt.execute("ALTER TABLE \"BotConfig\" ADD COLUMN \"reply_task_interval\" INTEGER NOT NULL DEFAULT 60");
        } catch (SQLException e) {
            // 字段已存在，忽略错误
        }

        // 创建索引
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_comment_post_id ON \"Comment\"(post_id);");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_comment_group_id ON \"Comment\"(group_id);");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_config_enabled ON \"CrawlerConfig\"(enabled);");
    }
}
