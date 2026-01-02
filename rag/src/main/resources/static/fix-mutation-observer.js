// MutationObserver 全局修复脚本
// 解决 TypeError: Failed to execute 'observe' on 'MutationObserver': parameter 1 is not of type 'Node'

(function() {
    'use strict';
    
    // 初始化函数
    function init() {
        // 检查MutationObserver是否存在
        if (!window.MutationObserver) {
            console.warn('MutationObserver不存在，无法应用修复');
            return;
        }
        
        // 保存原始的MutationObserver构造函数
        const OriginalMutationObserver = window.MutationObserver;
        
        // 重写MutationObserver构造函数
        window.MutationObserver = function(callback) {
            // 验证回调函数
            if (typeof callback !== 'function') {
                console.warn('MutationObserver: 回调必须是函数');
                callback = function() {};
            }
            
            // 创建原始的MutationObserver实例
            const observer = new OriginalMutationObserver(function(mutations, obs) {
                try {
                    callback(mutations, obs);
                } catch (error) {
                    console.error('MutationObserver回调错误:', error);
                    // 防止回调错误影响整个应用
                }
            });
            
            // 保存原始的observe方法
            const originalObserve = observer.observe;
            const originalDisconnect = observer.disconnect;
            const originalTakeRecords = observer.takeRecords;
            
            // 重写observe方法，添加参数验证
            observer.observe = function(target, options) {
                try {
                    // 全面检查target是否为有效的Node类型
                    if (!target) {
                        console.warn('MutationObserver.observe: 目标元素为null或undefined');
                        return;
                    }
                    
                    // 检查是否为Node对象
                    if (!(target instanceof Node)) {
                        console.warn('MutationObserver.observe: 目标不是Node类型', typeof target, target);
                        // 尝试获取实际DOM节点（处理可能的包装对象）
                        if (target.node && target.node instanceof Node) {
                            return originalObserve.call(observer, target.node, options);
                        }
                        return;
                    }
                    
                    // 验证节点类型
                    const validNodeTypes = [1, 9, 11]; // Element, Document, DocumentFragment
                    if (!validNodeTypes.includes(target.nodeType)) {
                        console.warn('MutationObserver.observe: 不支持的节点类型', target.nodeType);
                        return;
                    }
                    
                    // 验证options参数
                    if (!options || typeof options !== 'object') {
                        console.warn('MutationObserver.observe: 无效的选项参数');
                        options = { childList: true };
                    }
                    
                    // 调用原始方法
                    return originalObserve.call(observer, target, options);
                } catch (error) {
                    console.error('MutationObserver.observe 错误:', error);
                    // 捕获所有错误，确保程序不会崩溃
                    return undefined;
                }
            };
            
            // 重写disconnect方法，增加错误处理
            observer.disconnect = function() {
                try {
                    return originalDisconnect.call(observer);
                } catch (error) {
                    console.error('MutationObserver.disconnect 错误:', error);
                    return undefined;
                }
            };
            
            // 重写takeRecords方法，增加错误处理
            observer.takeRecords = function() {
                try {
                    return originalTakeRecords.call(observer);
                } catch (error) {
                    console.error('MutationObserver.takeRecords 错误:', error);
                    return [];
                }
            };
            
            return observer;
        };
        
        // 复制静态方法和属性
        window.MutationObserver.prototype = OriginalMutationObserver.prototype;
        window.MutationObserver.prototype.constructor = window.MutationObserver;
        
        // 复制静态属性（如果存在）
        if (OriginalMutationObserver.TakeRecords && typeof OriginalMutationObserver.TakeRecords === 'function') {
            window.MutationObserver.TakeRecords = OriginalMutationObserver.TakeRecords;
        }
        
        // 添加安全的元素访问辅助函数
        window.safeElementAccess = function(selector, callback) {
            try {
                if (typeof selector !== 'string') {
                    console.warn('safeElementAccess: 选择器必须是字符串');
                    return false;
                }
                
                if (typeof callback !== 'function') {
                    console.warn('safeElementAccess: 回调必须是函数');
                    return false;
                }
                
                const element = document.querySelector(selector);
                if (element && element instanceof Node) {
                    callback(element);
                    return true;
                }
                return false;
            } catch (error) {
                console.error('safeElementAccess 错误:', error);
                return false;
            }
        };
        
        // 添加批量安全元素访问
        window.safeElementsAccess = function(selector, callback) {
            try {
                if (typeof selector !== 'string') {
                    console.warn('safeElementsAccess: 选择器必须是字符串');
                    return false;
                }
                
                if (typeof callback !== 'function') {
                    console.warn('safeElementsAccess: 回调必须是函数');
                    return false;
                }
                
                const elements = document.querySelectorAll(selector);
                if (elements && elements.length > 0) {
                    elements.forEach(element => {
                        if (element instanceof Node) {
                            try {
                                callback(element);
                            } catch (err) {
                                console.error('单个元素处理错误:', err);
                            }
                        }
                    });
                    return true;
                }
                return false;
            } catch (error) {
                console.error('safeElementsAccess 错误:', error);
                return false;
            }
        };
        
        // 添加延迟执行辅助函数，确保DOM元素已加载
        window.ensureElementReady = function(selector, callback, timeout = 3000) {
            const startTime = Date.now();
            
            function checkElement() {
                const element = document.querySelector(selector);
                
                if (element && element instanceof Node) {
                    callback(element);
                    return;
                }
                
                if (Date.now() - startTime < timeout) {
                    setTimeout(checkElement, 50);
                } else {
                    console.warn('ensureElementReady: 元素未在超时时间内加载', selector);
                }
            }
            
            // 立即检查一次
            checkElement();
        };
        
        console.log('MutationObserver全局修复已成功应用');
    }
    
    // 确保在DOM加载完成后初始化
    function safeInit() {
        try {
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', function() {
                    setTimeout(init, 0); // 微任务延迟，确保其他DOMContentLoaded监听器先执行
                });
            } else {
                // 如果DOM已经加载完成，则使用setTimeout延迟执行，确保在其他脚本之后运行
                setTimeout(init, 0);
            }
        } catch (error) {
            console.error('MutationObserver修复初始化错误:', error);
            // 即使初始化失败，也尝试应用基本修复
            setTimeout(init, 1000);
        }
    }
    
    // 启动初始化
    safeInit();
})();