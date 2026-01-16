package crawler

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"github.com/PuerkitoBio/goquery"

	"github.com/LLZ-DinosaurEgg/douban-crawler/config"
	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/db/model"
	sqlitepkg "github.com/LLZ-DinosaurEgg/douban-crawler/internal/db/sqlite"
	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/utils"
)

// Crawler 爬虫结构体
type Crawler struct {
	db     *sqlitepkg.DB
	client *http.Client
}

// NewCrawler 创建爬虫实例
func NewCrawler(db *sqlitepkg.DB) *Crawler {
	return &Crawler{
		db:     db,
		client: utils.NewHTTPClient(),
	}
}

// Crawl 爬取指定小组
func (c *Crawler) Crawl(groupID string, pages int, keywords, exclude []string) error {
	log.Printf("开始爬取小组: %s", groupID)

	// 检查小组是否存在
	group, err := c.db.GetGroupByID(groupID)
	if err != nil {
		return err
	}

	// 如果小组不存在，先爬取小组信息
	if group == nil {
		group, err = c.crawlGroupInfo(groupID)
		if err != nil {
			return err
		}
		if err := c.db.CreateGroup(group); err != nil {
			return err
		}
		log.Printf("创建小组: %s 成功", groupID)
	}

	// 爬取帖子
	for page := 0; page < pages; page++ {
		// 随机睡眠
		time.Sleep(utils.GetRandomSleep(5000, 8000))

		// 构建请求URL
		reqURL := fmt.Sprintf(config.GROUP_TOPICS_BASE_URL, groupID)
		params := url.Values{}
		params.Add("start", fmt.Sprintf("%d", page*25))
		reqURL += "?" + params.Encode()

		// 发送请求
		req, err := http.NewRequest("GET", reqURL, nil)
		if err != nil {
			log.Printf("创建请求失败: %v", err)
			continue
		}
		req.Header.Set("User-Agent", utils.GetRandomUserAgent())
		req.Header.Set("Cookie", config.COOKIE)

		resp, err := c.client.Do(req)
		if err != nil {
			log.Printf("请求失败: %v", err)
			continue
		}
		if resp.Body == nil {
			resp.Body.Close()
			continue
		}
		defer resp.Body.Close()

		if resp.StatusCode != 200 {
			log.Printf("请求失败，状态码: %d", resp.StatusCode)
			continue
		}

		// 解析帖子列表
		doc, err := goquery.NewDocumentFromReader(resp.Body)
		if err != nil {
			log.Printf("解析HTML失败: %v", err)
			continue
		}

		posts, err := ParsePosts(doc)
		if err != nil {
			log.Printf("解析帖子列表失败: %v", err)
			continue
		}

		// 处理每个帖子
		for _, post := range posts {
			c.processPost(post, group, keywords, exclude)
		}
	}

	return nil
}

// crawlGroupInfo 爬取小组信息
func (c *Crawler) crawlGroupInfo(groupID string) (*model.Group, error) {
	// 构建请求URL
	reqURL := fmt.Sprintf(config.GROUP_INFO_BASE_URL, groupID)

	// 发送请求
	req, err := http.NewRequest("GET", reqURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", utils.GetRandomUserAgent())
	req.Header.Set("Cookie", config.COOKIE)

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, err
	}
	if resp.Body == nil {
		resp.Body.Close()
		return nil, fmt.Errorf("empty response body")
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("请求小组信息失败，状态码: %d", resp.StatusCode)
	}

	// 解析HTML
	doc, err := goquery.NewDocumentFromReader(resp.Body)
	if err != nil {
		return nil, err
	}

	// 解析小组信息
	return ParseGroupInfo(doc, groupID)
}

// processPost 处理单个帖子
func (c *Crawler) processPost(postMap map[string]interface{}, group *model.Group, keywords, exclude []string) {
	// 检查排除关键词
	title, _ := postMap["title"].(string)
	// 先爬取详情获取内容
	detail, err := c.crawlPostDetail(postMap["alt"].(string))
	if err != nil {
		log.Printf("爬取帖子详情失败: %v", err)
		return
	}
	content, _ := detail["content"].(string)

	// 检查排除关键词
	for _, e := range exclude {
		if e == "" {
			continue
		}
		if strings.Contains(title, e) || strings.Contains(content, e) {
			return
		}
	}

	// 检查帖子是否已存在
	postID, _ := postMap["id"].(string)
	existing, err := c.db.GetPostByPostID(postID)
	if err != nil {
		log.Printf("查询帖子失败: %v", err)
		return
	}

	// 如果帖子已存在，更新
	if existing != nil {
		updatedStr, _ := postMap["updated"].(string)
		updated, err := time.ParseInLocation(config.DATETIME_FORMAT, updatedStr, time.Local)
		if err != nil {
			log.Printf("解析时间失败: %v", err)
			return
		}
		existing.Title = title
		existing.Updated = updated
		if err := c.db.UpdatePost(existing); err != nil {
			log.Printf("更新帖子失败: %v", err)
		}
		log.Printf("更新帖子: %s", postID)
		return
	}

	// 检查标题是否重复
	existsTitle, err := c.db.CheckPostTitleExists(title)
	if err != nil {
		log.Printf("检查标题失败: %v", err)
		return
	}
	if existsTitle {
		log.Printf("标题重复，忽略: %s", title)
		return
	}

	// 匹配关键词
	var keywordList []string
	isMatched := false
	for _, k := range keywords {
		if k == "" {
			continue
		}
		// 安全构建正则：先转义特殊字符，再做模糊匹配
		escaped := regexp.QuoteMeta(k)
		pattern := strings.Join(strings.Split(escaped, ""), ".?")
		re, err := regexp.Compile(pattern)
		if err != nil {
			continue
		}
		if re.MatchString(title) || re.MatchString(content) {
			keywordList = append(keywordList, k)
			isMatched = true
		}
	}

	// 解析时间
	createdStr, _ := detail["created"].(string)
	created, err := time.ParseInLocation(config.DATETIME_FORMAT, createdStr, time.Local)
	if err != nil {
		// 尝试更宽松解析
		created = time.Now()
	}
	updatedStr, _ := postMap["updated"].(string)
	updated, err := time.ParseInLocation(config.DATETIME_FORMAT, updatedStr, time.Local)
	if err != nil {
		updated = time.Now()
	}

	// 构建帖子对象
	newPost := &model.Post{
		PostID:      postID,
		GroupID:     group.ID,
		AuthorInfo:  map[string]interface{}{},
		Alt:         postMap["alt"].(string),
		Title:       title,
		Content:     content,
		PhotoList:   []string{},
		IsMatched:   isMatched,
		KeywordList: keywordList,
		Created:     created,
		Updated:     updated,
	}

	// 从 postMap.author 尝试复制 author 信息（如果有）
	if authorRaw, ok := postMap["author"]; ok {
		// authorRaw 可能是 map[string]string
		if m, ok := authorRaw.(map[string]string); ok {
			j, _ := json.Marshal(m)
			var mm map[string]interface{}
			_ = json.Unmarshal(j, &mm)
			newPost.AuthorInfo = mm
		}
	}

	// 从 detail.photos 尝试复制图片
	if photos, ok := detail["photos"].([]string); ok {
		newPost.PhotoList = photos
	} else if ifacePhotos, ok := detail["photos"].([]interface{}); ok {
		for _, pi := range ifacePhotos {
			if s, ok := pi.(string); ok {
				newPost.PhotoList = append(newPost.PhotoList, s)
			}
		}
	}

	// 保存帖子
	if err := c.db.CreatePost(newPost); err != nil {
		log.Printf("保存帖子失败: %v", err)
		return
	}
	log.Printf("保存帖子: %s", postID)

	// 爬取并保存评论
	if err := c.crawlAndSaveComments(postID, group.ID, postMap["alt"].(string)); err != nil {
		log.Printf("爬取评论失败: %v", err)
	}
}

// crawlPostDetail 爬取帖子详情
func (c *Crawler) crawlPostDetail(url string) (map[string]interface{}, error) {
	// 随机睡眠
	time.Sleep(utils.GetRandomSleep(2500, 7500))

	// 发送请求
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", utils.GetRandomUserAgent())
	req.Header.Set("Cookie", config.COOKIE)

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, err
	}
	if resp.Body == nil {
		resp.Body.Close()
		return nil, fmt.Errorf("empty response body")
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("请求帖子详情失败，状态码: %d", resp.StatusCode)
	}

	// 解析HTML
	doc, err := goquery.NewDocumentFromReader(resp.Body)
	if err != nil {
		return nil, err
	}

	// 解析帖子详情
	return ParsePostDetail(doc)
}

// crawlAndSaveComments 爬取并保存评论
func (c *Crawler) crawlAndSaveComments(postID, groupID, postURL string) error {
	// 随机睡眠
	time.Sleep(utils.GetRandomSleep(2000, 5000))

	// 发送请求
	req, err := http.NewRequest("GET", postURL, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", utils.GetRandomUserAgent())
	req.Header.Set("Cookie", config.COOKIE)

	resp, err := c.client.Do(req)
	if err != nil {
		return err
	}
	if resp.Body == nil {
		resp.Body.Close()
		return fmt.Errorf("empty response body")
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return fmt.Errorf("请求评论失败，状态码: %d", resp.StatusCode)
	}

	// 解析HTML
	doc, err := goquery.NewDocumentFromReader(resp.Body)
	if err != nil {
		return err
	}

	// 解析评论
	comments, err := ParseComments(doc)
	if err != nil {
		return err
	}

	// 保存评论
	for _, commentMap := range comments {
		commentID, _ := commentMap["id"].(string)
		content, _ := commentMap["content"].(string)
		replyToID, _ := commentMap["reply_to_id"].(string)
		likeCount, _ := commentMap["like_count"].(int)
		createdStr, _ := commentMap["created"].(string)

		// 检查评论是否已存在
		existing, err := c.db.GetCommentByCommentID(commentID)
		if err != nil {
			log.Printf("查询评论失败: %v", err)
			continue
		}
		if existing != nil {
			continue // 评论已存在，跳过
		}

		// 解析时间
		created, err := time.ParseInLocation(config.DATETIME_FORMAT, createdStr, time.Local)
		if err != nil {
			created = time.Now()
		}

		// 构建评论对象
		newComment := &model.Comment{
			CommentID:  commentID,
			PostID:     postID,
			GroupID:    groupID,
			AuthorInfo: map[string]interface{}{},
			Content:    content,
			LikeCount:  likeCount,
			Created:    created,
			CreatedAt:  time.Now(),
		}

		// 设置回复ID
		if replyToID != "" {
			newComment.ReplyToID = sql.NullString{String: replyToID, Valid: true}
		}

		// 从 commentMap.author 复制作者信息
		if authorRaw, ok := commentMap["author"]; ok {
			if m, ok := authorRaw.(map[string]string); ok {
				j, _ := json.Marshal(m)
				var mm map[string]interface{}
				_ = json.Unmarshal(j, &mm)
				newComment.AuthorInfo = mm
			}
		}

		// 保存评论
		if err := c.db.CreateComment(newComment); err != nil {
			log.Printf("保存评论失败: %v", err)
			continue
		}
		log.Printf("保存评论: %s (帖子: %s)", commentID, postID)
	}

	return nil
}
