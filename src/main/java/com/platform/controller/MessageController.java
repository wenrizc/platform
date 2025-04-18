package com.platform.controller;

import com.platform.entity.Room;
import com.platform.entity.User;
import com.platform.service.MessageService;
import com.platform.service.RoomService;
import com.platform.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息控制器
 * <p>
 * 处理大厅消息和房间消息的发送与历史记录查询
 * </p>
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final RoomService roomService;
    private final UserService userService;
    private final MessageService messageService;

    @Autowired
    public MessageController(RoomService roomService, UserService userService, MessageService messageService) {
        this.roomService = roomService;
        this.userService = userService;
        this.messageService = messageService;
    }

    /**
     * 发送大厅消息
     *
     * @param request 包含消息内容的请求体
     * @param session 用户会话
     * @return 发送结果
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

        messageService.sendLobbyMessage(username, message);

        Map<String, String> response = new HashMap<>();
        response.put("success", "消息已发送");
        return ResponseEntity.ok(response);
    }

    /**
     * 获取大厅消息历史
     *
     * @param session 用户会话
     * @return 大厅消息历史记录
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
     * 发送房间消息
     *
     * @param roomId 房间ID
     * @param request 包含消息内容的请求体
     * @param session 用户会话
     * @return 发送结果
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

        messageService.sendRoomMessage(roomId, username, message);

        Map<String, String> response = new HashMap<>();
        response.put("success", "消息已发送");
        return ResponseEntity.ok(response);
    }

    /**
     * 获取房间消息历史
     *
     * @param roomId 房间ID
     * @param session 用户会话
     * @return 房间消息历史记录
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

    /**
     * 从会话中获取用户名
     *
     * @param session HTTP会话
     * @return 已登录且活跃的用户名，如果未登录则返回null
     */
    private String getUsernameFromSession(HttpSession session) {
        if (session == null) return null;

        User user = userService.findBySessionId(session.getId());
        return user != null && user.isActive() ? user.getUsername() : null;
    }

    /**
     * 创建统一错误响应
     *
     * @param errorMessage 错误信息
     * @return 包含错误信息的Map
     */
    private Map<String, String> createErrorResponse(String errorMessage) {
        Map<String, String> response = new HashMap<>();
        response.put("error", errorMessage);
        return response;
    }
}