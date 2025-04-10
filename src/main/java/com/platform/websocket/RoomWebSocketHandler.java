package com.platform.websocket;

import com.platform.entity.Room;
import com.platform.service.RoomService;
import com.platform.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class RoomWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    private final RoomService roomService;
    private final UserService userService;

    @Autowired
    public RoomWebSocketHandler(RoomService roomService, UserService userService) {
        this.roomService = roomService;
        this.userService = userService;
    }

    /**
     * 用户请求创建房间
     */
    @MessageMapping("/room.create")
    public void handleRoomCreate(@Payload Map<String, Object> payload,
                                 SimpMessageHeaderAccessor headerAccessor) {
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if (username == null) {
            logger.warn("未授权的房间创建请求");
            return;
        }

        String roomName = (String) payload.get("roomName");
        String gameType = (String) payload.get("gameType");
        Integer maxPlayers = (Integer) payload.get("maxPlayers");

        if (roomName != null && gameType != null && maxPlayers != null) {
            roomService.createRoom(username, roomName, gameType, maxPlayers);
        } else {
            logger.warn("房间创建请求缺少必要参数");
        }
    }

    /**
     * 用户请求加入房间
     */
    @MessageMapping("/room.join")
    public void handleRoomJoin(@Payload Map<String, Object> payload,
                               SimpMessageHeaderAccessor headerAccessor) {
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if (username == null) {
            logger.warn("未授权的房间加入请求");
            return;
        }

        Long roomId = ((Number) payload.get("roomId")).longValue();
        roomService.joinRoom(username, roomId);
    }

    /**
     * 用户请求离开房间
     */
    @MessageMapping("/room.leave")
    public void handleRoomLeave(SimpMessageHeaderAccessor headerAccessor) {
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if (username == null) {
            logger.warn("未授权的房间离开请求");
            return;
        }

        roomService.leaveRoom(username);
    }

    /**
     * 房主请求开始游戏
     */
    @MessageMapping("/room.start")
    public void handleGameStart(@Payload Map<String, Object> payload,
                                SimpMessageHeaderAccessor headerAccessor) {
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if (username == null) {
            logger.warn("未授权的游戏开始请求");
            return;
        }

        Long roomId = ((Number) payload.get("roomId")).longValue();
        roomService.startGame(username, roomId);
    }

    /**
     * 房主请求结束游戏
     */
    @MessageMapping("/room.end")
    public void handleGameEnd(@Payload Map<String, Object> payload,
                              SimpMessageHeaderAccessor headerAccessor) {
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if (username == null) {
            logger.warn("未授权的游戏结束请求");
            return;
        }

        Long roomId = ((Number) payload.get("roomId")).longValue();
        roomService.endGame(username, roomId);
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
            roomService.sendRoomMessage(roomId, username, message);
        } else {
            logger.warn("用户 {} 试图发送消息到不属于他的房间 {}", username, roomId);
        }
    }
}