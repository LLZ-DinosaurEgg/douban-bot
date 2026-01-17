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
    // 初始化错误日志显示
    updateErrorLogDisplay();
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
            document.getElementById('groups-count').textContent = result.data.groups_count || 0;
            document.getElementById('posts-count').textContent = result.data.posts_count || 0;
            document.getElementById('comments-count').textContent = result.data.comments_count || 0;
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
                    <div class="group-meta">成员: ${group.member_count || 0}</div>
                `;
                li.addEventListener('click', () => {
                    selectGroup(group.id, group.name);
                });
                groupList.appendChild(li);
            });

            // 默认选择第一个小组
            if (result.data.length > 0) {
                selectGroup(result.data[0].id, result.data[0].name);
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
                posts = posts.filter(post => post.is_matched);
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
    card.className = `post-card ${post.is_matched ? 'matched' : ''}`;
    
    const authorName = post.author_info?.name || '未知';
    const created = formatDate(post.created);
    const content = post.content || '';
    const keywords = post.keyword_list || [];

    card.innerHTML = `
        <div class="post-header">
            <div class="post-title">${escapeHtml(post.title)}</div>
        </div>
        <div class="post-meta">
            <span>作者: ${escapeHtml(authorName)}</span>
            <span>时间: ${created}</span>
            ${post.is_matched ? '<span style="color: #28a745;">✓ 已匹配</span>' : ''}
        </div>
        ${content ? `<div class="post-content">${escapeHtml(content.substring(0, 200))}${content.length > 200 ? '...' : ''}</div>` : ''}
        ${keywords.length > 0 ? `
            <div class="post-keywords">
                ${keywords.map(k => `<span class="keyword-tag">${escapeHtml(k)}</span>`).join('')}
            </div>
        ` : ''}
    `;

    card.addEventListener('click', () => {
        showPostDetail(post.post_id);
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

            const authorName = post.author_info?.name || '未知';
            const created = formatDate(post.created);
            const updated = formatDate(post.updated);
            const keywords = post.keyword_list || [];

            detail.innerHTML = `
                <div class="post-detail-title">${escapeHtml(post.title)}</div>
                <div class="post-detail-meta">
                    <span>作者: ${escapeHtml(authorName)}</span>
                    <span>创建时间: ${created}</span>
                    <span>更新时间: ${updated}</span>
                    ${post.is_matched ? '<span style="color: #28a745;">✓ 已匹配</span>' : ''}
                </div>
                ${keywords.length > 0 ? `
                    <div class="post-keywords" style="margin-bottom: 20px;">
                        ${keywords.map(k => `<span class="keyword-tag">${escapeHtml(k)}</span>`).join('')}
                    </div>
                ` : ''}
                <div class="post-detail-content">${escapeHtml(post.content || '无内容')}</div>
                ${post.alt ? `<div style="margin-top: 15px;"><a href="${post.alt}" target="_blank" style="color: #667eea;">查看原帖 →</a></div>` : ''}
                ${comments.length > 0 ? `
                    <div class="comments-section">
                        <h3>评论 (${comments.length})</h3>
                        ${comments.map(comment => `
                            <div class="comment-item">
                                <div class="comment-author">${escapeHtml(comment.author_info?.name || '匿名')}</div>
                                <div class="comment-content">${escapeHtml(comment.content)}</div>
                                <div class="comment-meta">
                                    时间: ${formatDate(comment.created)} | 
                                    点赞: ${comment.like_count || 0}
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

    // 错误日志按钮事件
    document.getElementById('clear-error-log')?.addEventListener('click', clearErrorLog);
    document.getElementById('copy-error-log')?.addEventListener('click', copyErrorLog);
    document.getElementById('toggle-error-log')?.addEventListener('click', () => {
        const container = document.getElementById('error-log-container');
        container.classList.toggle('collapsed');
        const btn = document.getElementById('toggle-error-log');
        btn.textContent = container.classList.contains('collapsed') ? '展开' : '收起';
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
            addErrorLog(`加载配置列表失败: ${errorMsg}`, { response: result });
            container.innerHTML = '<div class="error">加载配置失败: ' + errorMsg + '</div>';
        }
    } catch (error) {
        console.error('加载配置失败:', error);
        addErrorLog(`加载配置列表异常: ${error.message}`, { error: error.message, stack: error.stack });
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
        document.getElementById('keywords').value = config.keywords ? config.keywords.join(',') : '';
        document.getElementById('exclude-keywords').value = config.excludeKeywords ? config.excludeKeywords.join(',') : '';
        document.getElementById('pages').value = config.pages || 10;
        document.getElementById('sleep-seconds').value = config.sleepSeconds || 900;
        document.getElementById('config-enabled').checked = config.enabled !== false;
    } else {
        title.textContent = '添加爬虫配置';
        form.reset();
        document.getElementById('config-id').value = '';
        document.getElementById('pages').value = 10;
        document.getElementById('sleep-seconds').value = 900;
        document.getElementById('config-enabled').checked = true;
    }
    
    modal.classList.add('show');
}

// 关闭配置模态框
function closeConfigModal() {
    document.getElementById('config-modal').classList.remove('show');
}

// 错误日志管理
let errorLogs = [];

function addErrorLog(message, details = null) {
    const timestamp = new Date().toLocaleString('zh-CN');
    const errorEntry = {
        timestamp,
        message,
        details: details ? JSON.stringify(details, null, 2) : null,
        fullError: details
    };
    errorLogs.push(errorEntry);
    updateErrorLogDisplay();
    
    // 自动展开错误日志区域
    const container = document.getElementById('error-log-container');
    container.classList.add('expanded');
    container.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function updateErrorLogDisplay() {
    const content = document.getElementById('error-log-content');
    if (errorLogs.length === 0) {
        content.innerHTML = '<div class="error-log-empty">暂无错误日志</div>';
        return;
    }
    
    content.innerHTML = errorLogs.slice().reverse().map((log, index) => {
        return `
            <div class="error-log-entry">
                <div class="error-log-time">[${log.timestamp}]</div>
                <div class="error-log-message">${escapeHtml(log.message)}</div>
                ${log.details ? `
                    <details class="error-log-details">
                        <summary>查看详细信息</summary>
                        <pre class="error-log-details-content">${escapeHtml(log.details)}</pre>
                    </details>
                ` : ''}
            </div>
        `;
    }).join('');
}

function clearErrorLog() {
    errorLogs = [];
    updateErrorLogDisplay();
}

function copyErrorLog() {
    if (errorLogs.length === 0) {
        alert('没有错误日志可复制');
        return;
    }
    
    const logText = errorLogs.map(log => {
        let text = `[${log.timestamp}] ${log.message}`;
        if (log.details) {
            text += `\n详细信息:\n${log.details}`;
        }
        return text;
    }).join('\n\n');
    
    navigator.clipboard.writeText(logText).then(() => {
        alert('错误日志已复制到剪贴板');
    }).catch(err => {
        console.error('复制失败:', err);
        alert('复制失败，请手动复制');
    });
}

// 保存配置
async function saveConfig() {
    const form = document.getElementById('config-form');
    const configId = document.getElementById('config-id').value;
    
    const config = {
        name: document.getElementById('config-name').value,
        groupUrl: document.getElementById('group-url').value,
        keywords: document.getElementById('keywords').value,
        excludeKeywords: document.getElementById('exclude-keywords').value,
        pages: parseInt(document.getElementById('pages').value) || 10,
        sleepSeconds: parseInt(document.getElementById('sleep-seconds').value) || 900,
        enabled: document.getElementById('config-enabled').checked
    };

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
            addErrorLog(`保存配置失败: ${errorMsg}`, {
                request: config,
                response: result,
                status: response.status
            });
            alert('保存失败: ' + errorMsg);
        }
    } catch (error) {
        console.error('保存配置失败:', error);
        const errorDetails = {
            message: error.message,
            stack: error.stack,
            config: config,
            url: configId ? `/api/config/crawler/${configId}` : '/api/config/crawler'
        };
        addErrorLog(`保存配置异常: ${error.message}`, errorDetails);
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
            addErrorLog(`加载配置失败: ${errorMsg}`, { id, response: result });
            alert('加载配置失败: ' + errorMsg);
        }
    } catch (error) {
        console.error('加载配置失败:', error);
        addErrorLog(`加载配置异常: ${error.message}`, { id, error: error.message, stack: error.stack });
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
            addErrorLog(`删除配置失败: ${errorMsg}`, { id, response: result });
            alert('删除失败: ' + errorMsg);
        }
    } catch (error) {
        console.error('删除配置失败:', error);
        addErrorLog(`删除配置异常: ${error.message}`, { id, error: error.message, stack: error.stack });
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
            addErrorLog(`启动爬虫失败: ${errorMsg}`, { id, response: result });
            alert('启动失败: ' + errorMsg);
        }
    } catch (error) {
        console.error('启动爬虫失败:', error);
        addErrorLog(`启动爬虫异常: ${error.message}`, { id, error: error.message, stack: error.stack });
        alert('启动失败: ' + error.message);
    }
}
