package com.example.rag.controller;

import com.example.rag.model.Document;
import com.example.rag.model.User;
import com.example.rag.repository.UserRepository;
import com.example.rag.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.io.IOException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 作者: liangyajie
 * 联系方式: 695274107@qq.com
 * 文档控制器
 */
@Controller
@RequestMapping("/documents")
public class DocumentController {
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 获取当前登录用户的实体对象
     */
    private User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("用户未找到: " + username));
    }
    
    /**
     * 文档列表页面（不分页，支持搜索和排序）
     */
    @GetMapping
    public String documentList(Model model, @AuthenticationPrincipal UserDetails userDetails, 
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String dateFrom,
                               @RequestParam(required = false) String dateTo,
                               @RequestParam(required = false, defaultValue = "newest") String sort) {
        // 获取当前用户的实际User对象
        User user = getCurrentUser(userDetails.getUsername());
        
        List<Document> documents;
        
        // 解析日期参数
        LocalDateTime parsedDateFrom = null;
        LocalDateTime parsedDateTo = null;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        try {
            if (dateFrom != null && !dateFrom.isEmpty()) {
                LocalDate localDateFrom = LocalDate.parse(dateFrom, dateFormatter);
                parsedDateFrom = localDateFrom.atStartOfDay();
            }
            
            if (dateTo != null && !dateTo.isEmpty()) {
                LocalDate localDateTo = LocalDate.parse(dateTo, dateFormatter);
                parsedDateTo = localDateTo.plusDays(1).atStartOfDay().minusNanos(1); // 设置为当天的最后一秒
            }
        } catch (Exception e) {
            // 如果日期格式不正确，忽略日期筛选条件
        }
        
        // 根据不同条件和排序方式获取文档列表
        // 确保即使category为空字符串也正确处理
        if (keyword != null && !keyword.isEmpty()) {
            // 如果有搜索关键词，优先使用关键词搜索
            documents = documentService.searchByTitle(keyword, user, sort, parsedDateFrom, parsedDateTo);
            model.addAttribute("searchKeyword", keyword);
        } else if (category != null && !category.isEmpty()) {
            // 只有当category不为null且不为空字符串时才按类别筛选
            documents = documentService.searchByCategory(category, user, sort, parsedDateFrom, parsedDateTo);
            model.addAttribute("activeCategory", category);
        } else {
            // 否则获取所有文档
            documents = documentService.getUserDocuments(user, sort, parsedDateFrom, parsedDateTo);
        }
        
        // 直接使用文档对象列表，不进行格式化，让Thymeleaf模板使用#temporals.format来处理日期
        // 这样可以保持日期对象的类型，避免类型不匹配错误
        // 添加日期范围到模型，用于回显
        model.addAttribute("documents", documents);
        model.addAttribute("sortOrder", sort);
        model.addAttribute("statistics", documentService.getDocumentStatistics(user));
        model.addAttribute("startDate", dateFrom);
        model.addAttribute("endDate", dateTo);
        
        return "documents";
    }
    
    /**
     * 上传文档页面
     */
    @GetMapping("/upload")
    public String uploadForm(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        // 获取当前用户的实际User对象
        User user = getCurrentUser(userDetails.getUsername());
        model.addAttribute("statistics", documentService.getDocumentStatistics(user));
        return "documents/upload";
    }
    
    /**
     * 上传文档处理
     */
    @PostMapping("/upload")
    public String uploadDocument(@RequestParam("file") MultipartFile file,
                                @RequestParam String title,
                                @RequestParam(required = false) String category,
                                @RequestParam(required = false) String tags,
                                @RequestParam(required = false) String description,
                                @AuthenticationPrincipal UserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        // 获取当前用户的实际User对象
        User user = getCurrentUser(userDetails.getUsername());
        
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "请选择要上传的文件");
            return "redirect:/documents/upload";
        }
        
        try {
            Document savedDocument = documentService.uploadDocument(
                file, title, category, tags, description, user);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "文档上传成功：" + savedDocument.getTitle());
            return "redirect:/documents";
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "文档上传失败：" + e.getMessage());
            return "redirect:/documents/upload";
        }
    }
    
    /**
     * 文档详情页面
     */
    @GetMapping("/{id}")
    public String documentDetail(@PathVariable Long id, Model model,
                                @AuthenticationPrincipal UserDetails userDetails) {
        // 获取当前用户的实际User对象
        User user = getCurrentUser(userDetails.getUsername());
        Document document = documentService.getDocumentById(id, user);
        
        // 创建一个包含格式化数据的Map
        Map<String, Object> documentData = new HashMap<>();
        documentData.put("id", document.getId());
        documentData.put("title", document.getTitle());
        documentData.put("category", document.getCategory());
        documentData.put("description", document.getDescription());
        documentData.put("tags", document.getTags());
        documentData.put("fileSize", document.getFileSize());
        documentData.put("fileName", document.getFileName());
        
        // 在后端格式化所有日期时间字段 - 使用正确的LocalDateTime格式化方式
        if (document.getCreatedAt() != null) {
            documentData.put("date", document.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            documentData.put("createdAt", document.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            documentData.put("createdTime", document.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (document.getUpdatedAt() != null) {
            documentData.put("updatedAt", document.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            documentData.put("lastModified", document.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (document.getLastAccessed() != null) {
            documentData.put("lastAccessed", document.getLastAccessed().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        
        model.addAttribute("document", documentData);
        return "documents/detail";
    }
    
    /**
     * 编辑文档页面
     */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model,
                          @AuthenticationPrincipal UserDetails userDetails) {
        // 获取当前用户的实际User对象
        User user = getCurrentUser(userDetails.getUsername());
        Document document = documentService.getDocumentById(id, user);
        
        // 直接使用文档对象，不进行格式化，让Thymeleaf模板使用#temporals.format来处理日期
        // 这样可以保持日期对象的类型，避免类型不匹配错误
        model.addAttribute("document", document);
        return "documents/edit";
    }
    
    /**
     * 更新文档处理
     */
    @PostMapping("/{id}/edit")
    public String updateDocument(@PathVariable Long id,
                                @RequestParam String title,
                                @RequestParam(required = false) String category,
                                @RequestParam(required = false) String tags,
                                @RequestParam(required = false) String description,
                                @AuthenticationPrincipal UserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        // 获取当前用户的实际User对象
        User user = getCurrentUser(userDetails.getUsername());
        
        try {
            Document updatedDocument = documentService.updateDocument(
                id, title, category, tags, description, user);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "文档更新成功：" + updatedDocument.getTitle());
            return "redirect:/documents";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "文档更新失败：" + e.getMessage());
            return "redirect:/documents/" + id + "/edit";
        }
    }
    
    /**
     * 下载文档
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id, 
                                                  @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        // 获取当前用户
        User user = getCurrentUser(userDetails.getUsername());
        
        // 获取文档信息
        Document document = documentService.getDocumentById(id, user);
        
        // 构建文件路径
        Path filePath = Paths.get(document.getFilePath());
        
        // 检查文件是否存在
        if (!Files.exists(filePath)) {
            throw new RuntimeException("文件不存在: " + filePath);
        }
        
        // 读取文件内容
        byte[] fileContent = Files.readAllBytes(filePath);
        
        // 设置响应头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        
        // 使用文档标题作为下载文件名，并保留原始文件扩展名
        String originalFileName = document.getFileName();
        String fileExtension = "";
        
        // 安全地提取文件扩展名
        if (originalFileName != null && originalFileName.contains(".")) {
            int lastDotIndex = originalFileName.lastIndexOf('.');
            if (lastDotIndex > 0 && lastDotIndex < originalFileName.length() - 1) {
                fileExtension = originalFileName.substring(lastDotIndex);
            }
        }
        
        // 组合下载文件名
        String downloadFileName = document.getTitle() + fileExtension;
        
        // 设置带有UTF-8编码的Content-Disposition头
        try {
            String encodedFileName = java.net.URLEncoder.encode(downloadFileName, "UTF-8");
            headers.set("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
        } catch (java.io.UnsupportedEncodingException e) {
            // 如果编码失败，使用原始文件名
            headers.setContentDispositionFormData("attachment", downloadFileName);
        }
        headers.setContentLength(fileContent.length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(fileContent);
    }
    
    /**
     * 渲染文档预览页面 - 支持标准路径
     */
    @GetMapping("/view/{id}")
    public String viewDocument(@PathVariable Long id,
                             @AuthenticationPrincipal UserDetails userDetails,
                             Model model) {
        // 获取当前用户
        User user = getCurrentUser(userDetails.getUsername());
        
        // 获取文档信息
        Document document = documentService.getDocumentById(id, user);
        
        // 将用户信息添加到模型中
        model.addAttribute("user", user);
        model.addAttribute("documentId", id);
        
        return "document-preview";
    }
    

    
    /**
     * 预览文档内容（API端点）
     */
    @GetMapping("/preview/{id}")
    @ResponseBody
    public ResponseEntity<?> previewDocument(@PathVariable Long id, 
                                           @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        // 获取当前用户
        User user = getCurrentUser(userDetails.getUsername());
        
        // 获取文档信息
        Document document = documentService.getDocumentById(id, user);
        
        // 构建响应数据
        Map<String, Object> response = new HashMap<>();
        response.put("title", document.getTitle());
        response.put("content", document.getContent());
        response.put("fileName", document.getFileName());
        response.put("contentType", document.getContentType());
        response.put("fileSize", document.getFileSize());
        response.put("category", document.getCategory());
        response.put("description", document.getDescription());
        response.put("tags", document.getTags());
        
        // 格式化日期时间字段 - 使用正确的LocalDateTime格式化方式
        if (document.getCreatedAt() != null) {
            response.put("date", document.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            response.put("createdTime", document.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            response.put("uploadDate", document.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (document.getUpdatedAt() != null) {
            response.put("lastModified", document.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (document.getLastAccessed() != null) {
            response.put("lastAccessed", document.getLastAccessed().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 删除文档
     */
    @PostMapping("/{id}/delete")
    public String deleteDocument(@PathVariable Long id,
                                @AuthenticationPrincipal UserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        // 获取当前用户的实际User对象
        User user = getCurrentUser(userDetails.getUsername());
        
        try {
            documentService.deleteDocument(id, user);
            redirectAttributes.addFlashAttribute("successMessage", "文档删除成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "文档删除失败：" + e.getMessage());
        }
        
        return "redirect:/documents";
    }
    
    /**
     * 向量化搜索页面
     */
    @GetMapping("/search")
    public String searchForm(Model model) {
        return "documents/search";
    }
    
    /**
     * 向量化搜索处理
     */
    @PostMapping("/search")
    public String vectorSearch(@RequestParam String query,
                             @RequestParam(defaultValue = "10") int limit,
                             @AuthenticationPrincipal UserDetails userDetails,
                             Model model) {
        // 获取当前用户的实际User对象
        User user = getCurrentUser(userDetails.getUsername());
        
        if (query == null || query.trim().isEmpty()) {
            model.addAttribute("errorMessage", "请输入搜索内容");
            return "documents/search";
        }
        
        List<Map<String, Object>> results = documentService.vectorSearch(query, user, limit);
        model.addAttribute("results", results);
        model.addAttribute("query", query);
        model.addAttribute("resultCount", results.size());
        
        return "documents/search-results";
    }
    
    /**
     * 文档数据API端点 - 返回JSON格式的分页文档数据
     */
    @GetMapping("/data")
    @ResponseBody
    public ResponseEntity<?> getDocumentsJson(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        
        // 获取当前用户
        User user = getCurrentUser(userDetails.getUsername());
        
        // 解析日期参数
        LocalDateTime fromDate = null;
        LocalDateTime toDate = null;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        try {
            if (dateFrom != null && !dateFrom.isEmpty()) {
                fromDate = LocalDate.parse(dateFrom, dateFormatter).atStartOfDay();
            }
            if (dateTo != null && !dateTo.isEmpty()) {
                toDate = LocalDate.parse(dateTo, dateFormatter).atStartOfDay();
            }
        } catch (DateTimeParseException e) {
            // 日期解析失败，返回错误信息
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "日期格式错误，应为yyyy-MM-dd");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        // 根据条件查询文档
        List<Document> allResults;
        
        if (keyword != null && !keyword.isEmpty()) {
            // 如果有搜索关键词，优先使用关键词搜索
            allResults = documentService.searchByTitle(keyword, user, sort != null ? sort : "newest", fromDate, toDate);
        } else if (category != null && !category.isEmpty()) {
            // 只有当category不为null且不为空字符串时才按类别筛选
            allResults = documentService.searchByCategory(category, user, sort != null ? sort : "newest", fromDate, toDate);
        } else {
            // 否则获取所有文档
            allResults = documentService.getUserDocuments(user, sort != null ? sort : "newest", fromDate, toDate);
        }
        
        // 手动分页处理
        int start = (page - 1) * size;
        int end = Math.min(start + size, allResults.size());
        List<Document> pagedResults = (start < allResults.size()) ? allResults.subList(start, end) : java.util.Collections.emptyList();
        
        // 创建格式化后的文档数据列表
        List<Map<String, Object>> formattedResults = new ArrayList<>();
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd");
        java.text.SimpleDateFormat datetimeFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        for (Document doc : pagedResults) {
            Map<String, Object> docData = new HashMap<>();
            docData.put("id", doc.getId());
            docData.put("title", doc.getTitle());
            docData.put("category", doc.getCategory());
            docData.put("tags", doc.getTags());
            docData.put("fileSize", doc.getFileSize());
            docData.put("fileName", doc.getFileName());
            
            // 格式化日期时间字段 - 使用正确的LocalDateTime格式化方式
            if (doc.getCreatedAt() != null) {
                docData.put("createdAt", doc.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                docData.put("date", doc.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            }
            if (doc.getUpdatedAt() != null) {
                docData.put("updatedAt", doc.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                docData.put("lastModified", doc.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            if (doc.getLastAccessed() != null) {
                docData.put("lastAccessed", doc.getLastAccessed().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            
            formattedResults.add(docData);
        }
        
        // 创建分页结果
        Page<Map<String, Object>> formattedPage = new org.springframework.data.domain.PageImpl<>(
                formattedResults, PageRequest.of(page - 1, size), allResults.size());
        
        // 返回格式化后的分页数据
        return ResponseEntity.ok(formattedPage);
    }
}