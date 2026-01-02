// 模拟文档数据
const mockDocuments = [
    {
        id: '1',
        title: '北滘镇第二幼儿园财务报表',
        filename: '附件1-3：北滘镇第二幼儿园（博.pdf',
        category: '报表',
        tags: ['幼儿园', '财务', '月报'],
        size: '2.5MB',
        uploadDate: '2024-01-15',
        lastModified: '2024-01-16 14:30'
    },
    {
        id: '2',
        title: '2024年度预算计划',
        filename: '2024年预算计划.docx',
        category: '报告',
        tags: ['预算', '计划', '年度'],
        size: '1.8MB',
        uploadDate: '2024-01-10',
        lastModified: '2024-01-12 09:45'
    },
    {
        id: '3',
        title: '办公设备采购合同',
        filename: '办公设备采购合同.pdf',
        category: '合同',
        tags: ['采购', '设备', '合同'],
        size: '3.2MB',
        uploadDate: '2024-01-08',
        lastModified: '2024-01-08 16:20'
    },
    {
        id: '4',
        title: '2023年第四季度财务分析',
        filename: '2023Q4财务分析报告.pptx',
        category: '报告',
        tags: ['财务', '分析', '季度'],
        size: '5.7MB',
        uploadDate: '2024-01-05',
        lastModified: '2024-01-06 11:15'
    },
    {
        id: '5',
        title: '员工工资表202401',
        filename: '工资表202401.xlsx',
        category: '报表',
        tags: ['工资', '员工', '月度'],
        size: '0.8MB',
        uploadDate: '2024-01-03',
        lastModified: '2024-01-04 17:30'
    }
];

// 页面加载时初始化文档列表
document.addEventListener('DOMContentLoaded', function() {
    // 检查是否在文档管理页面
    if (window.location.pathname.includes('/documents') && !window.location.pathname.includes('/documents/upload')) {
        // 尝试获取文档表格的tbody
        const tableBody = document.querySelector('.document-table tbody');
        
        if (tableBody) {
            // 清空现有的内容
            tableBody.innerHTML = '';
            
            // 如果有模拟数据，渲染文档列表
            if (mockDocuments && mockDocuments.length > 0) {
                mockDocuments.forEach(doc => {
                    const row = createDocumentRow(doc);
                    tableBody.appendChild(row);
                });
            } else {
                // 显示空状态
                tableBody.innerHTML = `
                    <tr>
                        <td colspan="8">
                            <div class="empty-state">
                                <div class="empty-icon">
                                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="64" height="64">
                                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                                        <polyline points="14 2 14 8 20 8" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                                        <line x1="16" y1="13" x2="8" y2="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                                        <line x1="16" y1="17" x2="8" y2="17" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                                        <polyline points="10 9 9 9 8 9" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                                    </svg>
                                </div>
                                <h3>暂无文档</h3>
                                <p>点击"上传新文档"按钮开始添加您的第一个文档</p>
                            </div>
                        </td>
                    </tr>
                `;
            }
            
            // 重新绑定事件监听器
            bindEventListeners();
        }
    }
});

// 创建文档行
function createDocumentRow(document) {
    const tr = document.createElement('tr');
    
    // 确定文档类别对应的样式类
    let categoryClass = 'document-other';
    let categoryBadgeClass = 'category-other';
    
    switch (document.category.toLowerCase()) {
        case '发票':
            categoryClass = 'document-invoice';
            categoryBadgeClass = 'category-invoice';
            break;
        case '合同':
            categoryClass = 'document-contract';
            categoryBadgeClass = 'category-contract';
            break;
        case '报告':
        case '报表':
            categoryClass = 'document-report';
            categoryBadgeClass = 'category-report';
            break;
        case '收据':
            categoryClass = 'document-receipt';
            categoryBadgeClass = 'category-receipt';
            break;
    }
    
    tr.innerHTML = `
        <td class="checkbox-column">
            <input type="checkbox" class="document-checkbox" data-id="${document.id}" value="${document.id}" />
        </td>
        <td>
            <div class="document-info">
                <div class="document-icon ${categoryClass}">
                    <img src="/images/document-icon.svg" alt="文档图标" />
                </div>
                <div class="document-title">
                    <h3>${document.title}</h3>
                    <p class="document-filename">${document.filename}</p>
                </div>
            </div>
        </td>
        <td>
            <span class="badge ${categoryBadgeClass}">${document.category}</span>
        </td>
        <td>
            ${document.tags.map(tag => `<span class="badge tag">${tag}</span>`).join('')}
        </td>
        <td>${document.size}</td>
        <td>${document.uploadDate}</td>
        <td>${document.lastModified}</td>
        <td>
            <div class="action-buttons">
                <a href="/documents/view/${document.id}" class="action-button view" title="查看">
                    <svg viewBox="0 0 24 24" width="16" height="16">
                        <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2" />
                        <path d="M12 16v-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                        <path d="M12 8h.01" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                        <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="2" />
                    </svg>
                </a>
                <a href="/documents/edit/${document.id}" class="action-button edit" title="编辑">
                    <svg viewBox="0 0 24 24" width="16" height="16">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                </a>
                <a href="/documents/download/${document.id}" class="action-button download" title="下载">
                    <svg viewBox="0 0 24 24" width="16" height="16">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                        <polyline points="7 10 12 15 17 10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                        <line x1="12" y1="15" x2="12" y2="3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                </a>
                <a href="/documents/delete/${document.id}" class="action-button delete" title="删除">
                    <svg viewBox="0 0 24 24" width="16" height="16">
                        <polyline points="3 6 5 6 21 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                </a>
            </div>
        </td>
    `;
    
    return tr;
}

// 绑定事件监听器
function bindEventListeners() {
    const selectAllCheckbox = document.getElementById('select-all');
    const documentCheckboxes = document.querySelectorAll('.document-checkbox');
    const batchActionsButton = document.getElementById('batch-actions');
    
    // 确保元素存在
    if (selectAllCheckbox && documentCheckboxes.length > 0 && batchActionsButton) {
        // 移除现有的事件监听器
        const newSelectAll = selectAllCheckbox.cloneNode(true);
        selectAllCheckbox.parentNode.replaceChild(newSelectAll, selectAllCheckbox);
        
        // 全选/取消全选
        newSelectAll.addEventListener('change', function() {
            documentCheckboxes.forEach(checkbox => {
                checkbox.checked = this.checked;
            });
            updateBatchActionsButton();
        });
        
        // 单个复选框变化时更新批量操作按钮状态
        documentCheckboxes.forEach(checkbox => {
            checkbox.addEventListener('change', updateBatchActionsButton);
        });
        
        // 更新批量操作按钮状态
        function updateBatchActionsButton() {
            const checkedCount = document.querySelectorAll('.document-checkbox:checked').length;
            if (checkedCount > 0) {
                batchActionsButton.classList.remove('disabled');
                batchActionsButton.setAttribute('data-count', checkedCount);
            } else {
                batchActionsButton.classList.add('disabled');
                batchActionsButton.removeAttribute('data-count');
            }
        }
    }
}

// 导出模拟数据，供其他脚本使用
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { mockDocuments };
}