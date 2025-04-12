package com.platform.service;

import com.platform.entity.Room;
import com.platform.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    // 存储大厅消息历史
    private final List<Map<String, Object>> lobbyMessageHistory = Collections.synchronizedList(new ArrayList<>());

    // 存储房间消息历史，key为roomId
    private final Map<Long, List<Map<String, Object>>> roomMessageHistory = new ConcurrentHashMap<>();

    // 最大历史消息数量
    private static final int MAX_ROOM_HISTORY_SIZE = 100;  // 房间消息上限
    private static final int MAX_LOBBY_HISTORY_SIZE = 500; // 大厅消息上限

    private final WebSocketService webSocketService;
    private final RoomRepository roomRepository;

    @Autowired
    public MessageService(WebSocketService webSocketService, RoomRepository roomRepository) {
        this.webSocketService = webSocketService;
        this.roomRepository = roomRepository;
    }

    /**
     * 添加大厅消息到历史
     */
    public void addLobbyMessage(String sender, String message) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("sender", sender);
        messageData.put("message", message);
        messageData.put("timestamp", System.currentTimeMillis());

        synchronized(lobbyMessageHistory) {
            lobbyMessageHistory.add(messageData);
            if (lobbyMessageHistory.size() > MAX_LOBBY_HISTORY_SIZE) {
                lobbyMessageHistory.remove(0);
            }
        }

        // 记录当前消息历史数量
        if (lobbyMessageHistory.size() % 100 == 0) {
            logger.debug("大厅消息历史数量: {}", lobbyMessageHistory.size());
        }
    }

    /**
     * 添加房间消息到历史
     */
    public void addRoomMessage(Long roomId, String sender, String message) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("sender", sender);
        messageData.put("message", message);
        messageData.put("timestamp", System.currentTimeMillis());

        roomMessageHistory.computeIfAbsent(roomId, k -> Collections.synchronizedList(new ArrayList<>()));
        List<Map<String, Object>> messages = roomMessageHistory.get(roomId);

        synchronized(messages) {
            messages.add(messageData);
            if (messages.size() > MAX_ROOM_HISTORY_SIZE) {
                messages.remove(0);
            }
        }

        // 记录当前房间的消息数量
        if (messages.size() % 20 == 0) {
            logger.debug("房间 {} 的消息历史数量: {}", roomId, messages.size());
        }
    }

    /**
     * 获取大厅消息历史
     */
    public List<Map<String, Object>> getLobbyMessageHistory() {
        synchronized(lobbyMessageHistory) {
            return new ArrayList<>(lobbyMessageHistory);
        }
    }

    /**
     * 获取房间消息历史
     */
    public List<Map<String, Object>> getRoomMessageHistory(Long roomId) {
        List<Map<String, Object>> messages = roomMessageHistory.get(roomId);
        if (messages == null) {
            return new ArrayList<>();
        }

        synchronized(messages) {
            return new ArrayList<>(messages);
        }
    }

    /**
     * 清除房间消息历史（房间删除时调用）
     */
    public void clearRoomMessageHistory(Long roomId) {
        List<Map<String, Object>> removedMessages = roomMessageHistory.remove(roomId);
        if (removedMessages != null) {
            logger.debug("已清除房间 {} 的 {} 条消息历史", roomId, removedMessages.size());
        }
    }

    /**
     * 发送房间消息
     */
    public void sendRoomMessage(Long roomId, String senderUsername, String message) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room != null && room.containsPlayer(senderUsername)) {
            // 构建消息
            Map<String, Object> chatMessage = new HashMap<>();
            chatMessage.put("roomId", roomId);
            chatMessage.put("sender", senderUsername);
            chatMessage.put("message", message);
            chatMessage.put("timestamp", System.currentTimeMillis());

            // 保存消息历史
            addRoomMessage(roomId, senderUsername, message);

            // 发送到房间特定频道
            String destination = "/topic/room." + roomId + ".messages";
            webSocketService.broadcastMessage(destination, chatMessage);

            logger.debug("用户 {} 在房间 {} 发送消息: {}", senderUsername, roomId, message);
        }
    }

    /**
     * 发送大厅消息
     */
    public void sendLobbyMessage(String senderUsername, String message) {
        try {
            // 构建消息
            Map<String, Object> chatMessage = new HashMap<>();
            chatMessage.put("sender", senderUsername);
            chatMessage.put("message", message);
            chatMessage.put("timestamp", System.currentTimeMillis());
            chatMessage.put("type", "LOBBY_MESSAGE");

            // 保存消息历史
            addLobbyMessage(senderUsername, message);

            // 广播消息
            webSocketService.broadcastMessage("/topic/lobby.messages", chatMessage);
            logger.info("大厅消息已广播: {} - {}", senderUsername, message);
        } catch (Exception e) {
            logger.error("发送大厅消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送系统消息
     * @param target 消息目标类型 (LOBBY, ROOM, ALL)
     * @param roomId 如果是房间消息，则提供房间ID；否则可为null
     * @param message 消息内容
     */
    public void sendSystemMessage(MessageTarget target, Long roomId, String message) {
        try {
            // 构建基本消息
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("sender", "系统");
            systemMessage.put("message", message);
            systemMessage.put("timestamp", System.currentTimeMillis());
            systemMessage.put("type", "SYSTEM_MESSAGE");

            switch (target) {
                case LOBBY:
                    // 保存到大厅历史
                    addLobbyMessage("系统", message);
                    // 发送到大厅
                    webSocketService.broadcastMessage("/topic/lobby.messages", systemMessage);
                    logger.info("系统消息已发送到大厅: {}", message);
                    break;

                case ROOM:
                    if (roomId != null) {
                        Room room = roomRepository.findById(roomId).orElse(null);
                        if (room != null) {
                            // 保存到房间历史
                            addRoomMessage(roomId, "系统", message);
                            // 发送到指定房间
                            systemMessage.put("roomId", roomId);
                            String destination = "/topic/room." + roomId + ".messages";
                            webSocketService.broadcastMessage(destination, systemMessage);
                            logger.info("系统消息已发送到房间 {}: {}", roomId, message);
                        } else {
                            logger.warn("尝试发送系统消息到不存在的房间: {}", roomId);
                        }
                    } else {
                        logger.error("发送房间系统消息时未提供房间ID");
                    }
                    break;

                case ALL:
                    // 保存到大厅历史
                    addLobbyMessage("系统", message);
                    // 广播到所有用户
                    webSocketService.broadcastMessage("/topic/system.notifications", systemMessage);
                    logger.info("系统全局通知已广播: {}", message);
                    break;

                default:
                    logger.error("未知的消息目标类型: {}", target);
            }
        } catch (Exception e) {
            logger.error("发送系统消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 消息目标枚举
     */
    public enum MessageTarget {
        LOBBY,  // 大厅消息
        ROOM,   // 房间消息
        ALL     // 全局通知
    }
}