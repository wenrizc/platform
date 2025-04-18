package com.platform.controller;

import com.platform.entity.Room;
import com.platform.entity.User;
import com.platform.service.RoomService;
import com.platform.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 房间控制器
 * 处理游戏房间的创建、加入、退出及游戏状态管理
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final UserService userService;

    @Autowired
    public RoomController(RoomService roomService, UserService userService) {
        this.roomService = roomService;
        this.userService = userService;
    }

    /**
     * 获取可加入的房间列表
     */
    @GetMapping
    public ResponseEntity<List<Room>> getJoinableRooms() {
        return ResponseEntity.ok(roomService.getJoinableRooms());
    }

    /**
     * 获取用户当前所在房间
     */
    @GetMapping("/my-room")
    public ResponseEntity<?> getUserRoom(HttpSession session) {
        String username = getUsernameFromSession(session);
        if (username == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录"));
        }

        Room room = roomService.getUserRoom(username);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(createErrorResponse("用户不在任何房间中"));
        }

        return ResponseEntity.ok(room);
    }

    /**
     * 获取指定房间详情
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoomInfo(@PathVariable Long roomId) {
        Room room = roomService.getRoomInfo(roomId);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(createErrorResponse("房间不存在"));
        }

        return ResponseEntity.ok(room);
    }

    /**
     * 创建新房间
     */
    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody Map<String, Object> request, HttpSession session) {
        String username = getUsernameFromSession(session);
        if (username == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录"));
        }

        String roomName = (String) request.get("roomName");
        String gameName = (String) request.get("gameName");
        Integer maxPlayers = (Integer) request.get("maxPlayers");

        if (roomName == null || gameName == null || maxPlayers == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("缺少必要参数"));
        }

        if (roomService.isRoomNameExists(roomName)) {
            return ResponseEntity.badRequest().body(createErrorResponse("房间名已存在，请使用其他名称"));
        }

        Room room = roomService.createRoom(username, roomName, gameName, maxPlayers);
        if (room == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("创建房间失败"));
        }

        return ResponseEntity.ok(room);
    }

    /**
     * 加入房间
     */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable Long roomId, HttpSession session) {
        String username = getUsernameFromSession(session);
        if (username == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录"));
        }

        if (roomId == null || roomId <= 0) {
            return ResponseEntity.badRequest().body(createErrorResponse("无效的房间ID"));
        }

        boolean joined = roomService.joinRoom(username, roomId);
        if (!joined) {
            return ResponseEntity.badRequest().body(createErrorResponse("加入房间失败"));
        }

        Room room = roomService.getRoomInfo(roomId);
        return ResponseEntity.ok(room);
    }

    /**
     * 退出房间
     */
    @PostMapping("/leave")
    public ResponseEntity<?> leaveRoom(HttpSession session) {
        String username = getUsernameFromSession(session);
        if (username == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录"));
        }

        boolean left = roomService.leaveRoom(username);
        if (!left) {
            return ResponseEntity.badRequest().body(createErrorResponse("退出房间失败"));
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "成功退出房间");
        return ResponseEntity.ok(response);
    }

    /**
     * 开始游戏
     */
    @PostMapping("/{roomId}/start")
    public ResponseEntity<?> startGame(@PathVariable Long roomId, HttpSession session) {
        String username = getUsernameFromSession(session);
        if (username == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录"));
        }

        boolean started = roomService.startGame(username, roomId);
        if (!started) {
            return ResponseEntity.badRequest().body(createErrorResponse("开始游戏失败"));
        }

        Room room = roomService.getRoomInfo(roomId);
        return ResponseEntity.ok(room);
    }

    /**
     * 结束游戏
     */
    @PostMapping("/{roomId}/end")
    public ResponseEntity<?> endGame(@PathVariable Long roomId, HttpSession session) {
        String username = getUsernameFromSession(session);
        if (username == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录"));
        }

        boolean ended = roomService.endGame(username, roomId);
        if (!ended) {
            return ResponseEntity.badRequest().body(createErrorResponse("结束游戏失败"));
        }

        Room room = roomService.getRoomInfo(roomId);
        return ResponseEntity.ok(room);
    }

    /**
     * 从session中获取用户名
     */
    private String getUsernameFromSession(HttpSession session) {
        if (session == null) return null;

        User user = userService.findBySessionId(session.getId());
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
}