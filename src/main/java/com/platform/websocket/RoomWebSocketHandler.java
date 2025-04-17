package com.platform.websocket;

import com.platform.entity.Room;
import com.platform.service.MessageService;
import com.platform.service.RoomService;
import com.platform.service.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

@Controller
public class RoomWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    private final RoomService roomService;
    private final MessageService messageService;
    private final WebSocketService webSocketService;

    @Autowired
    public RoomWebSocketHandler(RoomService roomService, MessageService messageService, WebSocketService webSocketService) {
        this.roomService = roomService;
        this.messageService = messageService;
        this.webSocketService = webSocketService;
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
        String gameName = (String) payload.get("gameName");
        Integer maxPlayers = (Integer) payload.get("maxPlayers");

        if (roomName != null && gameName != null && maxPlayers != null) {
            // 检查房间名是否已存在
            if (roomService.isRoomNameExists(roomName)) {
                // 发送错误消息给客户端
                Map<String, Object> errorMessage = new HashMap<>();
                errorMessage.put("error", "房间名已存在，请使用其他名称");
                webSocketService.sendMessageToUser(username, "/queue/errors", errorMessage);
                return;
            }
            Room room = roomService.createRoom(username, roomName, gameName, maxPlayers);

            // 添加：发送创建成功消息
            if (room != null) {
                Map<String, Object> successMessage = new HashMap<>();
                successMessage.put("type", "ROOM_CREATED");
                successMessage.put("room", room);
                webSocketService.sendMessageToUser(username, "/queue/messages", successMessage);
            }
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
     * 请求开始游戏
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
     * 请求结束游戏
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
}