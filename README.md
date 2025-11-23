# Financial_DMS - 智能财务文档管理系统

**作者**: liangyajie
**联系方式**: 695274107@qq.com

## 项目简介

Financial_DMS是一个基于RAG（检索增强生成）技术的智能财务文档管理系统，集成了本地Ollama大语言模型，提供高效的文档上传、智能检索和基于上下文的问答功能。系统采用现代Web开发技术栈构建，具备用户友好的界面和强大的文档处理能力。

## 功能亮点

### 📚 文档管理
- 支持多种格式文档上传（通过Apache Tika实现多格式解析）
- 文档分类、搜索和批量操作
- 完整的文档CRUD操作和权限管理

### 🤖 智能检索增强生成（RAG）
- 基于文档内容的语义检索
- 本地Ollama模型集成，确保数据隐私
- 流式响应生成，提供更流畅的用户体验
- 基于上下文的精准问答，只回答文档中包含的信息

### 🔍 文本处理技术
- 智能文本分割算法，基于句子和段落进行优化分块
- 支持分块重叠设置，确保上下文连贯性
- 使用向量嵌入技术进行文档表示

### 📊 数据分析与可视化
- 仪表盘统计功能，展示系统使用情况
- 用户管理与权限控制
- 系统设置和配置管理

## 技术栈

### 后端技术
- **框架**: Spring Boot 3.5.7
- **语言**: Java 21
- **数据库**: H2（开发环境）
- **搜索引擎**: Elasticsearch（用于向量检索）
- **文档处理**: Apache Tika
- **AI模型**: Ollama（本地大语言模型）
- **安全**: Spring Security, JWT
- **API**: RESTful设计

### 前端技术
- **框架**: HTML5, CSS3, JavaScript
- **样式**: Tailwind CSS v3
- **图标**: Font Awesome
- **模板引擎**: Thymeleaf
- **响应式设计**: 支持移动端和桌面端

## 系统架构

### 核心组件
1. **文档处理模块**
   - 文档上传与解析
   - 文本分割与向量化
   - 文件存储管理

2. **RAG引擎**
   - 向量嵌入生成（通过Ollama API）
   - Elasticsearch向量存储与检索
   - 提示词构建与优化

3. **模型集成**
   - OllamaClient：与本地Ollama模型交互
   - 支持流式和非流式响应
   - 可配置模型参数（温度、top_p等）

4. **用户管理**
   - 基于Spring Security的认证授权
   - 微信登录集成
   - 用户信息和权限管理

5. **Web界面**
   - 现代化UI设计
   - 响应式布局
   - 交互友好的用户体验

## 核心功能实现

### RAG工作流
1. **文档处理**：上传文档 → 解析内容 → 智能分块 → 生成嵌入向量
2. **检索过程**：用户提问 → 查询转换 → 向量相似度搜索 → 召回相关文档块
3. **生成回答**：构建提示词 → 调用Ollama模型 → 流式返回回答

### 智能分块算法
- 基于句子和段落的自然分割
- 支持可配置的块大小和重叠比例
- 处理超长段落和句子的特殊逻辑

### Ollama集成
- 支持向量嵌入生成
- 流式和非流式聊天完成
- 可自定义模型参数（温度、top_p、生成长度等）

## 开发与部署

### 环境要求
- JDK 21+
- Maven 3.8+
- Ollama服务（默认端口11434）
- Elasticsearch服务

### 快速开始
1. 克隆项目仓库
2. 配置Ollama服务地址（默认：http://localhost:11434）
3. 配置Elasticsearch连接
4. 运行Spring Boot应用
   ```bash
   mvn spring-boot:run
   ```
5. 访问 http://localhost:8080 进入系统

### 系统配置
- Ollama模型配置可在系统设置中调整
- 向量检索参数可根据需求优化
- 分块大小和重叠比例可通过配置文件调整

## 安全性

- 基于Spring Security的认证授权机制
- JWT令牌管理
- 敏感操作权限控制
- 本地模型部署确保数据隐私

## 许可证

© 2024 Financial_DMS Team

## 运行效果图

*注：此处可添加系统运行时的截图，建议包括以下页面：
- 系统登录界面
  <img width="625" height="840" alt="image" src="https://github.com/user-attachments/assets/d7123f56-0868-40f3-8e6a-76fc7e5ab777" />
  <img width="506" height="855" alt="image" src="https://github.com/user-attachments/assets/121c874a-4d5d-4904-bd64-0be5e13bd0a5" />
  <img width="521" height="554" alt="image" src="https://github.com/user-attachments/assets/420188d6-e110-46dc-b486-011753836e34" />

- 文档管理主页
<img width="1920" height="806" alt="image" src="https://github.com/user-attachments/assets/1df4e080-faa5-49bd-bf54-c508f1a590f9" />
<img width="1806" height="870" alt="image" src="https://github.com/user-attachments/assets/90082b54-47ea-4c7b-ae66-0c5275fd779a" />

- RAG问答界面
<img width="1750" height="679" alt="image" src="https://github.com/user-attachments/assets/354de9ce-cd1b-4c8a-aa38-d699acd3b128" />
<img width="1915" height="681" alt="image" src="https://github.com/user-attachments/assets/0baf1533-d685-4a6c-bda8-37ac9cdc479b" />

- 仪表盘统计页面
<img width="1915" height="815" alt="image" src="https://github.com/user-attachments/assets/b31719bb-5378-4e5a-8fdf-be70594007ab" />

实际部署后请替换为真实截图。*

---

*注：本系统需要本地运行Ollama服务以支持文档嵌入和问答功能。建议使用性能较好的硬件以获得最佳体验。*
