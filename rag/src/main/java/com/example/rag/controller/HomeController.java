package com.example.rag.controller;

import com.example.rag.model.User;
import com.example.rag.repository.UserRepository;
import com.example.rag.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 作者: liangyajie
 * 联系方式: 695274107@qq.com
 * 首页控制器
 */
@Controller
@RequestMapping
public class HomeController {
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 首页/仪表盘
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }
    
    /**
     * 仪表盘页面
     */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        // 只传递用户名，避免直接使用User实体对象
        model.addAttribute("username", userDetails.getUsername());
        
        // 获取当前用户的实际User对象用于统计和文档查询
        com.example.rag.model.User currentUser = getCurrentUser(userDetails.getUsername());
        model.addAttribute("user", currentUser);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("documentStats", documentService.getDocumentStatistics(currentUser));
        model.addAttribute("recentDocuments", documentService.getUserDocuments(currentUser, "newest").stream().limit(5).toList());
        
        // 统计信息
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("documentCategories", documentService.getDocumentCategoriesCount(currentUser));
        
        // 欢迎消息
        String welcomeMessage = "欢迎回来，" + currentUser.getFullName() + "！";
        model.addAttribute("welcomeMessage", welcomeMessage);
        
        return "dashboard";
    }
    
    /**
     * 获取仪表盘统计数据的API端点
     */
    @GetMapping("/dashboard/stats")
    @org.springframework.web.bind.annotation.ResponseBody
    public ResponseEntity<Map<String, Object>> getDashboardStats(@AuthenticationPrincipal UserDetails userDetails) {
        // 获取当前用户的实际User对象用于统计和文档查询
        com.example.rag.model.User currentUser = getCurrentUser(userDetails.getUsername());
        
        // 准备统计数据
        Map<String, Object> stats = new HashMap<>();
        stats.put("documentStats", documentService.getDocumentStatistics(currentUser));
        stats.put("totalUsers", userRepository.count());
        stats.put("documentCategories", documentService.getDocumentCategoriesCount(currentUser));
        
        // 计算用户增长统计
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.with(LocalTime.MIN);
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        LocalDateTime lastMonthStart = todayStart.minusMonths(1);
        LocalDateTime lastMonthEnd = todayStart.minusDays(1);
        
        long todayUsers = userRepository.countByCreatedAtBetween(todayStart, now);
        long yesterdayUsers = userRepository.countByCreatedAtBetween(yesterdayStart, todayStart);
        long lastMonthUsers = userRepository.countByCreatedAtBetween(lastMonthStart, lastMonthEnd);
        long totalUsers = userRepository.count();
        
        // 计算增长率
        double dailyGrowthRate = yesterdayUsers > 0 ? ((double)(todayUsers - yesterdayUsers) / yesterdayUsers * 100) : 0;
        double monthlyGrowthRate = lastMonthUsers > 0 ? ((double)(totalUsers - lastMonthUsers) / lastMonthUsers * 100) : 0;
        
        Map<String, Object> userGrowth = new HashMap<>();
        userGrowth.put("today", todayUsers);
        userGrowth.put("yesterday", yesterdayUsers);
        userGrowth.put("lastMonth", lastMonthUsers);
        userGrowth.put("dailyGrowthRate", Math.round(dailyGrowthRate * 10.0) / 10.0);
        userGrowth.put("monthlyGrowthRate", Math.round(monthlyGrowthRate * 10.0) / 10.0);
        
        stats.put("userGrowth", userGrowth);
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 获取当前登录用户的实体对象
     */
    private User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("用户未找到: " + username));
    }
    
    /**
     * 关于页面
     */
    @GetMapping("/about")
    public String about() {
        return "about";
    }
    
    /**
     * 帮助页面
     */
    @GetMapping("/help")
    public String help() {
        return "help";
    }
    
    /**
     * 处理仪表盘文档搜索请求
     */
    @PostMapping("/dashboard/search")
    public ResponseEntity<Map<String, Object>> searchDocuments(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> requestData) {
        
        // 获取搜索参数
        String keyword = requestData.getOrDefault("keyword", "");
        String sortOrder = requestData.getOrDefault("sortOrder", "newest");
        
        // 获取当前用户
        User currentUser = getCurrentUser(userDetails.getUsername());
        
        // 根据关键字搜索文档
        List<?> documents;
        if (keyword.isEmpty()) {
            // 如果没有关键字，获取所有用户文档
            documents = documentService.getUserDocuments(currentUser, sortOrder);
        } else {
            // 如果有关键字，执行搜索
            documents = documentService.searchDocuments(currentUser, keyword, sortOrder);
        }
        
        // 限制返回数量
        List<?> limitedDocuments = documents.stream().limit(5).collect(Collectors.toList());
        
        // 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("documents", limitedDocuments);
        response.put("total", documents.size());
        
        return ResponseEntity.ok(response);
    }
}