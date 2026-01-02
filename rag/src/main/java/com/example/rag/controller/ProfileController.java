package com.example.rag.controller;

import com.example.rag.model.User;
import com.example.rag.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 显示个人资料页面
     */
    @GetMapping
    public String showProfile(Model model) {
        // 获取当前登录用户
        User currentUser = getCurrentUser();
        model.addAttribute("currentUser", currentUser);
        return "profile";
    }

    /**
     * 更新个人基本信息
     */
    @PostMapping("/update")
    public String updateProfile(@RequestParam String name, 
                              @RequestParam String email, 
                              @RequestParam(required = false) String department,
                              @RequestParam(required = false) String bio,
                              RedirectAttributes redirectAttributes) {
        
        // 获取当前登录用户
        User currentUser = getCurrentUser();
        
        // 检查邮箱是否已被其他用户使用
        if (!currentUser.getEmail().equals(email)) {
            if (userRepository.existsByEmail(email)) {
                redirectAttributes.addFlashAttribute("errorMessage", "邮箱已被其他用户使用");
                return "redirect:/profile";
            }
        }
        
        // 更新用户信息
        currentUser.setFullName(name);
        currentUser.setEmail(email);
        // 注意：User模型中没有department和bio字段，已移除
        
        // 保存更新
        userRepository.save(currentUser);
        
        redirectAttributes.addFlashAttribute("successMessage", "个人信息已成功更新");
        return "redirect:/profile";
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public String changePassword(@RequestParam String currentPassword,
                               @RequestParam String newPassword,
                               @RequestParam String confirmPassword,
                               RedirectAttributes redirectAttributes) {
        
        // 获取当前登录用户
        User currentUser = getCurrentUser();
        
        // 验证当前密码是否正确
        if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
            redirectAttributes.addFlashAttribute("errorMessage", "当前密码不正确");
            return "redirect:/profile#security";
        }
        
        // 验证新密码与确认密码是否一致
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "新密码与确认密码不一致");
            return "redirect:/profile#security";
        }
        
        // 验证密码强度
        if (!isPasswordStrong(newPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "密码强度不足，请包含大小写字母、数字和特殊字符，长度至少8位");
            return "redirect:/profile#security";
        }
        
        // 更新密码
        currentUser.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(currentUser);
        
        redirectAttributes.addFlashAttribute("successMessage", "密码已成功更新");
        return "redirect:/profile#security";
    }

    /**
     * 验证密码强度
     */
    private boolean isPasswordStrong(String password) {
        // 至少8位
        if (password.length() < 8) return false;
        
        // 包含小写字母
        boolean hasLowerCase = !password.equals(password.toUpperCase());
        // 包含大写字母
        boolean hasUpperCase = !password.equals(password.toLowerCase());
        // 包含数字
        boolean hasDigit = password.matches(".*\\d.*");
        // 包含特殊字符
        boolean hasSpecial = !password.matches("[A-Za-z0-9]*");
        
        // 至少满足三个条件
        int conditionsMet = 0;
        if (hasLowerCase) conditionsMet++;
        if (hasUpperCase) conditionsMet++;
        if (hasDigit) conditionsMet++;
        if (hasSpecial) conditionsMet++;
        
        return conditionsMet >= 3;
    }

    /**
     * 获取当前登录用户
     */
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户未找到: " + username));
    }
}