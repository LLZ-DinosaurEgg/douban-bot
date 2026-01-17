// 全局状态
let currentGroupId = '';
let currentPage = 1;
let pageSize = 20;
let filterMatched = false;

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    loadStats();
    loadGroups();
    setupEventListeners();
    setupConfigTabListeners();
    setupBotTabListeners();
});

// 设置事件监听
function setupEventListeners() {
    document.getElementById('filter-matched').addEventListener('change', (e) => {
        filterMatched = e.target.checked;
        loadPosts();
    });

    document.getElementById('close-modal').addEventListener('click', () => {
        document.getElementById('post-modal').classList.remove('show');
    });

    document.getElementById('post-modal').addEventListener('click', (e) => {
        if (e.target.id === 'post-modal') {
            document.getElementById('post-modal').classList.remove('show');
        }
    });

    document.getElementById('group-search').addEventListener('input', (e) => {
        filterGroups(e.target.value);
    });
}

// 加载统计信息
async function loadStats() {
    try {
        const response = await fetch('/api/stats');
        const result = await response.json();
        if (result.success) {
            document.getElementById('groups-count').textContent = result.data.groups || 0;
            document.getElementById('posts-count').textContent = result.data.posts || 0;
            document.getElementById('comments-count').textContent = result.data.comments || 0;
        }
    } catch (error) {
        console.error('加载统计信息失败:', error);
    }
}

// 加载小组列表
async function loadGroups() {
    const groupList = document.getElementById('group-list');
    groupList.innerHTML = '<li class="loading">加载中...</li>';

    try {
        const response = await fetch('/api/groups');
        const result = await response.json();
        if (result.success && result.data) {
            if (result.data.length === 0) {
                groupList.innerHTML = '<li class="loading">暂无小组数据</li>';
                return;
            }

            groupList.innerHTML = '';
            result.data.forEach(group => {
                const li = document.createElement('li');
                li.innerHTML = `
                    <div class="group-name">${escapeHtml(group.name)}</div>
                    <div class="group-meta">成员: ${group.memberCount || 0}</div>
                `;
                li.addEventListener('click', () => {
                    selectGroup(group.groupId, group.name);
                });
                groupList.appendChild(li);
            });

            // 默认选择第一个小组
            if (result.data.length > 0) {
                selectGroup(result.data[0].groupId, result.data[0].name);
            }
        }
    } catch (error) {
        console.error('加载小组失败:', error);
        groupList.innerHTML = '<li class="loading">加载失败</li>';
    }
}

// 过滤小组
function filterGroups(keyword) {
    const items = document.querySelectorAll('#group-list li');
    items.forEach(item => {
        const text = item.textContent.toLowerCase();
        if (text.includes(keyword.toLowerCase()) || item.classList.contains('loading')) {
            item.style.display = '';
        } else {
            item.style.display = 'none';
        }
    });
}

// 选择小组
function selectGroup(groupId, groupName) {
    currentGroupId = groupId;
    currentPage = 1;

    // 更新UI
    document.querySelectorAll('#group-list li').forEach(li => {
        li.classList.remove('active');
        if (li.textContent.includes(groupName)) {
            li.classList.add('active');
        }
    });

    loadPosts();
}

// 加载帖子列表
async function loadPosts() {
    const container = document.getElementById('posts-container');
    container.innerHTML = '<div class="loading">加载中...</div>';

    try {
        const url = `/api/posts?group_id=${currentGroupId}&page=${currentPage}&page_size=${pageSize}`;
        const response = await fetch(url);
        const result = await response.json();

        if (result.success && result.data) {
            let posts = result.data;
            
            // 过滤匹配的帖子
            if (filterMatched) {
                posts = posts.filter(post => post.isMatched);
            }

            if (posts.length === 0) {
                container.innerHTML = '<div class="loading">暂无帖子数据</div>';
                renderPagination(result.pagination);
                return;
            }

            container.innerHTML = '';
            posts.forEach(post => {
                const card = createPostCard(post);
                container.appendChild(card);
            });

            renderPagination(result.pagination);
            updatePaginationInfo(result.pagination);
        }
    } catch (error) {
        console.error('加载帖子失败:', error);
        container.innerHTML = '<div class="loading">加载失败</div>';
    }
}

// 创建帖子卡片
function createPostCard(post) {
    const card = document.createElement('div');
    card.className = `post-card ${post.isMatched ? 'matched' : ''}`;
    
    const authorName = post.authorInfo?.name || '未知';
    const created = formatDate(post.created);
    const content = post.content || '';
    const keywords = post.keywordList || [];

    card.innerHTML = `
        <div class="post-header">
            <div class="post-title">${escapeHtml(post.title)}</div>
        </div>
        <div class="post-meta">
            <span>作者: ${escapeHtml(authorName)}</span>
            <span>时间: ${created}</span>
            ${post.isMatched ? '<span style="color: #28a745;">✓ 已匹配</span>' : ''}
            ${post.botReplied ? '<span style="color: #007bff; margin-left: 10px;">🤖 已自动回复</span>' : ''}
        </div>
        ${content ? `<div class="post-content">${escapeHtml(content.substring(0, 200))}${content.length > 200 ? '...' : ''}</div>` : ''}
        ${keywords.length > 0 ? `
            <div class="post-keywords">
                ${keywords.map(k => `<span class="keyword-tag">${escapeHtml(k)}</span>`).join('')}
            </div>
        ` : ''}
    `;

    card.addEventListener('click', () => {
        showPostDetail(post.postId);
    });

    return card;
}

// 显示帖子详情
async function showPostDetail(postId) {
    const modal = document.getElementById('post-modal');
    const detail = document.getElementById('post-detail');
    detail.innerHTML = '<div class="loading">加载中...</div>';
    modal.classList.add('show');

    try {
        // 加载帖子详情
        const postResponse = await fetch(`/api/post/${postId}`);
        const postResult = await postResponse.json();

        // 加载评论
        const commentsResponse = await fetch(`/api/comments/${postId}`);
        const commentsResult = await commentsResponse.json();

        if (postResult.success) {
            const post = postResult.data;
            const comments = commentsResult.success ? commentsResult.data : [];

            const authorName = post.authorInfo?.name || '未知';
            const created = formatDate(post.created);
            const updated = formatDate(post.updated);
            const keywords = post.keywordList || [];

            detail.innerHTML = `
                <div class="post-detail-title">${escapeHtml(post.title)}</div>
                <div class="post-detail-meta">
                    <span>作者: ${escapeHtml(authorName)}</span>
                    <span>创建时间: ${created}</span>
                    <span>更新时间: ${updated}</span>
                    ${post.isMatched ? '<span style="color: #28a745;">✓ 已匹配</span>' : ''}
                    ${post.botReplied ? '<span style="color: #007bff; margin-left: 10px;">🤖 已自动回复</span>' : ''}
                </div>
                ${keywords.length > 0 ? `
                    <div class="post-keywords" style="margin-bottom: 20px;">
                        ${keywords.map(k => `<span class="keyword-tag">${escapeHtml(k)}</span>`).join('')}
                    </div>
                ` : ''}
                <div class="post-detail-content">${escapeHtml(post.content || '无内容')}</div>
                ${post.botReplied && post.botReplyContent ? `
                    <div style="margin-top: 20px; padding: 15px; background-color: #f8f9fa; border-left: 4px solid #007bff; border-radius: 4px;">
                        <div style="font-weight: bold; color: #007bff; margin-bottom: 10px;">
                            🤖 自动回复内容
                            ${post.botReplyAt ? `<span style="font-size: 12px; color: #6c757d; font-weight: normal; margin-left: 10px;">(${formatDate(post.botReplyAt)})</span>` : ''}
                        </div>
                        <div style="color: #333; white-space: pre-wrap; line-height: 1.6;">${escapeHtml(post.botReplyContent)}</div>
                    </div>
                ` : ''}
                ${post.alt ? `<div style="margin-top: 15px;"><a href="${post.alt}" target="_blank" style="color: #667eea;">查看原帖 →</a></div>` : ''}
                ${comments.length > 0 ? `
                    <div class="comments-section">
                        <h3>评论 (${comments.length})</h3>
                        ${comments.map(comment => `
                            <div class="comment-item">
                                <div class="comment-author">${escapeHtml(comment.authorInfo?.name || '匿名')}</div>
                                <div class="comment-content">${escapeHtml(comment.content)}</div>
                                <div class="comment-meta">
                                    时间: ${formatDate(comment.created)} | 
                                    点赞: ${comment.likeCount || 0}
                                </div>
                            </div>
                        `).join('')}
                    </div>
                ` : '<div class="comments-section"><h3>暂无评论</h3></div>'}
            `;
        }
    } catch (error) {
        console.error('加载帖子详情失败:', error);
        detail.innerHTML = '<div class="loading">加载失败</div>';
    }
}

// 渲染分页
function renderPagination(pagination) {
    const container = document.getElementById('pagination');
    if (!pagination || pagination.pages <= 1) {
        container.innerHTML = '';
        return;
    }

    const { page, pages } = pagination;
    let html = '';

    // 上一页
    html += `<button ${page <= 1 ? 'disabled' : ''} onclick="goToPage(${page - 1})">上一页</button>`;

    // 页码
    const startPage = Math.max(1, page - 2);
    const endPage = Math.min(pages, page + 2);

    if (startPage > 1) {
        html += `<button onclick="goToPage(1)">1</button>`;
        if (startPage > 2) {
            html += `<span>...</span>`;
        }
    }

    for (let i = startPage; i <= endPage; i++) {
        html += `<button class="${i === page ? 'active' : ''}" onclick="goToPage(${i})">${i}</button>`;
    }

    if (endPage < pages) {
        if (endPage < pages - 1) {
            html += `<span>...</span>`;
        }
        html += `<button onclick="goToPage(${pages})">${pages}</button>`;
    }

    // 下一页
    html += `<button ${page >= pages ? 'disabled' : ''} onclick="goToPage(${page + 1})">下一页</button>`;

    container.innerHTML = html;
}

// 更新分页信息
function updatePaginationInfo(pagination) {
    const info = document.getElementById('pagination-info');
    if (pagination) {
        info.textContent = `第 ${pagination.page} 页，共 ${pagination.pages} 页，总计 ${pagination.total} 条`;
    }
}

// 跳转到指定页
function goToPage(page) {
    currentPage = page;
    loadPosts();
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// 格式化日期
function formatDate(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleString('zh-CN');
}

// HTML转义
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ============== 配置管理功能 ==============

// 设置配置标签页监听器
function setupConfigTabListeners() {
    // 标签页切换
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const tab = btn.dataset.tab;
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
            btn.classList.add('active');
            document.getElementById(`${tab}-tab`).classList.add('active');
            
            if (tab === 'config') {
                loadConfigs();
            } else if (tab === 'bot') {
                loadBotConfig();
            }
        });
    });

    // 添加配置按钮
    document.getElementById('add-config-btn').addEventListener('click', () => {
        openConfigModal();
    });

    // 配置表单提交
    document.getElementById('config-form').addEventListener('submit', (e) => {
        e.preventDefault();
        saveConfig();
    });

    // 关闭配置模态框
    document.getElementById('close-config-modal').addEventListener('click', () => {
        closeConfigModal();
    });

    document.getElementById('cancel-config-btn').addEventListener('click', () => {
        closeConfigModal();
    });

    document.getElementById('config-modal').addEventListener('click', (e) => {
        if (e.target.id === 'config-modal') {
            closeConfigModal();
        }
    });
}

// 加载配置列表
async function loadConfigs() {
    const container = document.getElementById('config-list');
    container.innerHTML = '<div class="loading">加载中...</div>';

    try {
        const response = await fetch('/api/config/crawler');
        const result = await response.json();

        if (result.success && result.data) {
            if (result.data.length === 0) {
                container.innerHTML = '<div class="empty-state">暂无配置，点击"添加配置"按钮创建</div>';
                return;
            }

            container.innerHTML = '';
            result.data.forEach(config => {
                const card = createConfigCard(config);
                container.appendChild(card);
            });
        } else {
            const errorMsg = result.error || '未知错误';
            container.innerHTML = '<div class="error">加载配置失败: ' + errorMsg + '</div>';
        }
    } catch (error) {
        console.error('加载配置失败:', error);
        container.innerHTML = '<div class="error">加载失败: ' + error.message + '</div>';
    }
}

// 创建配置卡片
function createConfigCard(config) {
    const card = document.createElement('div');
    card.className = 'config-card';
    
    const keywords = config.keywords && config.keywords.length > 0 
        ? config.keywords.join(', ') 
        : '无';
    const exclude = config.excludeKeywords && config.excludeKeywords.length > 0 
        ? config.excludeKeywords.join(', ') 
        : '无';

    card.innerHTML = `
        <div class="config-card-header">
            <h3>${escapeHtml(config.name)}</h3>
            <span class="config-status ${config.enabled ? 'enabled' : 'disabled'}">
                ${config.enabled ? '✓ 已启用' : '✗ 已禁用'}
            </span>
        </div>
        <div class="config-card-body">
            <div class="config-item">
                <label>小组链接:</label>
                <span>${escapeHtml(config.groupUrl)}</span>
            </div>
            <div class="config-item">
                <label>小组ID:</label>
                <span>${escapeHtml(config.groupId || '')}</span>
            </div>
            <div class="config-item">
                <label>关键词:</label>
                <span>${escapeHtml(keywords)}</span>
            </div>
            <div class="config-item">
                <label>排除关键词:</label>
                <span>${escapeHtml(exclude)}</span>
            </div>
            <div class="config-item">
                <label>爬取页数:</label>
                <span>${config.pages || 10}</span>
            </div>
            <div class="config-item">
                <label>睡眠时长:</label>
                <span>${config.sleepSeconds || 900} 秒</span>
            </div>
            <div class="config-item">
                <label>爬取评论:</label>
                <span style="color: ${config.crawlComments !== false ? '#28a745' : '#dc3545'}">
                    ${config.crawlComments !== false ? '✅ 是' : '❌ 否'}
                </span>
            </div>
            <div class="config-item">
                <label>创建时间:</label>
                <span>${formatDate(config.createdAt)}</span>
            </div>
        </div>
        <div class="config-card-actions">
            <button class="btn btn-primary btn-sm" onclick="runCrawler(${config.id})">立即运行</button>
            <button class="btn btn-secondary btn-sm" onclick="editConfig(${config.id})">编辑</button>
            <button class="btn btn-danger btn-sm" onclick="deleteConfig(${config.id})">删除</button>
        </div>
    `;

    return card;
}

// 打开配置模态框
function openConfigModal(config = null) {
    const modal = document.getElementById('config-modal');
    const form = document.getElementById('config-form');
    const title = document.getElementById('config-modal-title');
    
    if (config) {
        title.textContent = '编辑爬虫配置';
        document.getElementById('config-id').value = config.id;
        document.getElementById('config-name').value = config.name || '';
        document.getElementById('group-url').value = config.groupUrl || '';
        // Cookie 字段：如果已有配置，显示占位符，留空则不修改
        const cookieInput = document.getElementById('config-cookie');
        if (config.cookie && config.cookie.length > 0) {
            cookieInput.placeholder = '****（已有配置，留空则不修改）';
            cookieInput.value = '';
        } else {
            cookieInput.placeholder = '留空则使用默认 Cookie';
            cookieInput.value = '';
        }
        document.getElementById('keywords').value = config.keywords ? config.keywords.join(',') : '';
        document.getElementById('exclude-keywords').value = config.excludeKeywords ? config.excludeKeywords.join(',') : '';
        document.getElementById('pages').value = config.pages || 10;
        document.getElementById('sleep-seconds').value = config.sleepSeconds || 900;
        document.getElementById('config-enabled').checked = config.enabled !== false;
        document.getElementById('config-crawl-comments').checked = config.crawlComments !== false;
    } else {
        title.textContent = '添加爬虫配置';
        form.reset();
        document.getElementById('config-id').value = '';
        document.getElementById('pages').value = 10;
        document.getElementById('sleep-seconds').value = 900;
        document.getElementById('config-enabled').checked = true;
        document.getElementById('config-crawl-comments').checked = true;
    }
    
    modal.classList.add('show');
}

// 关闭配置模态框
function closeConfigModal() {
    document.getElementById('config-modal').classList.remove('show');
}

// 保存配置
async function saveConfig() {
    const form = document.getElementById('config-form');
    const configId = document.getElementById('config-id').value;
    
    const cookieInput = document.getElementById('config-cookie');
    const cookie = cookieInput.value.trim();
    
    const config = {
        name: document.getElementById('config-name').value,
        groupUrl: document.getElementById('group-url').value,
        keywords: document.getElementById('keywords').value,
        excludeKeywords: document.getElementById('exclude-keywords').value,
        pages: parseInt(document.getElementById('pages').value) || 10,
        sleepSeconds: parseInt(document.getElementById('sleep-seconds').value) || 900,
        enabled: document.getElementById('config-enabled').checked,
        crawlComments: document.getElementById('config-crawl-comments').checked
    };
    
    // 只有当用户输入了新的 Cookie 时才添加到请求中
    // 如果为空，则不发送 cookie 字段，后端会保留原有的 Cookie
    if (cookie && cookie.length > 0) {
        config.cookie = cookie;
    }

    try {
        const url = configId ? `/api/config/crawler/${configId}` : '/api/config/crawler';
        const method = configId ? 'PUT' : 'POST';
        
        console.log('发送配置请求:', { url, method, config });
        
        const response = await fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(config)
        });

        const result = await response.json();
        console.log('服务器响应:', result);

        if (result.success) {
            closeConfigModal();
            loadConfigs();
            alert('配置保存成功！');
        } else {
            const errorMsg = result.error || '未知错误';
            alert('保存失败: ' + errorMsg);
        }
    } catch (error) {
        console.error('保存配置失败:', error);
        alert('保存失败: ' + error.message);
    }
}

// 编辑配置
async function editConfig(id) {
    try {
        const response = await fetch(`/api/config/crawler/${id}`);
        const result = await response.json();

        if (result.success) {
            openConfigModal(result.data);
        } else {
            const errorMsg = result.error || '未知错误';
            alert('加载配置失败: ' + errorMsg);
        }
    } catch (error) {
        console.error('加载配置失败:', error);
        alert('加载失败: ' + error.message);
    }
}

// 删除配置
async function deleteConfig(id) {
    if (!confirm('确定要删除这个配置吗？')) {
        return;
    }

    try {
        const response = await fetch(`/api/config/crawler/${id}`, {
            method: 'DELETE'
        });

        const result = await response.json();

        if (result.success) {
            loadConfigs();
            alert('配置已删除');
        } else {
            const errorMsg = result.error || '未知错误';
            alert('删除失败: ' + errorMsg);
        }
    } catch (error) {
        console.error('删除配置失败:', error);
        alert('删除失败: ' + error.message);
    }
}

// 运行爬虫
async function runCrawler(id) {
    if (!confirm('确定要立即运行这个爬虫配置吗？')) {
        return;
    }

    try {
        const response = await fetch(`/api/config/crawler/${id}/run`, {
            method: 'POST'
        });

        const result = await response.json();

        if (result.success) {
            alert('爬虫任务已启动！请稍后查看数据。');
            // 3秒后刷新统计信息
            setTimeout(() => {
                loadStats();
            }, 3000);
        } else {
            const errorMsg = result.error || '未知错误';
            alert('启动失败: ' + errorMsg);
        }
    } catch (error) {
        console.error('启动爬虫失败:', error);
        alert('启动失败: ' + error.message);
    }
}

// ============== 机器人管理功能 ==============

// 设置机器人标签页监听器
function setupBotTabListeners() {
    // 测试生成回复按钮
    document.getElementById('test-generate-btn')?.addEventListener('click', testGenerateReply);
    
    // 复制回复按钮
    document.getElementById('copy-reply-btn')?.addEventListener('click', copyTestReply);
    
    // 编辑大模型配置按钮
    document.getElementById('edit-llm-config-btn')?.addEventListener('click', openLlmConfigForm);
    
    // 取消编辑大模型配置按钮
    document.getElementById('cancel-llm-config-btn')?.addEventListener('click', closeLlmConfigForm);
    
    // 保存大模型配置表单
    document.getElementById('llm-config-edit-form')?.addEventListener('submit', (e) => {
        e.preventDefault();
        saveLlmConfig();
    });
    
    // 编辑机器人配置按钮
    document.getElementById('edit-bot-config-btn')?.addEventListener('click', openBotConfigForm);
    
    // 取消编辑机器人配置按钮
    document.getElementById('cancel-bot-config-btn')?.addEventListener('click', closeBotConfigForm);
    
    // 保存机器人配置表单
    document.getElementById('bot-config-edit-form')?.addEventListener('submit', (e) => {
        e.preventDefault();
        saveBotConfig();
    });
    
    // 切换机器人状态按钮
    document.getElementById('toggle-bot-btn')?.addEventListener('click', toggleBotStatus);
}

// 打开大模型配置表单
function openLlmConfigForm() {
    const formDiv = document.getElementById('llm-config-form');
    if (formDiv) {
        formDiv.style.display = 'block';
        // 加载配置到表单
        loadLlmConfigToForm();
    }
}

// 关闭大模型配置表单
function closeLlmConfigForm() {
    const formDiv = document.getElementById('llm-config-form');
    if (formDiv) {
        formDiv.style.display = 'none';
    }
}

// 打开机器人配置表单
function openBotConfigForm() {
    const displayDiv = document.getElementById('bot-config-display');
    const formDiv = document.getElementById('bot-config-form');
    if (displayDiv && formDiv) {
        displayDiv.style.display = 'none';
        formDiv.style.display = 'block';
        // 加载配置到表单
        loadBotConfigToForm();
    }
}

// 关闭机器人配置表单
function closeBotConfigForm() {
    const displayDiv = document.getElementById('bot-config-display');
    const formDiv = document.getElementById('bot-config-form');
    if (displayDiv && formDiv) {
        displayDiv.style.display = 'block';
        formDiv.style.display = 'none';
    }
}

// 加载机器人配置
async function loadBotConfig() {
    try {
        const response = await fetch('/api/bot/config');
        const result = await response.json();
        
        if (result.success && result.data) {
            const config = result.data;
            
            // 更新状态显示
            document.getElementById('bot-enabled-status').textContent = config.enabled ? '✅ 已启用' : '❌ 已禁用';
            document.getElementById('bot-enabled-status').style.color = config.enabled ? '#28a745' : '#dc3545';
            
            // 更新切换按钮
            const toggleBtn = document.getElementById('toggle-bot-btn');
            const toggleBtnText = document.getElementById('toggle-bot-btn-text');
            if (toggleBtn && toggleBtnText) {
                if (config.enabled) {
                    toggleBtn.classList.remove('btn-primary');
                    toggleBtn.classList.add('btn-danger');
                    toggleBtnText.textContent = '停止';
                } else {
                    toggleBtn.classList.remove('btn-danger');
                    toggleBtn.classList.add('btn-primary');
                    toggleBtnText.textContent = '启动';
                }
            }
            
            document.getElementById('bot-api-status').textContent = config.hasApiKey ? '✅ 已配置' : '❌ 未配置';
            document.getElementById('bot-api-status').style.color = config.hasApiKey ? '#28a745' : '#dc3545';
            
            document.getElementById('bot-model').textContent = config.model || '-';
            document.getElementById('bot-temperature').textContent = config.temperature != null ? config.temperature.toString() : '-';
            
            // 更新配置信息显示
            const replyKeywords = config.replyKeywords && config.replyKeywords.length > 0 
                ? config.replyKeywords.join(', ') 
                : '未配置（将回复所有匹配的帖子）';
            document.getElementById('bot-reply-keywords').textContent = replyKeywords;
            
            const delay = config.minReplyDelay && config.maxReplyDelay
                ? `${config.minReplyDelay}-${config.maxReplyDelay} 秒`
                : '-';
            document.getElementById('bot-reply-delay').textContent = delay;
            
            const speedMultiplier = config.replySpeedMultiplier != null ? config.replySpeedMultiplier : 1.0;
            let speedText = speedMultiplier.toFixed(1);
            if (speedMultiplier < 1.0) {
                speedText += ' (快速模式)';
            } else if (speedMultiplier > 1.0) {
                speedText += ' (慢速模式)';
            } else {
                speedText += ' (正常速度)';
            }
            document.getElementById('bot-reply-speed-multiplier-display').textContent = speedText;
            
            const checkInterval = config.replyCheckInterval != null ? config.replyCheckInterval : 300;
            const intervalMinutes = Math.floor(checkInterval / 60);
            const intervalSeconds = checkInterval % 60;
            let intervalText = checkInterval + ' 秒';
            if (intervalMinutes > 0) {
                intervalText = intervalMinutes + ' 分';
                if (intervalSeconds > 0) {
                    intervalText += intervalSeconds + ' 秒';
                }
            }
            document.getElementById('bot-reply-check-interval-display').textContent = intervalText;
            
            document.getElementById('bot-history-posts').textContent = config.maxHistoryPosts || '-';
            document.getElementById('bot-history-comments').textContent = config.maxHistoryComments || '-';
            
            // 显示学习风格配置
            const enableStyleLearning = config.enableStyleLearning !== undefined ? config.enableStyleLearning : true;
            document.getElementById('bot-enable-style-learning').textContent = enableStyleLearning ? '✅ 已开启' : '❌ 已关闭';
            document.getElementById('bot-enable-style-learning').style.color = enableStyleLearning ? '#28a745' : '#dc3545';
            
            // 显示自定义 Prompt
            const customPrompt = config.customPrompt || '';
            if (customPrompt && customPrompt.trim().length > 0) {
                document.getElementById('bot-custom-prompt-display').textContent = customPrompt;
            } else {
                document.getElementById('bot-custom-prompt-display').textContent = '未配置（将使用默认 prompt 或学习风格）';
            }
            
            // 显示 Cookie 配置状态
            const hasCookie = config.hasCookie || false;
            const cookieStatus = document.getElementById('bot-cookie-status');
            if (cookieStatus) {
                cookieStatus.textContent = hasCookie ? '✅ 已配置' : '❌ 未配置（将使用默认 Cookie）';
                cookieStatus.style.color = hasCookie ? '#28a745' : '#dc3545';
            }
        }
    } catch (error) {
        console.error('加载机器人配置失败:', error);
    }
}

// 加载大模型配置到表单
async function loadLlmConfigToForm() {
    try {
        const response = await fetch('/api/bot/config');
        const result = await response.json();
        
        if (result.success && result.data) {
            const config = result.data;
            
            // 填充大模型配置字段
            document.getElementById('llm-api-type').value = config.apiType || 'openai';
            document.getElementById('llm-api-base').value = config.apiBase || '';
            // API Key 不显示，如果已有配置则显示占位符
            if (config.hasApiKey) {
                document.getElementById('llm-api-key').placeholder = '****（已有配置，留空则不修改）';
            } else {
                document.getElementById('llm-api-key').placeholder = 'sk-...';
            }
            document.getElementById('llm-api-key').value = '';
            document.getElementById('llm-model-input').value = config.model || '';
            document.getElementById('llm-temperature-input').value = config.temperature || 0.7;
            document.getElementById('llm-max-tokens').value = config.maxTokens || 500;
        }
    } catch (error) {
        console.error('加载大模型配置到表单失败:', error);
    }
}

// 加载机器人配置到表单
async function loadBotConfigToForm() {
    try {
        const response = await fetch('/api/bot/config');
        const result = await response.json();
        
        if (result.success && result.data) {
            const config = result.data;
            
            // 填充机器人配置字段
            document.getElementById('bot-enabled').checked = config.enabled || false;
            document.getElementById('bot-reply-keywords-input').value = config.replyKeywords ? config.replyKeywords.join(', ') : '';
            document.getElementById('bot-min-reply-delay').value = config.minReplyDelay || 30;
            document.getElementById('bot-max-reply-delay').value = config.maxReplyDelay || 300;
            document.getElementById('bot-reply-speed-multiplier').value = config.replySpeedMultiplier != null ? config.replySpeedMultiplier : 1.0;
            document.getElementById('bot-reply-check-interval').value = config.replyCheckInterval != null ? config.replyCheckInterval : 300;
            document.getElementById('bot-history-posts-input').value = config.maxHistoryPosts || 50;
            document.getElementById('bot-history-comments-input').value = config.maxHistoryComments || 200;
            
            // 填充学习风格和自定义 Prompt
            const enableStyleLearning = config.enableStyleLearning !== undefined ? config.enableStyleLearning : true;
            document.getElementById('bot-enable-style-learning-input').checked = enableStyleLearning;
            document.getElementById('bot-custom-prompt-input').value = config.customPrompt || '';
            
            // 填充 Cookie 字段
            const cookieInput = document.getElementById('bot-cookie-input');
            if (config.hasCookie) {
                cookieInput.placeholder = '****（已有配置，留空则不修改）';
                cookieInput.value = '';
            } else {
                cookieInput.placeholder = '留空则使用默认 Cookie';
                cookieInput.value = '';
            }
        }
    } catch (error) {
        console.error('加载机器人配置到表单失败:', error);
    }
}

// 保存大模型配置
async function saveLlmConfig() {
    const form = document.getElementById('llm-config-edit-form');
    if (!form) return;
    
    const apiKeyInput = document.getElementById('llm-api-key');
    const apiKey = apiKeyInput.value.trim();
    
    // 构建配置对象
    const config = {
        apiType: document.getElementById('llm-api-type').value,
        apiBase: document.getElementById('llm-api-base').value.trim(),
        model: document.getElementById('llm-model-input').value.trim(),
        temperature: parseFloat(document.getElementById('llm-temperature-input').value) || 0.7,
        maxTokens: parseInt(document.getElementById('llm-max-tokens').value) || 500
    };
    
    // 只有当用户输入了新的 API Key 时才添加到请求中
    // 如果为空，则不发送 apiKey 字段，后端会保留原有的 API Key
    if (apiKey && apiKey.length > 0) {
        config.apiKey = apiKey;
    }
    
    try {
        const response = await fetch('/api/bot/config', {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(config)
        });
        
        const result = await response.json();
        
        if (result.success) {
            alert('大模型配置保存成功！');
            // 刷新配置显示
            await loadBotConfig();
            // 关闭表单
            closeLlmConfigForm();
        } else {
            const errorMsg = result.error || '保存失败';
            alert('保存配置失败: ' + errorMsg);
        }
    } catch (error) {
        console.error('保存配置失败:', error);
        alert('保存配置失败: ' + error.message);
    }
}

// 保存机器人配置
async function saveBotConfig() {
    const form = document.getElementById('bot-config-edit-form');
    if (!form) return;
    
    const cookieInput = document.getElementById('bot-cookie-input');
    const cookie = cookieInput.value.trim();
    
    const config = {
        enabled: document.getElementById('bot-enabled').checked,
        replyKeywords: document.getElementById('bot-reply-keywords-input').value.trim(),
        minReplyDelay: parseInt(document.getElementById('bot-min-reply-delay').value) || 30,
        maxReplyDelay: parseInt(document.getElementById('bot-max-reply-delay').value) || 300,
        replySpeedMultiplier: parseFloat(document.getElementById('bot-reply-speed-multiplier').value) || 1.0,
        replyCheckInterval: parseInt(document.getElementById('bot-reply-check-interval').value) || 300,
        maxHistoryPosts: parseInt(document.getElementById('bot-history-posts-input').value) || 50,
        maxHistoryComments: parseInt(document.getElementById('bot-history-comments-input').value) || 200,
        enableStyleLearning: document.getElementById('bot-enable-style-learning-input').checked,
        customPrompt: document.getElementById('bot-custom-prompt-input').value.trim()
    };
    
    // 只有当用户输入了新的 Cookie 时才添加到请求中
    // 如果为空，则不发送 cookie 字段，后端会保留原有的 Cookie
    if (cookie && cookie.length > 0) {
        config.cookie = cookie;
    }
    
    try {
        const response = await fetch('/api/bot/config', {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(config)
        });
        
        const result = await response.json();
        
        if (result.success) {
            alert('机器人配置保存成功！');
            // 刷新配置显示
            await loadBotConfig();
            // 切换回显示模式
            const displayDiv = document.getElementById('bot-config-display');
            const formDiv = document.getElementById('bot-config-form');
            if (displayDiv && formDiv) {
                displayDiv.style.display = 'block';
                formDiv.style.display = 'none';
            }
        } else {
            const errorMsg = result.error || '保存失败';
            alert('保存配置失败: ' + errorMsg);
        }
    } catch (error) {
        console.error('保存配置失败:', error);
        alert('保存配置失败: ' + error.message);
    }
}

// 测试生成回复
async function testGenerateReply() {
    const title = document.getElementById('test-post-title').value.trim();
    const content = document.getElementById('test-post-content').value.trim();
    const groupId = document.getElementById('test-group-id').value.trim();
    const generateBtn = document.getElementById('test-generate-btn');
    const resultDiv = document.getElementById('test-result');
    const replyContent = document.getElementById('test-reply-content');
    
    if (!title || !content) {
        alert('请填写帖子标题和内容');
        return;
    }
    
    generateBtn.disabled = true;
    generateBtn.textContent = '生成中...';
    resultDiv.style.display = 'none';
    
    const requestData = {
        title: title,
        content: content,
        groupId: groupId || null
    };
    
    try {
        console.log('[测试回复生成] 开始生成回复', { title, contentLength: content.length, groupId });
        
        const response = await fetch('/api/bot/test', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestData)
        });
        
        const result = await response.json();
        console.log('[测试回复生成] 服务器响应', { status: response.status, success: result.success, hasData: !!result.data });
        
        if (result.success && result.data) {
            const reply = result.data.reply || '未收到回复';
            replyContent.textContent = reply;
            resultDiv.style.display = 'block';
            resultDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            console.log('[测试回复生成] 生成成功', { replyLength: reply.length });
        } else {
            const errorMsg = result.error || '生成回复失败';
            console.error('[测试回复生成] 生成失败', {
                request: requestData,
                response: result,
                status: response.status
            });
            alert('生成回复失败: ' + errorMsg);
        }
    } catch (error) {
        console.error('[测试回复生成] 请求异常', {
            request: requestData,
            error: {
                message: error.message,
                stack: error.stack,
                name: error.name
            }
        });
        alert('生成回复失败: ' + error.message);
    } finally {
        generateBtn.disabled = false;
        generateBtn.textContent = '✨ 生成回复';
    }
}

// 复制测试回复
function copyTestReply() {
    const replyContent = document.getElementById('test-reply-content').textContent;
    if (!replyContent) {
        alert('没有可复制的内容');
        return;
    }
    
    navigator.clipboard.writeText(replyContent).then(() => {
        alert('回复已复制到剪贴板');
    }).catch(err => {
        console.error('复制失败:', err);
        alert('复制失败，请手动复制');
    });
}

// 切换机器人状态
async function toggleBotStatus() {
    try {
        // 先获取当前配置
        const response = await fetch('/api/bot/config');
        const result = await response.json();
        
        if (!result.success || !result.data) {
            alert('获取机器人配置失败');
            return;
        }
        
        const currentEnabled = result.data.enabled;
        const newEnabled = !currentEnabled;
        
        // 更新配置
        const updateResponse = await fetch('/api/bot/config', {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                enabled: newEnabled
            })
        });
        
        const updateResult = await updateResponse.json();
        
        if (updateResult.success) {
            // 刷新配置显示
            await loadBotConfig();
            alert(newEnabled ? '✅ 机器人已启动' : '⏸️ 机器人已停止');
        } else {
            const errorMsg = updateResult.error || '切换状态失败';
            alert('切换状态失败: ' + errorMsg);
        }
    } catch (error) {
        console.error('切换机器人状态失败:', error);
        alert('切换状态失败: ' + error.message);
    }
}
