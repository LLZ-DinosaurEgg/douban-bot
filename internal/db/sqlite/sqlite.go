package sqlite

import (
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"time"

	_ "github.com/mattn/go-sqlite3"

	"github.com/LLZ-DinosaurEgg/douban-crawler/config"
	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/db/model"
)

// DB 数据库连接实例
type DB struct {
	conn *sql.DB
}

// NewSQLiteDB 创建SQLite数据库连接
func NewSQLiteDB(dbPath string) (*DB, error) {
	// 创建目录
	dir := filepath.Dir(dbPath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return nil, err
	}

	// 打开数据库
	conn, err := sql.Open("sqlite3", dbPath)
	if err != nil {
		return nil, err
	}

	// 测试连接
	if err := conn.Ping(); err != nil {
		return nil, err
	}

	// 设置连接池
	conn.SetMaxOpenConns(1)
	conn.SetMaxIdleConns(1)
	conn.SetConnMaxLifetime(time.Hour)

	// 初始化表结构
	if err := initTables(conn); err != nil {
		return nil, err
	}

	return &DB{conn: conn}, nil
}

// 初始化表结构
func initTables(conn *sql.DB) error {
	// Group表
	groupTableSQL := `
	CREATE TABLE IF NOT EXISTS "Group" (
		"id" TEXT PRIMARY KEY NOT NULL UNIQUE,
		"name" TEXT NOT NULL,
		"alt" TEXT NOT NULL,
		"member_count" INTEGER NOT NULL,
		"created" DATETIME NOT NULL,
		"created_at" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
	);
	`
	if _, err := conn.Exec(groupTableSQL); err != nil {
		return err
	}

	// Post表
	postTableSQL := `
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
		"is_matched" BOOLEAN NOT NULL DEFAULT false,
		"keyword_list" TEXT NOT NULL DEFAULT '[]',
		"comment" TEXT,
		"is_collected" BOOLEAN NOT NULL DEFAULT false,
		"created" DATETIME NOT NULL,
		"updated" DATETIME NOT NULL,
		"created_at" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
		FOREIGN KEY (group_id) REFERENCES "Group"(id) ON DELETE DO NOTHING
	);
	`
	if _, err := conn.Exec(postTableSQL); err != nil {
		return err
	}

	// Comment表
	commentTableSQL := `
	CREATE TABLE IF NOT EXISTS "Comment" (
		"id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
		"comment_id" TEXT NOT NULL UNIQUE,
		"post_id" TEXT NOT NULL,
		"group_id" TEXT NOT NULL,
		"author_info" TEXT NOT NULL DEFAULT '{}',
		"content" TEXT NOT NULL,
		"reply_to_id" TEXT,
		"like_count" INTEGER NOT NULL DEFAULT 0,
		"created" DATETIME NOT NULL,
		"created_at" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
		"updated_at" DATETIME,
		FOREIGN KEY (post_id) REFERENCES "Post"(post_id) ON DELETE CASCADE,
		FOREIGN KEY (group_id) REFERENCES "Group"(id) ON DELETE CASCADE
	);
	`
	if _, err := conn.Exec(commentTableSQL); err != nil {
		return err
	}

	// 创建索引
	if _, err := conn.Exec(`CREATE INDEX IF NOT EXISTS idx_comment_post_id ON "Comment"(post_id);`); err != nil {
		return err
	}
	if _, err := conn.Exec(`CREATE INDEX IF NOT EXISTS idx_comment_group_id ON "Comment"(group_id);`); err != nil {
		return err
	}

	return nil
}

// GetGroupByID 根据ID获取小组
func (d *DB) GetGroupByID(id string) (*model.Group, error) {
	row := d.conn.QueryRow(`SELECT id, name, alt, member_count, created, created_at FROM "Group" WHERE id = ?`, id)

	var group model.Group
	var createdStr, createdAtStr string
	err := row.Scan(&group.ID, &group.Name, &group.Alt, &group.MemberCount, &createdStr, &createdAtStr)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}

	// 解析时间
	group.Created, err = time.ParseInLocation(config.DATE_FORMAT, createdStr, time.Local)
	if err != nil {
		// 如果解析失败，尝试用 DATETIME_FORMAT
		group.Created, err = time.ParseInLocation(config.DATETIME_FORMAT, createdStr, time.Local)
		if err != nil {
			return nil, err
		}
	}
	group.CreatedAt, err = time.ParseInLocation(config.DATETIME_FORMAT, createdAtStr, time.Local)
	if err != nil {
		return nil, err
	}

	return &group, nil
}

// CreateGroup 创建小组
func (d *DB) CreateGroup(group *model.Group) error {
	if group == nil {
		return errors.New("group is nil")
	}
	_, err := d.conn.Exec(`
		INSERT INTO "Group" (id, name, alt, member_count, created, created_at)
		VALUES (?, ?, ?, ?, ?, ?)
	`, group.ID, group.Name, group.Alt, group.MemberCount, group.Created.Format(config.DATE_FORMAT), group.CreatedAt.Format(config.DATETIME_FORMAT))
	return err
}

// GetPostByPostID 根据PostID获取帖子
func (d *DB) GetPostByPostID(postID string) (*model.Post, error) {
	row := d.conn.QueryRow(`SELECT id, post_id, group_id, author_info, alt, title, content, photo_list, is_matched, keyword_list, created, updated FROM "Post" WHERE post_id = ?`, postID)

	var post model.Post
	var authorInfoStr, photoListStr, keywordListStr, createdStr, updatedStr string
	err := row.Scan(&post.ID, &post.PostID, &post.GroupID, &authorInfoStr, &post.Alt, &post.Title, &post.Content, &photoListStr, &post.IsMatched, &keywordListStr, &createdStr, &updatedStr)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}

	// 解析时间
	post.Created, err = time.ParseInLocation(config.DATETIME_FORMAT, createdStr, time.Local)
	if err != nil {
		return nil, err
	}
	post.Updated, err = time.ParseInLocation(config.DATETIME_FORMAT, updatedStr, time.Local)
	if err != nil {
		return nil, err
	}

	// 解析 JSON 字段
	if authorInfoStr != "" {
		var m map[string]interface{}
		if err := json.Unmarshal([]byte(authorInfoStr), &m); err == nil {
			post.AuthorInfo = m
		} else {
			post.AuthorInfo = map[string]interface{}{}
		}
	} else {
		post.AuthorInfo = map[string]interface{}{}
	}

	if photoListStr != "" {
		var arr []string
		if err := json.Unmarshal([]byte(photoListStr), &arr); err == nil {
			post.PhotoList = arr
		} else {
			post.PhotoList = []string{}
		}
	} else {
		post.PhotoList = []string{}
	}

	if keywordListStr != "" {
		var arr []string
		if err := json.Unmarshal([]byte(keywordListStr), &arr); err == nil {
			post.KeywordList = arr
		} else {
			post.KeywordList = []string{}
		}
	} else {
		post.KeywordList = []string{}
	}

	return &post, nil
}

// CreatePost 创建帖子
func (d *DB) CreatePost(post *model.Post) error {
	if post == nil {
		return errors.New("post is nil")
	}

	// 序列化JSON字段
	authorInfo, err := json.Marshal(post.AuthorInfo)
	if err != nil {
		return err
	}
	photoList, err := json.Marshal(post.PhotoList)
	if err != nil {
		return err
	}
	keywordList, err := json.Marshal(post.KeywordList)
	if err != nil {
		return err
	}

	_, err = d.conn.Exec(`
		INSERT INTO "Post" (post_id, group_id, author_info, alt, title, content, photo_list, is_matched, keyword_list, created, updated)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`, post.PostID, post.GroupID, string(authorInfo), post.Alt, post.Title, post.Content, string(photoList), post.IsMatched, string(keywordList), post.Created.Format(config.DATETIME_FORMAT), post.Updated.Format(config.DATETIME_FORMAT))
	return err
}

// UpdatePost 更新帖子
func (d *DB) UpdatePost(post *model.Post) error {
	_, err := d.conn.Exec(`
		UPDATE "Post" SET title = ?, updated = ? WHERE post_id = ?
	`, post.Title, post.Updated.Format(config.DATETIME_FORMAT), post.PostID)
	return err
}

// CheckPostTitleExists 检查标题是否存在
func (d *DB) CheckPostTitleExists(title string) (bool, error) {
	row := d.conn.QueryRow(`SELECT 1 FROM "Post" WHERE title = ? LIMIT 1`, title)
	var exists int
	err := row.Scan(&exists)
	if err != nil {
		if err == sql.ErrNoRows {
			return false, nil
		}
		return false, err
	}
	return true, nil
}

// CreateComment 创建评论
func (d *DB) CreateComment(comment *model.Comment) error {
	if comment == nil {
		return errors.New("comment is nil")
	}

	// 序列化JSON字段
	authorInfo, err := json.Marshal(comment.AuthorInfo)
	if err != nil {
		return err
	}

	_, err = d.conn.Exec(`
		INSERT INTO "Comment" (comment_id, post_id, group_id, author_info, content, reply_to_id, like_count, created)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?)
	`, comment.CommentID, comment.PostID, comment.GroupID, string(authorInfo), comment.Content, 
		comment.ReplyToID, comment.LikeCount, comment.Created.Format(config.DATETIME_FORMAT))
	return err
}

// GetCommentByCommentID 根据CommentID获取评论
func (d *DB) GetCommentByCommentID(commentID string) (*model.Comment, error) {
	row := d.conn.QueryRow(`SELECT id, comment_id, post_id, group_id, author_info, content, reply_to_id, like_count, created, created_at FROM "Comment" WHERE comment_id = ?`, commentID)

	var comment model.Comment
	var authorInfoStr, createdStr, createdAtStr string
	var replyToID sql.NullString
	err := row.Scan(&comment.ID, &comment.CommentID, &comment.PostID, &comment.GroupID, &authorInfoStr, 
		&comment.Content, &replyToID, &comment.LikeCount, &createdStr, &createdAtStr)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}

	comment.ReplyToID = replyToID

	// 解析时间
	comment.Created, err = time.ParseInLocation(config.DATETIME_FORMAT, createdStr, time.Local)
	if err != nil {
		return nil, err
	}
	comment.CreatedAt, err = time.ParseInLocation(config.DATETIME_FORMAT, createdAtStr, time.Local)
	if err != nil {
		return nil, err
	}

	// 解析 JSON 字段
	if authorInfoStr != "" {
		var m map[string]interface{}
		if err := json.Unmarshal([]byte(authorInfoStr), &m); err == nil {
			comment.AuthorInfo = m
		} else {
			comment.AuthorInfo = map[string]interface{}{}
		}
	} else {
		comment.AuthorInfo = map[string]interface{}{}
	}

	return &comment, nil
}

// GetCommentsByPostID 根据PostID获取所有评论
func (d *DB) GetCommentsByPostID(postID string) ([]*model.Comment, error) {
	rows, err := d.conn.Query(`SELECT id, comment_id, post_id, group_id, author_info, content, reply_to_id, like_count, created, created_at FROM "Comment" WHERE post_id = ? ORDER BY created ASC`, postID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var comments []*model.Comment
	for rows.Next() {
		var comment model.Comment
		var authorInfoStr, createdStr, createdAtStr string
		var replyToID sql.NullString
		err := rows.Scan(&comment.ID, &comment.CommentID, &comment.PostID, &comment.GroupID, &authorInfoStr,
			&comment.Content, &replyToID, &comment.LikeCount, &createdStr, &createdAtStr)
		if err != nil {
			continue
		}

		comment.ReplyToID = replyToID

		// 解析时间
		comment.Created, _ = time.ParseInLocation(config.DATETIME_FORMAT, createdStr, time.Local)
		comment.CreatedAt, _ = time.ParseInLocation(config.DATETIME_FORMAT, createdAtStr, time.Local)

		// 解析 JSON 字段
		if authorInfoStr != "" {
			var m map[string]interface{}
			if err := json.Unmarshal([]byte(authorInfoStr), &m); err == nil {
				comment.AuthorInfo = m
			} else {
				comment.AuthorInfo = map[string]interface{}{}
			}
		} else {
			comment.AuthorInfo = map[string]interface{}{}
		}

		comments = append(comments, &comment)
	}

	return comments, nil
}

// GetCommentsByGroupID 根据GroupID获取评论（用于学习风格）
func (d *DB) GetCommentsByGroupID(groupID string, limit int) ([]*model.Comment, error) {
	query := `SELECT id, comment_id, post_id, group_id, author_info, content, reply_to_id, like_count, created, created_at 
	          FROM "Comment" WHERE group_id = ? ORDER BY created DESC LIMIT ?`
	rows, err := d.conn.Query(query, groupID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var comments []*model.Comment
	for rows.Next() {
		var comment model.Comment
		var authorInfoStr, createdStr, createdAtStr string
		var replyToID sql.NullString
		err := rows.Scan(&comment.ID, &comment.CommentID, &comment.PostID, &comment.GroupID, &authorInfoStr,
			&comment.Content, &replyToID, &comment.LikeCount, &createdStr, &createdAtStr)
		if err != nil {
			continue
		}

		comment.ReplyToID = replyToID

		// 解析时间
		comment.Created, _ = time.ParseInLocation(config.DATETIME_FORMAT, createdStr, time.Local)
		comment.CreatedAt, _ = time.ParseInLocation(config.DATETIME_FORMAT, createdAtStr, time.Local)

		// 解析 JSON 字段
		if authorInfoStr != "" {
			var m map[string]interface{}
			if err := json.Unmarshal([]byte(authorInfoStr), &m); err == nil {
				comment.AuthorInfo = m
			} else {
				comment.AuthorInfo = map[string]interface{}{}
			}
		} else {
			comment.AuthorInfo = map[string]interface{}{}
		}

		comments = append(comments, &comment)
	}

	return comments, nil
}

// GetPostsByGroupID 根据GroupID获取帖子（用于学习风格）
func (d *DB) GetPostsByGroupID(groupID string, limit int) ([]*model.Post, error) {
	query := `SELECT id, post_id, group_id, author_info, alt, title, content, photo_list, is_matched, keyword_list, created, updated 
	          FROM "Post" WHERE group_id = ? ORDER BY created DESC LIMIT ?`
	rows, err := d.conn.Query(query, groupID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var posts []*model.Post
	for rows.Next() {
		var post model.Post
		var authorInfoStr, photoListStr, keywordListStr, createdStr, updatedStr string
		err := rows.Scan(&post.ID, &post.PostID, &post.GroupID, &authorInfoStr, &post.Alt, &post.Title, 
			&post.Content, &photoListStr, &post.IsMatched, &keywordListStr, &createdStr, &updatedStr)
		if err != nil {
			continue
		}

		// 解析时间
		post.Created, _ = time.ParseInLocation(config.DATETIME_FORMAT, createdStr, time.Local)
		post.Updated, _ = time.ParseInLocation(config.DATETIME_FORMAT, updatedStr, time.Local)

		// 解析 JSON 字段
		if authorInfoStr != "" {
			var m map[string]interface{}
			if err := json.Unmarshal([]byte(authorInfoStr), &m); err == nil {
				post.AuthorInfo = m
			} else {
				post.AuthorInfo = map[string]interface{}{}
			}
		} else {
			post.AuthorInfo = map[string]interface{}{}
		}

		if photoListStr != "" {
			var arr []string
			if err := json.Unmarshal([]byte(photoListStr), &arr); err == nil {
				post.PhotoList = arr
			} else {
				post.PhotoList = []string{}
			}
		} else {
			post.PhotoList = []string{}
		}

		if keywordListStr != "" {
			var arr []string
			if err := json.Unmarshal([]byte(keywordListStr), &arr); err == nil {
				post.KeywordList = arr
			} else {
				post.KeywordList = []string{}
			}
		} else {
			post.KeywordList = []string{}
		}

		posts = append(posts, &post)
	}

	return posts, nil
}

// Close 关闭数据库连接
func (d *DB) Close() error {
	return d.conn.Close()
}
