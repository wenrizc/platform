package com.platform.websocket;

import com.platform.entity.User;
import com.platform.service.UserService;
import com.platform.service.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Controller
public class UserWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(UserWebSocketHandler.class);

    private final UserService userService;
    private final WebSocketService webSocketService;

    @Autowired
    public UserWebSocketHandler(UserService userService, WebSocketService webSocketService) {
        this.userService = userService;
        this.webSocketService = webSocketService;
    }

    /**
     * 处理用户连接，关联用户名和会话
     */
    @MessageMapping("/user.connect")
    public void handleUserConnect(@Payload Map<String, Object> payload,
            SimpMessageHeaderAccessor headerAccessor) {
        String username = (String) payload.get("username");
        String sessionId = headerAccessor.getSessionId();

        // 从握手阶段获取HTTP会话ID
        String httpSessionId = (String) headerAccessor.getSessionAttributes().get("sessionId");

        if (username != null && !username.trim().isEmpty()) {
            // 使用HTTP会话ID查找用户
            User user = null;
            if (httpSessionId != null) {
                user = userService.findBySessionId(httpSessionId);
            }

            // 如果找不到，尝试使用WebSocket会话ID
            if (user == null) {
                user = userService.findByUsername(username);
            }

            // 如果用户存在且已经有活跃会话，拒绝新连接
            if (user != null && user.isActive() && !Objects.equals(user.getSessionId(), sessionId)
                    && !Objects.equals(user.getSessionId(), httpSessionId)) {
                // 向客户端发送拒绝消息
                Map<String, Object> response = new HashMap<>();
                response.put("type", "CONNECTION_REJECTED");
                response.put("reason", "USER_ALREADY_CONNECTED");
                response.put("message", "该账户已在其他设备登录");

                // 使用临时会话发送消息
                headerAccessor.getSessionAttributes().put("username", username);
                webSocketService.sendMessageToUser(username, "/queue/errors", response);
                logger.warn("用户 {} 尝试重复登录，已拒绝新连接", username);
                return;
            }

            // 用户验证逻辑
            if (user == null || !username.equals(user.getUsername())) {
                logger.warn("未授权的WebSocket连接尝试: {}", username);
                return;
            }

            // 存储用户名到会话属性中
            headerAccessor.getSessionAttributes().put("username", username);

            // 更新用户会话ID为WebSocket会话ID
            userService.updateUserSessionId(user.getId(), sessionId);

            // 更新用户活动时间
            userService.updateUserActivity(sessionId);

            // 设置Principal以便后续可以通过用户名发送定向消息
            headerAccessor.setUser(new Principal() {
                @Override
                public String getName() {
                    return username;
                }
            });

            logger.info("用户WebSocket连接成功: {}, 会话ID: {}", username, sessionId);
        }
    }

    /**
     * 处理用户主动退出登录
     */
    @MessageMapping("/user.logout")
    public void handleUserLogout(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String username = (String) headerAccessor.getSessionAttributes().get("username");

        if (username != null && sessionId != null) {
            User user = userService.findBySessionId(sessionId);
            if (user != null) {
                // 设置用户为离线状态
                boolean success = userService.logoutUser(sessionId);
                if (success) {
                    // 广播用户离线消息
                    webSocketService.sendUserStatusUpdate(user, false);
                    logger.info("用户主动退出登录: {}, 会话ID: {}", username, sessionId);
                }
            }
        }
    }

    /**
     * 处理用户心跳，保持连接活跃
     */
    @MessageMapping("/user.heartbeat")
    public void handleUserHeartbeat(@Payload Map<String, Object> payload,
            SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        if (sessionId != null) {
            userService.updateUserActivity(sessionId);

            String username = (String) headerAccessor.getSessionAttributes().get("username");
            if (username != null) {
                // 发送心跳响应
                webSocketService.sendHeartbeatResponse(username);
                logger.debug("收到用户心跳: {}", username);
            }
        }
    }

    /**
     * 监听WebSocket连接建立事件
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        logger.info("收到WebSocket连接: {}", sessionId);
    }

    /**
     * 监听WebSocket订阅事件
     */
    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();

        logger.debug("用户订阅: {}, 目标: {}", sessionId, destination);
    }

    /**
     * 监听WebSocket断开连接事件
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String username = (String) headerAccessor.getSessionAttributes().get("username");

        if (sessionId != null) {
            User user = userService.findBySessionId(sessionId);
            if (user != null) {
                // 设置用户为离线状态
                boolean success = userService.logoutUser(sessionId);
                if (success) {
                    // 广播用户离线消息
                    webSocketService.sendUserStatusUpdate(user, false);
                    logger.info("用户断开连接: {}, 会话ID: {}", username, sessionId);
                }
            } else {
                logger.info("未知会话断开连接: {}", sessionId);
            }
        }
    }

    /**
     * 处理大厅消息
     */
    @MessageMapping("/lobby.message")
    public void handleLobbyMessage(@Payload Map<String, Object> payload,
            SimpMessageHeaderAccessor headerAccessor) {
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if (username == null) {
            logger.warn("未授权的用户尝试发送大厅消息");
            return;
        }

        String message = (String) payload.get("message");
        if (message == null || message.trim().isEmpty()) {
            logger.warn("用户 {} 尝试发送空消息", username);
            return;
        }

        // 发送大厅消息
        webSocketService.sendLobbyMessage(username, message);
        logger.debug("用户 {} 发送大厅消息: {}", username, message);
    }
}