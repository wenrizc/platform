package com.platform.service;

import com.platform.entity.Room;
import com.platform.enums.MessageTarget;
import com.platform.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息服务
 * 负责管理和发送大厅和房间消息，以及系统通知
 */
@Service
public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    // 消息历史容量限制
    private static final int MAX_ROOM_HISTORY_SIZE = 100;  // 房间消息上限
    private static final int MAX_LOBBY_HISTORY_SIZE = 500; // 大厅消息上限

    // 存储消息历史
    private final List<Map<String, Object>> lobbyMessageHistory = Collections.synchronizedList(new ArrayList<>());
    private final Map<Long, List<Map<String, Object>>> roomMessageHistory = new ConcurrentHashMap<>();

    private final WebSocketService webSocketService;
    private final RoomRepository roomRepository;

    @Autowired
    public MessageService(WebSocketService webSocketService, RoomRepository roomRepository) {
        this.webSocketService = webSocketService;
        this.roomRepository = roomRepository;
    }

    /**
     * 发送大厅消息
     *
     * @param senderUsername 发送者用户名
     * @param message        消息内容
     */
    public void sendLobbyMessage(String senderUsername, String message) {
        try {
            // 构建消息
            Map<String, Object> chatMessage = createMessageData(senderUsername, message, "LOBBY_MESSAGE");

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
     * 添加大厅消息到历史
     *
     * @param sender  发送者
     * @param message 消息内容
     */
    public void addLobbyMessage(String sender, String message) {
        Map<String, Object> messageData = createMessageData(sender, message, null);

        lobbyMessageHistory.add(messageData);
        if (lobbyMessageHistory.size() > MAX_LOBBY_HISTORY_SIZE) {
            lobbyMessageHistory.remove(0);
        }

        // 定期记录消息数量
        if (lobbyMessageHistory.size() % 100 == 0) {
            logger.debug("大厅消息历史数量: {}", lobbyMessageHistory.size());

        }
    }

    /**
     * 获取大厅消息历史
     *
     * @return 大厅消息列表副本
     */
    public List<Map<String, Object>> getLobbyMessageHistory() {
        return new ArrayList<>(lobbyMessageHistory);
    }

    /**
     * 发送房间消息
     *
     * @param roomId         房间ID
     * @param senderUsername 发送者用户名
     * @param message        消息内容
     */
    public void sendRoomMessage(Long roomId, String senderUsername, String message) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room != null && room.containsPlayer(senderUsername)) {
            // 构建消息
            Map<String, Object> chatMessage = createMessageData(senderUsername, message, null);
            chatMessage.put("roomId", roomId);

            // 保存消息历史
            addRoomMessage(roomId, senderUsername, message);

            // 发送到房间特定频道
            String destination = "/topic/room." + roomId + ".messages";
            webSocketService.broadcastMessage(destination, chatMessage);

            logger.debug("用户 {} 在房间 {} 发送消息: {}", senderUsername, roomId, message);
        }
    }

    /**
     * 添加房间消息到历史
     *
     * @param roomId  房间ID
     * @param sender  发送者
     * @param message 消息内容
     */
    public void addRoomMessage(Long roomId, String sender, String message) {
        Map<String, Object> messageData = createMessageData(sender, message, null);

        roomMessageHistory.computeIfAbsent(roomId, k -> Collections.synchronizedList(new ArrayList<>()));
        List<Map<String, Object>> messages = roomMessageHistory.get(roomId);

        messages.add(messageData);
        if (messages.size() > MAX_ROOM_HISTORY_SIZE) {
            messages.remove(0);
        }

        // 定期记录消息数量
        if (messages.size() % 20 == 0) {
            logger.debug("房间 {} 的消息历史数量: {}", roomId, messages.size());

        }
    }

    /**
     * 获取房间消息历史
     *
     * @param roomId 房间ID
     * @return 房间消息列表副本，如无消息则返回空列表
     */
    public List<Map<String, Object>> getRoomMessageHistory(Long roomId) {
        List<Map<String, Object>> messages = roomMessageHistory.get(roomId);
        if (messages == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(messages);
    }

    /**
     * 清除房间消息历史（房间删除时调用）
     *
     * @param roomId 房间ID
     */
    public void clearRoomMessageHistory(Long roomId) {
        List<Map<String, Object>> removedMessages = roomMessageHistory.remove(roomId);
        if (removedMessages != null) {
            logger.debug("已清除房间 {} 的 {} 条消息历史", roomId, removedMessages.size());
        }
    }

    /**
     * 发送系统消息
     *
     * @param target  消息目标类型 (LOBBY, ROOM, ALL)
     * @param roomId  如果是房间消息，则提供房间ID；否则可为null
     * @param message 消息内容
     */
    public void sendSystemMessage(MessageTarget target, Long roomId, String message) {
        try {
            // 构建系统消息
            Map<String, Object> systemMessage = createMessageData("系统", message, "SYSTEM_MESSAGE");

            switch (target) {
                case LOBBY:
                    // 保存到大厅历史并发送
                    addLobbyMessage("系统", message);
                    webSocketService.broadcastMessage("/topic/lobby.messages", systemMessage);
                    logger.info("系统消息已发送到大厅: {}", message);
                    break;

                case ROOM:
                    if (roomId == null) {
                        logger.error("发送房间系统消息时未提供房间ID");
                        return;
                    }

                    Room room = roomRepository.findById(roomId).orElse(null);
                    if (room == null) {
                        logger.warn("尝试发送系统消息到不存在的房间: {}", roomId);
                        return;
                    }

                    // 保存到房间历史并发送
                    addRoomMessage(roomId, "系统", message);
                    systemMessage.put("roomId", roomId);
                    webSocketService.broadcastMessage("/topic/room." + roomId + ".messages", systemMessage);
                    logger.info("系统消息已发送到房间 {}: {}", roomId, message);
                    break;

                case ALL:
                    // 保存到大厅历史并全局广播
                    addLobbyMessage("系统", message);
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
     * 创建基础消息数据结构
     *
     * @param sender  发送者
     * @param message 消息内容
     * @param type    消息类型(可选)
     * @return 消息数据Map
     */
    private Map<String, Object> createMessageData(String sender, String message, String type) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("sender", sender);
        messageData.put("message", message);
        messageData.put("timestamp", System.currentTimeMillis());
        if (type != null) {
            messageData.put("type", type);
        }
        return messageData;
    }
}