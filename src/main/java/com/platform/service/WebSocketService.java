package com.platform.service;

import com.platform.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class WebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketService.class);
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 发送消息给特定用户
     */
    public void sendMessageToUser(String username, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(username, destination, payload);
        logger.debug("发送消息给用户 {}: {}", username, payload);
    }

    /**
     * 广播消息给所有用户
     */
    public void broadcastMessage(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
            logger.debug("消息已广播到 {}: {}", destination, payload);
        } catch (Exception e) {
            logger.error("广播消息到 {} 失败: {}", destination, e.getMessage(), e);
        }
    }

    /**
     * 发送用户状态更新通知
     */
    public void sendUserStatusUpdate(User user, boolean online) {
        Map<String, Object> status = new HashMap<>();
        status.put("username", user.getUsername());
        status.put("online", online);
        status.put("timestamp", System.currentTimeMillis());

        broadcastMessage("/topic/users.status", status);
    }

    /**
     * 发送心跳响应
     */
    public void sendHeartbeatResponse(String username) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", "heartbeat");
        response.put("timestamp", System.currentTimeMillis());

        sendMessageToUser(username, "/queue/heartbeat", response);
    }

    /**
     * 发送系统通知消息
     */
    public void sendSystemNotification(String message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("sender", "系统");
        notification.put("message", message);
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("type", "SYSTEM_NOTIFICATION");

        broadcastMessage("/topic/system.notifications", notification);
        logger.debug("发送系统通知: {}", message);
    }
}