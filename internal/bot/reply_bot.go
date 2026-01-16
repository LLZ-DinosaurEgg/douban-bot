package bot

import (
	"fmt"
	"log"
	"math/rand"
	"strings"
	"time"

	"github.com/LLZ-DinosaurEgg/douban-crawler/config"
	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/db/model"
	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/llm"
	sqlitepkg "github.com/LLZ-DinosaurEgg/douban-crawler/internal/db/sqlite"
)

// ReplyBot 自动回复机器人
type ReplyBot struct {
	db           *sqlitepkg.DB
	llmClient    *llm.Client
	styleLearner *StyleLearner
	config       *config.BotConfig
}

// NewReplyBot 创建自动回复机器人
func NewReplyBot(db *sqlitepkg.DB, botConfig *config.BotConfig) *ReplyBot {
	return &ReplyBot{
		db:           db,
		llmClient:    llm.NewClient(),
		styleLearner: NewStyleLearner(db),
		config:       botConfig,
	}
}

// ProcessNewPosts 处理新帖子，决定是否需要回复
func (rb *ReplyBot) ProcessNewPosts(groupID string) error {
	if !rb.config.EnableAutoReply {
		return nil
	}

	// 获取小组信息
	group, err := rb.db.GetGroupByID(groupID)
	if err != nil {
		return fmt.Errorf("获取小组信息失败: %v", err)
	}
	if group == nil {
		return fmt.Errorf("小组不存在: %s", groupID)
	}

	// 学习小组风格
	analysis, err := rb.styleLearner.LearnGroupStyle(groupID, rb.config.MaxHistoryPosts, rb.config.MaxHistoryComments)
	if err != nil {
		log.Printf("学习小组风格失败: %v", err)
		// 继续执行，使用默认风格
		analysis = &StyleAnalysis{
			AverageLength: 50,
			CommonWords:   []string{},
			CommonPhrases: []string{},
			CommonPatterns: []string{},
		}
	}

	// 构建系统提示词
	systemPrompt := rb.styleLearner.BuildSystemPrompt(group.Name, analysis)

	// 获取最近匹配的帖子（需要回复的帖子）
	posts, err := rb.db.GetPostsByGroupID(groupID, 50) // 获取最近50个帖子
	if err != nil {
		return fmt.Errorf("获取帖子失败: %v", err)
	}

	// 检查每个帖子是否需要回复
	for _, post := range posts {
		// 检查是否已经回复过（通过检查评论中是否有我们的回复）
		comments, err := rb.db.GetCommentsByPostID(post.PostID)
		if err != nil {
			log.Printf("获取评论失败: %v", err)
			continue
		}

		// 检查是否需要回复
		shouldReply := rb.shouldReply(post, comments)
		if !shouldReply {
			continue
		}

		// 生成回复
		reply, err := rb.generateReply(systemPrompt, post)
		if err != nil {
			log.Printf("生成回复失败: %v", err)
			continue
		}

		// 发送回复（这里只是记录，实际发送需要调用豆瓣API）
		log.Printf("为帖子 %s 生成回复: %s", post.PostID, reply)
		
		// TODO: 这里可以添加实际发送回复到豆瓣的逻辑
		// 由于需要登录和API调用，这里先记录回复内容
		// 可以保存到数据库的某个字段，或者通过其他方式发送

		// 随机延迟，避免被检测
		delay := rand.Intn(rb.config.MaxReplyDelay-rb.config.MinReplyDelay) + rb.config.MinReplyDelay
		time.Sleep(time.Duration(delay) * time.Second)
	}

	return nil
}

// shouldReply 判断是否需要回复
func (rb *ReplyBot) shouldReply(post *model.Post, comments []*model.Comment) bool {
	// 如果帖子不匹配关键词，不回复
	if !post.IsMatched {
		return false
	}

	// 如果配置了回复关键词，检查是否匹配
	if len(rb.config.ReplyKeywords) > 0 {
		matched := false
		for _, keyword := range rb.config.ReplyKeywords {
			if strings.Contains(post.Title, keyword) || strings.Contains(post.Content, keyword) {
				matched = true
				break
			}
		}
		if !matched {
			return false
		}
	}

	// 如果已经有太多评论，可能不需要回复
	if len(comments) > 20 {
		return false
	}

	// 如果帖子太旧（超过7天），不回复
	if time.Since(post.Created) > 7*24*time.Hour {
		return false
	}

	return true
}

// generateReply 生成回复内容
func (rb *ReplyBot) generateReply(systemPrompt string, post *model.Post) (string, error) {
	// 构建用户提示词
	userPrompt := fmt.Sprintf(`请为以下帖子生成一个合适的回复：

标题：%s

内容：%s

请生成一个自然、友好的回复，符合该小组的交流风格。`,
		post.Title,
		post.Content)

	// 调用大模型生成回复
	reply, err := rb.llmClient.GenerateReply(systemPrompt, userPrompt)
	if err != nil {
		return "", fmt.Errorf("生成回复失败: %v", err)
	}

	// 清理回复（移除可能的引号等）
	reply = strings.TrimSpace(reply)
	reply = strings.Trim(reply, `"`)
	reply = strings.Trim(reply, `'`)

	// 限制回复长度
	maxLen := 500
	if len(reply) > maxLen {
		reply = reply[:maxLen] + "..."
	}

	return reply, nil
}

// ReplyToPost 回复指定帖子
func (rb *ReplyBot) ReplyToPost(postID string) (string, error) {
	// 获取帖子
	post, err := rb.db.GetPostByPostID(postID)
	if err != nil {
		return "", fmt.Errorf("获取帖子失败: %v", err)
	}
	if post == nil {
		return "", fmt.Errorf("帖子不存在: %s", postID)
	}

	// 获取小组信息
	group, err := rb.db.GetGroupByID(post.GroupID)
	if err != nil {
		return "", fmt.Errorf("获取小组信息失败: %v", err)
	}

	// 学习小组风格
	analysis, err := rb.styleLearner.LearnGroupStyle(post.GroupID, rb.config.MaxHistoryPosts, rb.config.MaxHistoryComments)
	if err != nil {
		log.Printf("学习小组风格失败: %v", err)
		analysis = &StyleAnalysis{
			AverageLength: 50,
			CommonWords:   []string{},
			CommonPhrases: []string{},
			CommonPatterns: []string{},
		}
	}

	// 构建系统提示词
	systemPrompt := rb.styleLearner.BuildSystemPrompt(group.Name, analysis)

	// 生成回复
	return rb.generateReply(systemPrompt, post)
}
