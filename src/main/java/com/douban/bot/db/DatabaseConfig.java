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

        // 创建索引
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_comment_post_id ON \"Comment\"(post_id);");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_comment_group_id ON \"Comment\"(group_id);");
    }
}
