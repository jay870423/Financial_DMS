package com.example.rag.controller;

import com.example.rag.model.User;
import com.example.rag.repository.UserRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import java.util.Base64;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 作者: liangyajie
 * 联系方式: 695274107@qq.com
 * 微信登录控制器
 */
@Controller
public class WechatLoginController {
    
    // 微信接口常量
    private static final String WECHAT_QRCODE_TICKET_URL = "https://api.weixin.qq.com/cgi-bin/qrcode/create";
    private static final String WECHAT_SHOWQRCODE_URL = "https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=";
    private static final String WECHAT_ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String WECHAT_SNS_OAUTH2_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String WECHAT_SNS_ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String WECHAT_USER_INFO_URL = "https://api.weixin.qq.com/sns/userinfo";
    
    @Value("${wechat.app-id}")
    private String appId;
    
    @Value("${wechat.app-secret}")
    private String appSecret;
    
    @Value("${wechat.redirect-uri}")
    private String redirectUri;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * 微信登录入口 - 使用OAuth2.0授权码模式
     */
    @GetMapping("/wechat/login")
    public String wechatLogin(Model model) {
        try {
            // 生成随机state参数防止CSRF攻击
            String state = UUID.randomUUID().toString();
            
            // 构建微信授权URL（授权码模式）
            String encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString());
            String wechatOAuthUrl = String.format(
                "%s?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_userinfo&state=%s#wechat_redirect",
                WECHAT_SNS_OAUTH2_URL, appId, encodedRedirectUri, state);
            
            // 生成微信授权二维码
            String qrCodeBase64 = generateQRCodeBase64(wechatOAuthUrl, 200, 200);
            
            model.addAttribute("wechatOAuthUrl", wechatOAuthUrl);
            model.addAttribute("qrCodeBase64", qrCodeBase64);
            model.addAttribute("state", state);
            
            return "wechat-login";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "生成微信登录二维码失败：" + e.getMessage());
            return "wechat-login";
        }
    }
    
    /**
     * 微信回调处理
     */
    @GetMapping("/wechat/callback")
    public String wechatCallback(@RequestParam String code, @RequestParam String state, 
                                RedirectAttributes redirectAttributes) {
        try {
            // 验证state参数（可选，增强安全性）
            // 实际应用中应该将state存储在会话中并进行验证
            
            // 步骤1：使用code换取access_token和openid
            Map<String, Object> accessTokenResult = getWechatAccessToken(code);
            String openId = (String) accessTokenResult.get("openid");
            String accessToken = (String) accessTokenResult.get("access_token");
            String refreshToken = (String) accessTokenResult.get("refresh_token");
            
            // 步骤2：使用access_token和openid获取用户信息
            Map<String, Object> userInfo = getUserInfo(accessToken, openId);
            
            // 步骤3：查找或创建微信用户
            User user = findOrCreateWechatUser(openId, userInfo);
            
            // 更新最后登录时间
            user.setLastLoginTime(LocalDateTime.now());
            userRepository.save(user);
            
            // 自动登录用户
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            redirectAttributes.addFlashAttribute("successMessage", "微信登录成功！");
            return "redirect:/dashboard";
        } catch (HttpClientErrorException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "微信API调用失败：" + e.getMessage());
            return "redirect:/login";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "微信登录失败：" + e.getMessage());
            return "redirect:/login";
        }
    }
    
    /**
     * 使用code换取微信access_token和openid
     */
    private Map<String, Object> getWechatAccessToken(String code) {
        String url = String.format(
            "%s?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
            WECHAT_SNS_ACCESS_TOKEN_URL, appId, appSecret, code);
        
        // 发送GET请求获取access_token
        return restTemplate.getForObject(url, Map.class);
    }
    
    /**
     * 获取微信用户信息
     */
    private Map<String, Object> getUserInfo(String accessToken, String openId) {
        String url = String.format(
            "%s?access_token=%s&openid=%s&lang=zh_CN",
            WECHAT_USER_INFO_URL, accessToken, openId);
        
        // 发送GET请求获取用户信息
        return restTemplate.getForObject(url, Map.class);
    }
    
    /**
     * 生成二维码并返回Base64编码
     */
    private String generateQRCodeBase64(String content, int width, int height) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        
        // 使用ZXing生成二维码矩阵
        BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);
        
        // 转换为BufferedImage
        BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
        
        // 转换为Base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageBytes = baos.toByteArray();
        
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
    }
    
    /**
     * 查找或创建微信用户
     */
    private User findOrCreateWechatUser(String openId, Map<String, Object> userInfo) {
        return userRepository.findByWechatOpenId(openId)
            .orElseGet(() -> {
                // 创建新用户
                User newUser = new User();
                newUser.setUsername("wechat_" + openId.substring(0, 10));
                newUser.setPassword(UUID.randomUUID().toString()); // 随机密码
                newUser.setWechatOpenId(openId);
                
                // 使用微信用户信息填充用户资料
                if (userInfo != null) {
                    newUser.setFullName((String) userInfo.getOrDefault("nickname", "微信用户"));
                    // 可以根据需要保存更多微信用户信息
                    // 注意：微信头像等信息可能需要额外处理
                } else {
                    newUser.setFullName("微信用户");
                }
                
                newUser.setRole(User.Role.USER);
                newUser.setCreatedAt(LocalDateTime.now());
                newUser.setUpdatedAt(LocalDateTime.now());
                
                return userRepository.save(newUser);
            });
    }
}