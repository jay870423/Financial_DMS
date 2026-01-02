package com.example.rag.controller;

import com.example.rag.model.User;
import com.example.rag.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/**
 * 作者: liangyajie
 * 联系方式: 695274107@qq.com
 * 认证控制器
 */
@Controller
public class AuthController {
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    /**
     * 登录页面
     */
    @GetMapping("/login")
    public String login(Model model, @RequestParam(required = false) String error, 
                        @RequestParam(required = false) String logout, 
                        @RequestParam(required = false) Boolean expired) {
        if (error != null) {
            model.addAttribute("errorMessage", "用户名或密码错误");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "您已成功登出");
        }
        if (expired != null && expired) {
            model.addAttribute("errorMessage", "会话已过期，请重新登录");
        }
        return "login";
    }
    
    /**
     * 注册页面
     */
    @GetMapping("/register")
    public String register(Model model) {
        return "register";
    }
    
    /**
     * 注册处理
     */
    @PostMapping("/register")
    public String registerUser(@RequestParam String username, @RequestParam String password,
                             @RequestParam String confirmPassword, @RequestParam String email,
                             @RequestParam String fullName, Model model,
                             RedirectAttributes redirectAttributes) {
        // 验证用户名是否已存在
        if (userRepository.existsByUsername(username)) {
            model.addAttribute("errorMessage", "用户名已存在");
            return "register";
        }
        
        // 验证邮箱是否已存在
        if (userRepository.existsByEmail(email)) {
            model.addAttribute("errorMessage", "邮箱已被注册");
            return "register";
        }
        
        // 验证密码一致性
        if (!password.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "两次输入的密码不一致");
            return "register";
        }
        
        // 验证密码强度
        if (password.length() < 6) {
            model.addAttribute("errorMessage", "密码长度至少为6位");
            return "register";
        }
        
        // 创建新用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(User.Role.USER);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        userRepository.save(user);
        
        // 移除自动登录，改为重定向到登录页面让用户手动登录
        redirectAttributes.addFlashAttribute("successMessage", "注册成功！请登录您的新账号");
        return "redirect:/login";
    }
    
    /**
     * 登出处理
     */
    @GetMapping("/logout")
    public String logout() {
        return "redirect:/login?logout=true";
    }
}