package model

import (
	"database/sql"
	"time"
)

// Comment 豆瓣评论（回复）模型
type Comment struct {
	ID          int64                  `db:"id"`
	CommentID   string                 `db:"comment_id"`   // 评论的唯一ID
	PostID      string                 `db:"post_id"`      // 所属帖子ID
	GroupID     string                 `db:"group_id"`     // 所属小组ID
	AuthorInfo  map[string]interface{} `db:"author_info"`  // 作者信息（JSON）
	Content     string                 `db:"content"`      // 评论内容
	ReplyToID   sql.NullString         `db:"reply_to_id"`  // 回复的评论ID（如果是回复评论）
	LikeCount   int                    `db:"like_count"`   // 点赞数
	Created     time.Time              `db:"created"`      // 评论创建时间
	CreatedAt   time.Time              `db:"created_at"`   // 记录创建时间
	UpdatedAt   sql.NullTime           `db:"updated_at"`   // 记录更新时间
}

// TableName Comment表名
func (c *Comment) TableName() string {
	return "Comment"
}
