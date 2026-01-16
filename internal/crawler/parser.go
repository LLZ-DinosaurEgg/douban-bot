package crawler

import (
	"fmt"
	"regexp"
	"strings"
	"time"

	"github.com/PuerkitoBio/goquery"
	"github.com/LLZ-DinosaurEgg/douban-crawler/config"
	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/db/model"
)

// ParseGroupInfo 解析小组信息
func ParseGroupInfo(doc *goquery.Document, groupID string) (*model.Group, error) {
	// 获取小组名称
	name := doc.Find("h1").Text()
	name = strings.TrimSpace(name)

	// 获取成员数
	memberCount := 0
	doc.Find(`a[href="https://www.douban.com/group/` + groupID + `/members"]`).Each(func(i int, s *goquery.Selection) {
		text := s.Text()
		re := regexp.MustCompile(`\(([\d万\+]+)\)`)
		matches := re.FindStringSubmatch(text)
		if len(matches) > 1 {
			// 处理万为单位或带加号
			numStr := matches[1]
			numStr = strings.ReplaceAll(numStr, "+", "")
			if strings.Contains(numStr, "万") {
				numStr = strings.ReplaceAll(numStr, "万", "")
				var f float64
				fmt.Sscanf(numStr, "%f", &f)
				memberCount = int(f * 10000)
			} else {
				fmt.Sscanf(numStr, "%d", &memberCount)
			}
		}
	})

	// 获取创建时间
	created := time.Now()
	doc.Find(".group-loc").Each(func(i int, s *goquery.Selection) {
		text := s.Text()
		re := regexp.MustCompile(`创建于(.+?)\s`)
		matches := re.FindStringSubmatch(text)
		if len(matches) > 1 {
			dateStr := strings.TrimSpace(matches[1])
			t, err := time.ParseInLocation(config.DATE_FORMAT, dateStr, time.Local)
			if err == nil {
				created = t
			}
		}
	})

	// 构建小组对象
	group := &model.Group{
		ID:          groupID,
		Name:        name,
		Alt:         fmt.Sprintf(config.GROUP_INFO_BASE_URL, groupID),
		MemberCount: memberCount,
		Created:     created,
		CreatedAt:   time.Now(),
	}

	return group, nil
}

// ParsePosts 解析帖子列表
func ParsePosts(doc *goquery.Document) ([]map[string]interface{}, error) {
	var posts []map[string]interface{}

	// 遍历帖子行
	doc.Find(`table.olt tr`).Each(func(i int, s *goquery.Selection) {
		// 有些 tr 可能是表头或其他，跳过无链接的
		link := s.Find(`td.title a`)
		if link.Length() == 0 {
			return
		}
		href, _ := link.Attr("href")
		title := strings.TrimSpace(link.Text())

		// 解析帖子ID
		re := regexp.MustCompile(`https?://www\.douban\.com/group/topic/(\d+)/`)
		matches := re.FindStringSubmatch(href)
		if len(matches) < 2 {
			return
		}
		postID := matches[1]

		// 获取作者信息
		authorLink := s.Find("td").Eq(1).Find("a")
		authorName := strings.TrimSpace(authorLink.Text())
		authorHref, _ := authorLink.Attr("href")

		// 获取更新时间（表格里可能只有日期或时间）
		updateTime := strings.TrimSpace(s.Find("td").Eq(3).Text())
		updated := time.Now().Format(config.DATE_FORMAT) + " " + updateTime + ":00"

		// 构建帖子临时对象
		post := map[string]interface{}{
			"id":    postID,
			"title": title,
			"alt":   href,
			"author": map[string]string{
				"name": authorName,
				"alt":  authorHref,
			},
			"updated": updated,
		}

		posts = append(posts, post)
	})

	return posts, nil
}

// ParsePostDetail 解析帖子详情
func ParsePostDetail(doc *goquery.Document) (map[string]interface{}, error) {
	// 获取内容
	content := doc.Find(`div.topic-content`).Text()
	content = strings.TrimSpace(content)

	// 获取图片列表
	var photos []string
	doc.Find(`div.topic-content img`).Each(func(i int, s *goquery.Selection) {
		src, _ := s.Attr("src")
		if src != "" {
			photos = append(photos, src)
		}
	})

	// 获取创建时间
	created := strings.TrimSpace(doc.Find(`.create-time`).Text())

	// 构建详情对象
	detail := map[string]interface{}{
		"content": content,
		"photos":  photos,
		"created": created,
	}

	return detail, nil
}

// ParseComments 解析评论列表
func ParseComments(doc *goquery.Document) ([]map[string]interface{}, error) {
	var comments []map[string]interface{}

	// 豆瓣评论通常在 .comment-item 或类似的class中
	// 这里需要根据实际页面结构调整选择器
	doc.Find(`.comment-item, .reply-item`).Each(func(i int, s *goquery.Selection) {
		// 获取评论ID（从data-id或链接中提取）
		commentID := ""
		if dataID, ok := s.Attr("data-id"); ok {
			commentID = dataID
		} else {
			// 尝试从链接中提取
			link := s.Find("a").First()
			if href, ok := link.Attr("href"); ok {
				re := regexp.MustCompile(`comment/(\d+)`)
				matches := re.FindStringSubmatch(href)
				if len(matches) > 1 {
					commentID = matches[1]
				}
			}
		}
		if commentID == "" {
			// 如果没有找到ID，使用索引作为临时ID
			commentID = fmt.Sprintf("temp_%d_%d", time.Now().Unix(), i)
		}

		// 获取评论内容
		content := strings.TrimSpace(s.Find(`.reply-content, .comment-content, p`).Text())
		if content == "" {
			// 尝试其他选择器
			content = strings.TrimSpace(s.Find(`div`).Not(`.author, .time`).Text())
		}

		// 获取作者信息
		authorName := strings.TrimSpace(s.Find(`.author, .comment-author, a[href*="/people/"]`).First().Text())
		authorHref := ""
		if authorLink := s.Find(`a[href*="/people/"]`).First(); authorLink.Length() > 0 {
			authorHref, _ = authorLink.Attr("href")
		}

		// 获取回复的评论ID（如果有）
		replyToID := ""
		if replyLink := s.Find(`a[href*="#comment"]`).First(); replyLink.Length() > 0 {
			if href, ok := replyLink.Attr("href"); ok {
				re := regexp.MustCompile(`#comment-(\d+)`)
				matches := re.FindStringSubmatch(href)
				if len(matches) > 1 {
					replyToID = matches[1]
				}
			}
		}

		// 获取点赞数
		likeCount := 0
		likeText := strings.TrimSpace(s.Find(`.like-count, .vote-count`).Text())
		if likeText != "" {
			fmt.Sscanf(likeText, "%d", &likeCount)
		}

		// 获取创建时间
		timeText := strings.TrimSpace(s.Find(`.time, .comment-time, .pubtime`).Text())
		created := time.Now().Format(config.DATETIME_FORMAT)
		if timeText != "" {
			// 尝试解析时间（豆瓣的时间格式可能是"2024-01-01 12:00:00"或相对时间）
			created = timeText
		}

		comment := map[string]interface{}{
			"id":        commentID,
			"content":   content,
			"author": map[string]string{
				"name": authorName,
				"alt":  authorHref,
			},
			"reply_to_id": replyToID,
			"like_count":  likeCount,
			"created":     created,
		}

		if content != "" {
			comments = append(comments, comment)
		}
	})

	return comments, nil
}
