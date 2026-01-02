package com.example.rag.controller;

import com.example.rag.model.User;
import com.example.rag.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 获取当前登录用户信息
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户未找到: " + username));
    }

    /**
     * 用户列表页面
     */
    @GetMapping
    public String userList(Model model, 
                          @RequestParam(required = false) Boolean success,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "10") int size) {
        // 设置分页参数
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page - 1, size); // page从0开始
        
        // 获取分页数据
        org.springframework.data.domain.Page<User> userPage;
        if (keyword != null && !keyword.trim().isEmpty()) {
            // 使用搜索方法
            String searchKeyword = keyword.trim();
            userPage = userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                searchKeyword, searchKeyword, searchKeyword, pageable);
            model.addAttribute("searchKeyword", searchKeyword);
        } else {
            // 默认查询所有
            userPage = userRepository.findAll(pageable);
        }
        
        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentUser", getCurrentUser());
        if (Boolean.TRUE.equals(success)) {
            model.addAttribute("successMessage", "用户添加成功");
        }
        
        // 将分页数据添加到模型
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("totalItems", userPage.getTotalElements());
        model.addAttribute("pageSize", size);
        
        return "users/list";
    }
    
    /**
     * 获取用户列表数据（JSON格式，用于Ajax请求）
     */
    @GetMapping("/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserListData(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 设置分页参数
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page - 1, size);
        
        // 获取分页数据
        org.springframework.data.domain.Page<User> userPage;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String searchKeyword = keyword.trim();
            userPage = userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                searchKeyword, searchKeyword, searchKeyword, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }
        
        // 构建响应数据
        Map<String, Object> response = new HashMap<>();
        response.put("users", userPage.getContent());
        response.put("currentPage", page);
        response.put("totalPages", userPage.getTotalPages());
        response.put("totalItems", userPage.getTotalElements());
        response.put("pageSize", size);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取用户列表数据（简化版JSON格式，用于前端Ajax请求）
     */
    @GetMapping("/list")
    @ResponseBody
    public Map<String, Object> getUsersList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        // 设置分页参数
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page - 1, size);
        
        // 获取分页数据
        org.springframework.data.domain.Page<User> userPage;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String searchKeyword = keyword.trim();
            userPage = userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                searchKeyword, searchKeyword, searchKeyword, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }
        
        // 构建响应数据
        Map<String, Object> result = new HashMap<>();
        result.put("users", userPage.getContent());
        result.put("currentPage", page);
        result.put("totalPages", userPage.getTotalPages());
        result.put("totalItems", userPage.getTotalElements());
        result.put("pageSize", size);
        
        return result;
    }

    /**
     * 用户详情页面
     */
    @GetMapping("/{id}")
    public String userDetail(@PathVariable Long id, Model model) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            model.addAttribute("user", userOptional.get());
            model.addAttribute("currentUser", getCurrentUser());
            return "users/detail";
        } else {
            model.addAttribute("errorMessage", "用户未找到");
            return "users/list";
        }
    }

    /**
     * 添加用户页面
     */
    @GetMapping("/add")
    public String addUserForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("currentUser", getCurrentUser());
        // 添加部门列表
        List<String> departments = List.of("财务部", "人力资源部", "技术部", "市场部", "销售部", "行政部", "运营部");
        model.addAttribute("departments", departments);
        return "users/add";
    }

    /**
     * 处理添加用户
     */
    @PostMapping("/add")
    public String addUser(@ModelAttribute User user, Model model) {
        try {
            // 检查用户名是否已存在
            if (userRepository.findByUsername(user.getUsername()).isPresent()) {
                model.addAttribute("errorMessage", "用户名已存在");
                model.addAttribute("user", user);
                model.addAttribute("currentUser", getCurrentUser());
                return "users/add";
            }

            // 检查邮箱是否已存在
            if (userRepository.findByEmail(user.getEmail()).isPresent()) {
                model.addAttribute("errorMessage", "邮箱已存在");
                model.addAttribute("user", user);
                model.addAttribute("currentUser", getCurrentUser());
                return "users/add";
            }

            // 验证密码长度
            if (user.getPassword() == null || user.getPassword().length() < 6) {
                model.addAttribute("errorMessage", "密码长度至少为6位");
                model.addAttribute("user", user);
                model.addAttribute("currentUser", getCurrentUser());
                return "users/add";
            }

            // 加密密码
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            
            // 设置默认角色
            if (user.getRole() == null) {
                user.setRole(User.Role.USER);
            }

            // 设置创建时间
            user.setCreatedAt(LocalDateTime.now());
            
            // 保存用户
            userRepository.save(user);
            
            // 使用重定向属性传递成功消息
            return "redirect:/users?success=true";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "添加用户失败: " + e.getMessage());
            model.addAttribute("user", user);
            model.addAttribute("currentUser", getCurrentUser());
            return "users/add";
        }
    }

    /**
     * 编辑用户页面
     */
    @GetMapping("/edit/{id}")
    public String editUserForm(@PathVariable Long id, Model model) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            model.addAttribute("user", userOptional.get());
            model.addAttribute("currentUser", getCurrentUser());
            // 添加部门列表
            List<String> departments = List.of("财务部", "人力资源部", "技术部", "市场部", "销售部", "行政部", "运营部");
            model.addAttribute("departments", departments);
            return "users/edit";
        } else {
            model.addAttribute("errorMessage", "用户未找到");
            return "redirect:/users";
        }
    }

    /**
     * 处理编辑用户
     */
    @PostMapping("/edit/{id}")
    public String editUser(@PathVariable Long id, @ModelAttribute User user, Model model) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            User existingUser = userOptional.get();
            
            // 检查用户名是否被其他用户使用
            Optional<User> userWithSameUsername = userRepository.findByUsername(user.getUsername());
            if (userWithSameUsername.isPresent() && !userWithSameUsername.get().getId().equals(id)) {
                model.addAttribute("errorMessage", "用户名已被其他用户使用");
                model.addAttribute("user", user);
                model.addAttribute("currentUser", getCurrentUser());
                return "users/edit";
            }

            // 检查邮箱是否被其他用户使用
            Optional<User> userWithSameEmail = userRepository.findByEmail(user.getEmail());
            if (userWithSameEmail.isPresent() && !userWithSameEmail.get().getId().equals(id)) {
                model.addAttribute("errorMessage", "邮箱已被其他用户使用");
                model.addAttribute("user", user);
                model.addAttribute("currentUser", getCurrentUser());
                return "users/edit";
            }

            // 更新用户信息
            existingUser.setFullName(user.getFullName());
            existingUser.setUsername(user.getUsername());
            existingUser.setEmail(user.getEmail());
            existingUser.setRole(user.getRole());
            existingUser.setDepartment(user.getDepartment());
            
            // 如果输入了新密码，则更新密码
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
            }

            userRepository.save(existingUser);
            model.addAttribute("successMessage", "用户信息更新成功");
            return "redirect:/users";
        } else {
            model.addAttribute("errorMessage", "用户未找到");
            return "redirect:/users";
        }
    }

    /**
     * 删除用户
     */
    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id, Model model) {
        // 不允许删除当前登录用户
        User currentUser = getCurrentUser();
        if (currentUser.getId().equals(id)) {
            model.addAttribute("errorMessage", "不能删除当前登录用户");
            return "redirect:/users";
        }

        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            userRepository.delete(userOptional.get());
            model.addAttribute("successMessage", "用户删除成功");
        } else {
            model.addAttribute("errorMessage", "用户未找到");
        }
        return "redirect:/users";
    }

    /**
     * 我的资料页面
     */
    @GetMapping("/profile")
    public String userProfile(Model model) {
        User currentUser = getCurrentUser();
        model.addAttribute("user", currentUser);
        model.addAttribute("currentUser", currentUser);
        return "users/profile";
    }

    /**
     * 更新我的资料
     */
    @PostMapping("/profile/update")
    public String updateUserProfile(@ModelAttribute User user, Model model) {
        User currentUser = getCurrentUser();
        
        // 检查邮箱是否被其他用户使用
        Optional<User> userWithSameEmail = userRepository.findByEmail(user.getEmail());
        if (userWithSameEmail.isPresent() && !userWithSameEmail.get().getId().equals(currentUser.getId())) {
            model.addAttribute("errorMessage", "邮箱已被其他用户使用");
            model.addAttribute("user", currentUser);
            model.addAttribute("currentUser", currentUser);
            return "users/profile";
        }

        // 更新用户信息
        currentUser.setFullName(user.getFullName());
        currentUser.setEmail(user.getEmail());
        currentUser.setDepartment(user.getDepartment());
        
        // 如果输入了新密码，则更新密码
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            currentUser.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        userRepository.save(currentUser);
        model.addAttribute("successMessage", "个人资料更新成功");
        model.addAttribute("user", currentUser);
        model.addAttribute("currentUser", currentUser);
        return "users/profile";
    }
}