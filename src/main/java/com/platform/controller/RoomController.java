package com.platform.controller;

import com.platform.entity.Room;
import com.platform.service.RoomService;
import com.platform.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * 创建新房间
     */
    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody Map<String, Object> request,
                                        HttpSession session) {
        String username = getUsernameFromSession(session);
        if (username == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录"));
        }

        String roomName = (String) request.get("roomName");
        String gameType = (String) request.get("gameType");
        Integer maxPlayers = (Integer) request.get("maxPlayers");

        if (roomName == null || gameType == null || maxPlayers == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("缺少必要参数"));
        }

        Room room = roomService.createRoom(username, roomName, gameType, maxPlayers);
        if (room == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("创建房间失败"));
        }

        return ResponseEntity.ok(room);
    }

    /**
     * 加入房间
     */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable Long roomId,
                                      HttpSession session) {
        String username = getUsernameFromSession(session);
        if (username == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户未登录"));
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
    public ResponseEntity<?> startGame(@PathVariable Long roomId,
                                       HttpSession session) {
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
    public ResponseEntity<?> endGame(@PathVariable Long roomId,
                                     HttpSession session) {
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
     * 获取可加入的房间列表
     */
    @GetMapping
    public ResponseEntity<List<Room>> getJoinableRooms() {
        return ResponseEntity.ok(roomService.getJoinableRooms());
    }

    /**
     * 按游戏类型筛选可加入的房间
     */
    @GetMapping("/by-game/{gameType}")
    public ResponseEntity<List<Room>> getJoinableRoomsByGameType(@PathVariable String gameType) {
        return ResponseEntity.ok(roomService.getJoinableRoomsByGameType(gameType));
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
            return ResponseEntity.status(404).body(createErrorResponse("用户不在任何房间中"));
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
            return ResponseEntity.status(404).body(createErrorResponse("房间不存在"));
        }

        return ResponseEntity.ok(room);
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
}