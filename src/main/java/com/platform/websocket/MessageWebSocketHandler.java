package com.platform.websocket;

import com.platform.entity.Room;
import com.platform.service.MessageService;
import com.platform.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class MessageWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(MessageWebSocketHandler.class);

    private final MessageService messageService;
    private final RoomService roomService;

    @Autowired
    public MessageWebSocketHandler(MessageService messageService, RoomService roomService) {
        this.messageService = messageService;
        this.roomService = roomService;
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
        logger.info("用户 {} 发送大厅消息: {}", username, message);
        messageService.sendLobbyMessage(username, message);
    }

    /**
     * 房间内玩家发送消息
     */
    @MessageMapping("/room.message")
    public void handleRoomMessage(@Payload Map<String, Object> payload,
                                  SimpMessageHeaderAccessor headerAccessor) {
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if (username == null) {
            logger.warn("未授权的房间消息发送");
            return;
        }

        Long roomId = ((Number) payload.get("roomId")).longValue();
        String message = (String) payload.get("message");

        if (message == null || message.trim().isEmpty()) {
            logger.warn("用户 {} 尝试发送空消息", username);
            return;
        }

        // 验证用户在房间中
        Room room = roomService.getUserRoom(username);
        if (room != null && room.getId().equals(roomId)) {
            messageService.sendRoomMessage(roomId, username, message);
        } else {
            logger.warn("用户 {} 试图发送消息到不属于他的房间 {}", username, roomId);
        }
    }
}