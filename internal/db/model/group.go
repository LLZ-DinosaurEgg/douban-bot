package model

import (
	"database/sql"
	"time"
)

// Group 豆瓣小组模型
type Group struct {
	ID          string       `db:"id"`
	Name        string       `db:"name"`
	Alt         string       `db:"alt"`
	MemberCount int          `db:"member_count"`
	Created     time.Time    `db:"created"`
	CreatedAt   time.Time    `db:"created_at"`
	UpdatedAt   sql.NullTime `db:"updated_at"`
}

// TableName Group表名
func (g *Group) TableName() string {
	return "Group"
}
