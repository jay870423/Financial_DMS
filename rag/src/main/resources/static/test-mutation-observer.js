// MutationObserver修复测试脚本
console.log('MutationObserver修复测试开始...');

try {
    // 测试1：有效节点测试
    console.log('测试1: 有效节点测试');
    const testElement = document.createElement('div');
    testElement.id = 'test-element';
    document.body.appendChild(testElement);
    
    const observer1 = new MutationObserver(function(mutations) {
        console.log('有效节点观察器触发:', mutations);
    });
    
    observer1.observe(testElement, { childList: true });
    
    // 触发变更
    setTimeout(() => {
        const child = document.createElement('span');
        child.textContent = '测试内容';
        testElement.appendChild(child);
    }, 500);
    
    // 测试2：无效节点测试
    console.log('测试2: 无效节点测试');
    const invalidTarget = '这不是一个节点';
    
    const observer2 = new MutationObserver(function(mutations) {
        console.log('这不应该被触发');
    });
    
    // 这应该不会抛出错误
    observer2.observe(invalidTarget, { childList: true });
    
    // 测试3：null节点测试
    console.log('测试3: null节点测试');
    const observer3 = new MutationObserver(function(mutations) {
        console.log('这不应该被触发');
    });
    
    // 这应该不会抛出错误
    observer3.observe(null, { childList: true });
    
    // 测试4：安全元素访问辅助函数
    console.log('测试4: 安全元素访问辅助函数');
    const result = window.safeElementAccess('#test-element', function(element) {
        console.log('安全访问成功:', element.id);
    });
    console.log('safeElementAccess结果:', result);
    
    // 测试5：不存在的元素访问
    console.log('测试5: 不存在的元素访问');
    const result2 = window.safeElementAccess('#non-existent-element', function(element) {
        console.log('这不应该被触发');
    });
    console.log('safeElementAccess结果:', result2);
    
    // 测试6：延迟元素确保函数
    console.log('测试6: 延迟元素确保函数');
    window.ensureElementReady('#test-element', function(element) {
        console.log('ensureElementReady成功:', element.id);
    }, 2000);
    
    console.log('MutationObserver修复测试完成');
    
} catch (error) {
    console.error('MutationObserver测试过程中出现错误:', error);
}