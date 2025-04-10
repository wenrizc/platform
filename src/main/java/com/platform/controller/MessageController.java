package com.platform.controller;

import com.platform.entity.Room;
import com.platform.service.MessageService;
import com.platform.service.RoomService;
import com.platform.service.UserService;
import com.platform.service.WebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final WebSocketService webSocketService;
    private final RoomService roomService;
    private final UserService userService;
    private final MessageService messageService;

    @Autowired
    public MessageController(WebSocketService webSocketService, RoomService roomService, UserService userService, MessageService messageService) {
        this.webSocketService = webSocketService;
        this.roomService = roomService;
        this.userService = userService;
        this.messageService = messageService;
    }

    /**
     * 发送大厅消息
     */
    @PostMapping("/lobby")
    public ResponseEntity<?> sendLobbyMessage(@RequestBody Map<String, String> request, HttpSession session) {
        String username = getUsernameFromSession(session);
        if (username == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录"));
        }

        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("消息内容不能为空"));
        }

        // 发送大厅消息
        webSocketService.sendLobbyMessage(username, message);

        Map<String, String> response = new HashMap<>();
        response.put("success", "消息已发送");
        return ResponseEntity.ok(response);
    }

    /**
     * 发送房间消息
     */
    @PostMapping("/room/{roomId}")
    public ResponseEntity<?> sendRoomMessage(@PathVariable Long roomId,
                                             @RequestBody Map<String, String> request,
                                             HttpSession session) {
        String username = getUsernameFromSession(session);
        if (username == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录"));
        }

        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(createErrorResponse("消息内容不能为空"));
        }

        // 检查用户是否在房间内
        Room room = roomService.getUserRoom(username);
        if (room == null || !room.getId().equals(roomId)) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户不在该房间内"));
        }

        // 发送房间消息
        roomService.sendRoomMessage(roomId, username, message);

        Map<String, String> response = new HashMap<>();
        response.put("success", "消息已发送");
        return ResponseEntity.ok(response);
    }

    /**
     * 从session中获取用户名
     */
    private String getUsernameFromSession(HttpSession session) {
        if (session == null) return null;

        com.platform.entity.User user = userService.findBySessionId(session.getId());
        return user != null && user.isActive() ? user.getUsername() : null;
    }

    /**
     * 创建错误响应
     */
    private Map<String, String> createErrorResponse(String errorMessage) {
        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);
        return response;
    }

    /**
     * 获取大厅消息历史
     */
    @GetMapping("/lobby/history")
    public ResponseEntity<?> getLobbyMessageHistory(HttpSession session) {
        String username = getUsernameFromSession(session);
        if (username == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录"));
        }

        return ResponseEntity.ok(messageService.getLobbyMessageHistory());
    }

    /**
     * 获取房间消息历史
     */
    @GetMapping("/room/{roomId}/history")
    public ResponseEntity<?> getRoomMessageHistory(@PathVariable Long roomId, HttpSession session) {
        String username = getUsernameFromSession(session);
        if (username == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录"));
        }

        // 检查用户是否在房间内
        Room room = roomService.getUserRoom(username);
        if (room == null || !room.getId().equals(roomId)) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户不在该房间内"));
        }

        return ResponseEntity.ok(messageService.getRoomMessageHistory(roomId));
    }
}