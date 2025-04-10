package com.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
}