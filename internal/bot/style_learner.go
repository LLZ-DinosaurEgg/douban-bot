package bot

import (
	"fmt"
	"strings"

	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/db/model"
	sqlitepkg "github.com/LLZ-DinosaurEgg/douban-crawler/internal/db/sqlite"
)

// StyleLearner 回复风格学习器
type StyleLearner struct {
	db *sqlitepkg.DB
}

// NewStyleLearner 创建风格学习器
func NewStyleLearner(db *sqlitepkg.DB) *StyleLearner {
	return &StyleLearner{db: db}
}

// StyleAnalysis 风格分析结果
type StyleAnalysis struct {
	CommonPhrases    []string // 常用短语
	CommonWords      []string // 常用词汇
	AverageLength    int      // 平均长度
	CommonPatterns   []string // 常见模式
	SampleComments   []string // 样本评论
	SamplePosts      []string // 样本帖子
}

// LearnGroupStyle 学习小组的回复风格
func (sl *StyleLearner) LearnGroupStyle(groupID string, maxPosts, maxComments int) (*StyleAnalysis, error) {
	// 获取历史帖子
	posts, err := sl.db.GetPostsByGroupID(groupID, maxPosts)
	if err != nil {
		return nil, fmt.Errorf("获取帖子失败: %v", err)
	}

	// 获取历史评论
	comments, err := sl.db.GetCommentsByGroupID(groupID, maxComments)
	if err != nil {
		return nil, fmt.Errorf("获取评论失败: %v", err)
	}

	analysis := &StyleAnalysis{
		CommonPhrases:  []string{},
		CommonWords:    []string{},
		CommonPatterns: []string{},
		SampleComments: []string{},
		SamplePosts:    []string{},
	}

	// 收集样本
	totalLength := 0
	commentCount := 0
	wordFreq := make(map[string]int)
	phraseFreq := make(map[string]int)

	// 分析评论
	for _, comment := range comments {
		if comment.Content == "" {
			continue
		}
		
		content := strings.TrimSpace(comment.Content)
		if len(content) < 5 { // 跳过太短的评论
			continue
		}

		analysis.SampleComments = append(analysis.SampleComments, content)
		totalLength += len(content)
		commentCount++

		// 提取常用词汇（简单分词）
		words := strings.Fields(content)
		for _, word := range words {
			if len(word) > 1 {
				wordFreq[word]++
			}
		}

		// 提取常用短语（2-3个词）
		for i := 0; i < len(words)-1; i++ {
			if i+1 < len(words) {
				phrase := words[i] + " " + words[i+1]
				phraseFreq[phrase]++
			}
		}
	}

	// 分析帖子
	for _, post := range posts {
		if post.Content != "" {
			analysis.SamplePosts = append(analysis.SamplePosts, post.Title+"\n"+post.Content)
		}
	}

	// 计算平均长度
	if commentCount > 0 {
		analysis.AverageLength = totalLength / commentCount
	}

	// 提取最常见的词汇（前20个）
	analysis.CommonWords = getTopN(wordFreq, 20)

	// 提取最常见的短语（前15个）
	analysis.CommonPhrases = getTopN(phraseFreq, 15)

	// 识别常见模式
	analysis.CommonPatterns = sl.identifyPatterns(analysis.SampleComments)

	return analysis, nil
}

// identifyPatterns 识别常见回复模式
func (sl *StyleLearner) identifyPatterns(comments []string) []string {
	patterns := []string{}

	// 检查是否经常使用问号
	questionCount := 0
	for _, comment := range comments {
		if strings.Contains(comment, "？") || strings.Contains(comment, "?") {
			questionCount++
		}
	}
	if questionCount > len(comments)/3 {
		patterns = append(patterns, "经常使用疑问句")
	}

	// 检查是否经常使用感叹号
	exclamationCount := 0
	for _, comment := range comments {
		if strings.Contains(comment, "！") || strings.Contains(comment, "!") {
			exclamationCount++
		}
	}
	if exclamationCount > len(comments)/3 {
		patterns = append(patterns, "经常使用感叹句")
	}

	// 检查是否经常使用表情符号
	emojiCount := 0
	emojis := []string{"😊", "😂", "👍", "❤️", "🙏", "😭", "😅", "😁"}
	for _, comment := range comments {
		for _, emoji := range emojis {
			if strings.Contains(comment, emoji) {
				emojiCount++
				break
			}
		}
	}
	if emojiCount > len(comments)/4 {
		patterns = append(patterns, "经常使用表情符号")
	}

	// 检查是否经常使用"谢谢"、"感谢"等礼貌用语
	politeCount := 0
	politeWords := []string{"谢谢", "感谢", "请问", "麻烦", "不好意思"}
	for _, comment := range comments {
		for _, word := range politeWords {
			if strings.Contains(comment, word) {
				politeCount++
				break
			}
		}
	}
	if politeCount > len(comments)/3 {
		patterns = append(patterns, "经常使用礼貌用语")
	}

	return patterns
}

// getTopN 获取频率最高的N个项
func getTopN(freqMap map[string]int, n int) []string {
	type pair struct {
		key   string
		value int
	}
	pairs := make([]pair, 0, len(freqMap))
	for k, v := range freqMap {
		pairs = append(pairs, pair{k, v})
	}

	// 简单排序（冒泡排序，对于小数据集足够）
	for i := 0; i < len(pairs)-1 && i < n; i++ {
		for j := i + 1; j < len(pairs); j++ {
			if pairs[j].value > pairs[i].value {
				pairs[i], pairs[j] = pairs[j], pairs[i]
			}
		}
	}

	result := make([]string, 0, n)
	for i := 0; i < len(pairs) && i < n; i++ {
		result = append(result, pairs[i].key)
	}
	return result
}

// BuildSystemPrompt 构建系统提示词
func (sl *StyleLearner) BuildSystemPrompt(groupName string, analysis *StyleAnalysis) string {
	prompt := fmt.Sprintf(`你是一个豆瓣小组"%s"的自动回复机器人。请根据以下风格特点生成回复：

风格特点：
1. 平均回复长度：约%d个字符
2. 常用词汇：%s
3. 常用短语：%s
4. 回复模式：%s

回复要求：
1. 回复要自然、友好，符合该小组的交流风格
2. 使用该小组常用的词汇和表达方式
3. 回复长度要适中，不要过长或过短
4. 根据帖子内容给出有意义的回复，不要只是简单的"顶"、"支持"等
5. 如果帖子是提问，要尽量给出有用的建议或回答
6. 保持礼貌和友善的语气

请根据以上要求生成回复。`,
		groupName,
		analysis.AverageLength,
		strings.Join(analysis.CommonWords[:min(10, len(analysis.CommonWords))], "、"),
		strings.Join(analysis.CommonPhrases[:min(8, len(analysis.CommonPhrases))], "、"),
		strings.Join(analysis.CommonPatterns, "、"))

	return prompt
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
