package server

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strconv"
	"strings"

	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/db/model"
	sqlitepkg "github.com/LLZ-DinosaurEgg/douban-crawler/internal/db/sqlite"
)

// Server Web服务器
type Server struct {
	db     *sqlitepkg.DB
	port   string
	router *http.ServeMux
}

// NewServer 创建Web服务器
func NewServer(db *sqlitepkg.DB, port string) *Server {
	s := &Server{
		db:     db,
		port:   port,
		router: http.NewServeMux(),
	}
	s.setupRoutes()
	return s
}

// setupRoutes 设置路由
func (s *Server) setupRoutes() {
	// 静态文件
	s.router.HandleFunc("/", s.handleIndex)
	s.router.Handle("/static/", http.StripPrefix("/static/", http.FileServer(http.Dir("./web/static"))))

	// API路由
	s.router.HandleFunc("/api/groups", s.handleGetGroups)
	s.router.HandleFunc("/api/posts", s.handleGetPosts)
	s.router.HandleFunc("/api/post/", s.handleGetPost)
	s.router.HandleFunc("/api/comments/", s.handleGetComments)
	s.router.HandleFunc("/api/stats", s.handleGetStats)
}

// Start 启动服务器
func (s *Server) Start() error {
	log.Printf("Web服务器启动在 http://localhost:%s", s.port)
	return http.ListenAndServe(":"+s.port, s.router)
}

// handleIndex 处理首页
func (s *Server) handleIndex(w http.ResponseWriter, r *http.Request) {
	http.ServeFile(w, r, "./web/index.html")
}

// handleGetGroups 获取所有小组
func (s *Server) handleGetGroups(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	groups, err := s.db.GetAllGroups()
	if err != nil {
		http.Error(w, fmt.Sprintf("获取小组失败: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"data":    groups,
	})
}

// handleGetPosts 获取帖子列表
func (s *Server) handleGetPosts(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	groupID := r.URL.Query().Get("group_id")
	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	if page < 1 {
		page = 1
	}
	pageSize, _ := strconv.Atoi(r.URL.Query().Get("page_size"))
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	posts, total, err := s.db.GetPostsWithPagination(groupID, page, pageSize)
	if err != nil {
		http.Error(w, fmt.Sprintf("获取帖子失败: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"data":    posts,
		"pagination": map[string]interface{}{
			"page":      page,
			"page_size": pageSize,
			"total":     total,
			"pages":     (total + pageSize - 1) / pageSize,
		},
	})
}

// handleGetPost 获取单个帖子详情
func (s *Server) handleGetPost(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	postID := strings.TrimPrefix(r.URL.Path, "/api/post/")
	if postID == "" {
		http.Error(w, "Post ID required", http.StatusBadRequest)
		return
	}

	post, err := s.db.GetPostByPostID(postID)
	if err != nil {
		http.Error(w, fmt.Sprintf("获取帖子失败: %v", err), http.StatusInternalServerError)
		return
	}

	if post == nil {
		http.Error(w, "Post not found", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"data":    post,
	})
}

// handleGetComments 获取评论列表
func (s *Server) handleGetComments(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	postID := strings.TrimPrefix(r.URL.Path, "/api/comments/")
	if postID == "" {
		http.Error(w, "Post ID required", http.StatusBadRequest)
		return
	}

	comments, err := s.db.GetCommentsByPostID(postID)
	if err != nil {
		http.Error(w, fmt.Sprintf("获取评论失败: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"data":    comments,
	})
}

// handleGetStats 获取统计信息
func (s *Server) handleGetStats(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// 获取小组数量
	groups, err := s.db.GetAllGroups()
	if err != nil {
		http.Error(w, fmt.Sprintf("获取统计信息失败: %v", err), http.StatusInternalServerError)
		return
	}

	// 获取统计信息
	stats, err := s.db.GetStats()
	if err != nil {
		http.Error(w, fmt.Sprintf("获取统计信息失败: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"data": map[string]interface{}{
			"groups_count":  stats["groups"],
			"posts_count":   stats["posts"],
			"comments_count": stats["comments"],
		},
	})
}
