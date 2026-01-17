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
