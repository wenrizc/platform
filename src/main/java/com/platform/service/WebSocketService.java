package com.platform.service;

import com.platform.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket服务
 * <p>
 * 负责处理WebSocket消息的发送，包括用户间直接消息、广播消息、
 * 状态更新通知、系统通知和心跳响应等
 * </p>
 */
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
     *
     * @param username 目标用户名
     * @param destination 目标路径
     * @param payload 消息内容
     */
    public void sendMessageToUser(String username, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(username, destination, payload);
        logger.debug("发送消息给用户 {}: {}", username, payload);
    }

    /**
     * 广播消息
     *
     * @param destination 目标路径
     * @param payload 消息内容
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
     * 广播用户上线/下线状态
     *
     * @param user 发生状态变化的用户
     * @param online 是否在线
     */
    public void sendUserStatusUpdate(User user, boolean online) {
        Map<String, Object> status = createBaseMessage();
        status.put("username", user.getUsername());
        status.put("online", online);

        broadcastMessage("/topic/users.status", status);
    }

    /**
     * 发送心跳响应
     * 回应客户端的心跳请求，维持连接活跃
     *
     * @param username 目标用户名
     */
    public void sendHeartbeatResponse(String username) {
        Map<String, Object> response = createBaseMessage();
        response.put("type", "heartbeat");

        sendMessageToUser(username, "/queue/heartbeat", response);
    }

    /**
     * 发送系统通知消息
     * 广播系统级别通知给所有用户
     *
     * @param message 通知内容
     */
    public void sendSystemNotification(String message) {
        Map<String, Object> notification = createBaseMessage();
        notification.put("sender", "系统");
        notification.put("message", message);
        notification.put("type", "SYSTEM_NOTIFICATION");

        broadcastMessage("/topic/system.notifications", notification);
        logger.debug("发送系统通知: {}", message);
    }

    /**
     * 创建基础消息对象
     * 所有消息共享的基础字段
     *
     * @return 包含时间戳的基础消息Map
     */
    private Map<String, Object> createBaseMessage() {
        Map<String, Object> message = new HashMap<>();
        message.put("timestamp", System.currentTimeMillis());
        return message;
    }
}