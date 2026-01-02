package com.example.rag.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 错误页面控制器
 * 用于处理各种错误页面的显示
 */
@Controller
public class ErrorController {

    /**
     * 处理访问被拒绝页面
     */
    @GetMapping("/error/access-denied")
    public ModelAndView accessDenied(HttpServletRequest request, HttpServletResponse response) {
        ModelAndView modelAndView = new ModelAndView("error/access-denied");
        
        // 可以根据请求属性添加错误信息
        String errorMessage = (String) request.getAttribute("errorMessage");
        if (errorMessage != null) {
            modelAndView.addObject("message", errorMessage);
        }
        
        return modelAndView;
    }
    
    /**
     * 处理404错误页面
     */
    @GetMapping("/error/404")
    public String notFound() {
        return "error/404";
    }
    
    /**
     * 处理通用错误页面
     */
    @GetMapping("/error")
    public String generalError() {
        return "error/error";
    }
}