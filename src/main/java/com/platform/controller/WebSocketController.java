package com.platform.controller;

import com.platform.service.UserService;
import com.platform.service.WebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket控制器
 * 处理WebSocket相关的消息路由
 */
@Controller
public class WebSocketController {

    private final WebSocketService webSocketService;
    private final UserService userService;

    @Autowired
    public WebSocketController(WebSocketService webSocketService, UserService userService) {
        this.webSocketService = webSocketService;
        this.userService = userService;
    }

    /**
     * 处理用户心跳消息
     * 客户端定期发送心跳以保持连接活跃
     *
     * @param headerAccessor 消息头访问器
     * @param heartbeatData 心跳数据
     * @param principal 当前用户
     */
    @MessageMapping("/user.heartbeat")
    public void handleHeartbeat(SimpMessageHeaderAccessor headerAccessor,
                                Map<String, Object> heartbeatData,
                                Principal principal) {
        if (principal != null) {
            String username = principal.getName();
            // 更新用户活动时间
            userService.updateUserActivity(username);
            // 发送心跳响应
            webSocketService.sendHeartbeatResponse(username);
        }
    }

    /**
     * 处理用户连接消息
     *
     * @param headerAccessor 消息头访问器
     * @param connectData 连接数据
     * @param principal 当前用户
     */
    @MessageMapping("/user.connect")
    public void handleUserConnect(SimpMessageHeaderAccessor headerAccessor,
                                  Map<String, Object> connectData,
                                  Principal principal) {
        if (principal != null) {
            String username = principal.getName();
            userService.updateUserActivity(username);
        }
    }
}