package model

import (
	"database/sql"
	"time"
)

// Post 豆瓣帖子模型w
type Post struct {
	ID          int64                  `db:"id"`
	PostID      string                 `db:"post_id"`
	GroupID     string                 `db:"group_id"`
	AuthorInfo  map[string]interface{} `db:"author_info"` // 内存中为结构化类型，DB 中以 JSON 字符串存储
	Alt         string                 `db:"alt"`
	Title       string                 `db:"title"`
	Content     string                 `db:"content"`
	PhotoList   []string               `db:"photo_list"`  // 内存为切片，DB 中以 JSON 存储
	Rent        sql.NullFloat64        `db:"rent"`
	Subway      sql.NullString         `db:"subway"`
	Contact     sql.NullString         `db:"contact"`
	IsMatched   bool                   `db:"is_matched"`
	KeywordList []string               `db:"keyword_list"` // 内存为切片，DB 中以 JSON 存储
	Comment     sql.NullString         `db:"comment"`
	IsCollected bool                   `db:"is_collected"`
	Created     time.Time              `db:"created"`
	Updated     time.Time              `db:"updated"`
	CreatedAt   time.Time              `db:"created_at"`
	UpdatedAt   sql.NullTime           `db:"updated_at"`
}

// TableName Post表名
func (p *Post) TableName() string {
	return "Post"
}
