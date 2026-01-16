package llm

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/LLZ-DinosaurEgg/douban-crawler/config"
)

// Client 大模型客户端
type Client struct {
	apiBase    string
	apiKey     string
	model      string
	temperature float64
	maxTokens  int
	httpClient *http.Client
}

// NewClient 创建大模型客户端
func NewClient() *Client {
	return &Client{
		apiBase:    config.LLM_API_BASE,
		apiKey:     config.LLM_API_KEY,
		model:      config.LLM_MODEL,
		temperature: config.LLM_TEMPERATURE,
		maxTokens:  config.LLM_MAX_TOKENS,
		httpClient: &http.Client{
			Timeout: 60 * time.Second,
		},
	}
}

// Message 消息结构
type Message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// ChatRequest OpenAI格式的请求
type ChatRequest struct {
	Model       string    `json:"model"`
	Messages    []Message `json:"messages"`
	Temperature float64   `json:"temperature,omitempty"`
	MaxTokens   int       `json:"max_tokens,omitempty"`
}

// ChatResponse OpenAI格式的响应
type ChatResponse struct {
	Choices []struct {
		Message Message `json:"message"`
	} `json:"choices"`
	Error struct {
		Message string `json:"message"`
	} `json:"error"`
}

// GenerateReply 生成回复
func (c *Client) GenerateReply(systemPrompt string, userPrompt string) (string, error) {
	if c.apiKey == "" {
		return "", fmt.Errorf("API密钥未配置")
	}

	messages := []Message{
		{Role: "system", Content: systemPrompt},
		{Role: "user", Content: userPrompt},
	}

	reqBody := ChatRequest{
		Model:       c.model,
		Messages:    messages,
		Temperature: c.temperature,
		MaxTokens:   c.maxTokens,
	}

	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		return "", fmt.Errorf("序列化请求失败: %v", err)
	}

	req, err := http.NewRequest("POST", c.apiBase+"/chat/completions", bytes.NewBuffer(jsonData))
	if err != nil {
		return "", fmt.Errorf("创建请求失败: %v", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+c.apiKey)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("请求失败: %v", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("读取响应失败: %v", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("API请求失败，状态码: %d, 响应: %s", resp.StatusCode, string(body))
	}

	var chatResp ChatResponse
	if err := json.Unmarshal(body, &chatResp); err != nil {
		return "", fmt.Errorf("解析响应失败: %v", err)
	}

	if chatResp.Error.Message != "" {
		return "", fmt.Errorf("API错误: %s", chatResp.Error.Message)
	}

	if len(chatResp.Choices) == 0 {
		return "", fmt.Errorf("未收到有效回复")
	}

	return chatResp.Choices[0].Message.Content, nil
}
