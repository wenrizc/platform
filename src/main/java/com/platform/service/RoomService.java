package com.platform.service;

import com.platform.entity.Room;
import com.platform.entity.User;
import com.platform.enums.MessageTarget;
import com.platform.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 房间服务
 * 处理游戏房间的创建、加入、退出、游戏状态管理以及房间清理等操作
 */
@Service
public class RoomService {

    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);

    private final RoomRepository roomRepository;
    private final UserService userService;
    private final WebSocketService webSocketService;
    private final MessageService messageService;
    private final VirtualNetworkService networkService;

    @Value("${virtual.network.default:N2N}")
    private String networkType;

    @Autowired
    public RoomService(RoomRepository roomRepository, UserService userService, WebSocketService webSocketService,
                       MessageService messageService, VirtualNetworkFactory networkFactory) {
        this.roomRepository = roomRepository;
        this.userService = userService;
        this.webSocketService = webSocketService;
        this.messageService = messageService;
        this.networkService = networkFactory.getService(networkType);
    }

    /**
     * 创建新房间
     *
     * @param username 创建者用户名
     * @param roomName 房间名称
     * @param gameName 游戏名称
     * @param maxPlayers 最大玩家数
     * @return 创建的房间对象，创建失败返回null
     */
    public Room createRoom(String username, String roomName, String gameName, int maxPlayers) {
        // 用户验证
        User user = userService.findByUsername(username);
        if (user == null || !userService.isUserActive(user)) {
            logger.warn("用户 {} 尝试创建房间但未登录或不活跃", username);
            return null;
        }

        // 检查用户是否已在房间中
        if (user.getRoomId() != 0 || !roomRepository.findByPlayerUsername(username).isEmpty()) {
            logger.warn("用户 {} 已经在房间中，无法创建新房间", username);
            return null;
        }

        // 检查房间名是否已存在
        if (isRoomNameExists(roomName)) {
            logger.warn("用户 {} 尝试创建的房间名 {} 已存在", username, roomName);
            return null;
        }

        // 创建新房间
        Room room = new Room(roomName, gameName, maxPlayers, username);
        Room savedRoom = roomRepository.save(room);

        // 创建虚拟网络并配置
        String networkId = networkService.createNetwork();
        String networkName = "room_" + savedRoom.getId();
        String networkSecret = networkService.generateNetworkSecret();

        savedRoom.setNetworkId(networkId);
        savedRoom.setNetworkName(networkName);
        savedRoom.setNetworkSecret(networkSecret);
        savedRoom.setNetworkType(networkService.getTechnologyName());
        savedRoom.addPlayer(username);

        // 为创建者分配虚拟IP
        try {
            String virtualIp = networkService.assignIpAddress(username, networkId);
            user.setVirtualIp(virtualIp);
            user.setRoomId(savedRoom.getId());
            userService.updateUser(user);
            logger.info("为房主 {} 分配虚拟IP: {}", username, virtualIp);
        } catch (Exception e) {
            logger.error("为房主 {} 分配虚拟IP时出错: {}", username, e.getMessage(), e);
        }

        // 保存并广播房间创建消息
        savedRoom = roomRepository.save(savedRoom);
        broadcastRoomUpdate(savedRoom, "CREATED", username);

        logger.info("用户 {} 创建了房间: {}, 虚拟网络ID: {}", username, roomName, networkId);
        return savedRoom;
    }

    /**
     * 加入房间
     *
     * @param username 用户名
     * @param roomId 要加入的房间ID
     * @return 加入成功返回true，否则返回false
     */
    public boolean joinRoom(String username, Long roomId) {
        // 用户验证
        User user = userService.findByUsername(username);
        if (user == null || !userService.isUserActive(user)) {
            logger.warn("用户 {} 尝试加入房间但未登录或不活跃", username);
            return false;
        }

        // 检查用户是否已在房间中
        if (!roomRepository.findByPlayerUsername(username).isEmpty()) {
            logger.warn("用户 {} 已在其他房间中，无法加入新房间", username);
            return false;
        }

        // 检查房间状态
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            logger.warn("用户 {} 尝试加入不存在的房间: {}", username, roomId);
            return false;
        }

        if (room.getStatus() != Room.RoomStatus.WAITING) {
            logger.warn("用户 {} 尝试加入非等待状态的房间: {}", username, roomId);
            return false;
        }

        if (room.isFull()) {
            logger.warn("用户 {} 尝试加入已满的房间: {}", username, roomId);
            return false;
        }

        // 将用户添加到房间
        room.addPlayer(username);

        // 为用户分配虚拟IP
        try {
            String virtualIp = networkService.assignIpAddress(username, room.getNetworkId());
            user.setVirtualIp(virtualIp);
            user.setRoomId(roomId);
            userService.updateUser(user);
            logger.info("为用户 {} 分配虚拟IP: {}", username, virtualIp);
        } catch (Exception e) {
            logger.error("为用户 {} 分配虚拟IP时出错: {}", username, e.getMessage(), e);
        }

        // 保存房间状态并广播更新
        roomRepository.save(room);
        broadcastRoomUpdate(room, "JOINED", username);

        logger.info("用户 {} 加入了房间: {}", username, roomId);
        return true;
    }

    /**
     * 用户离开房间
     *
     * @param username 用户名
     * @return 离开成功返回true，否则返回false
     */
    public boolean leaveRoom(String username) {
        // 验证用户并获取所在房间
        User user = userService.findByUsername(username);
        if (user == null) {
            logger.warn("尝试让不存在的用户 {} 离开房间", username);
            return false;
        }

        List<Room> userRooms = roomRepository.findByPlayerUsername(username);
        if (userRooms.isEmpty()) {
            logger.warn("用户 {} 不在任何房间中", username);
            return false;
        }

        Room room = userRooms.get(0);
        room.removePlayer(username);

        // 清理用户的虚拟网络资源
        cleanupUserNetworkResources(user, room.getNetworkId());

        // 处理房间状态
        if (room.isEmpty()) {
            handleEmptyRoom(room);
        } else {
            handleRoomAfterUserLeave(room, username);
        }

        // 广播用户离开消息
        broadcastRoomUpdate(room, "LEFT", username);
        logger.info("用户 {} 离开了房间: {}", username, room.getId());
        return true;
    }

    /**
     * 用户离开房间 (通过用户ID)
     *
     * @param userId 用户ID
     * @param roomId 房间ID
     * @return 操作结果
     */
    public boolean leaveRoom(Long userId, Long roomId) {
        User user = userService.findById(userId);
        if (user == null) {
            logger.warn("尝试让不存在的用户ID {} 离开房间", userId);
            return false;
        }

        if (user.getRoomId() != roomId) {
            logger.warn("用户 {} 不在指定房间 {} 中", user.getUsername(), roomId);
            return false;
        }

        return leaveRoom(user.getUsername());
    }

    /**
     * 开始游戏
     *
     * @param username 发起开始的用户名
     * @param roomId 房间ID
     * @return 开始成功返回true，否则返回false
     */
    public boolean startGame(String username, Long roomId) {
        // 验证房间和用户权限
        Room room = validateRoomOperation(username, roomId, Room.RoomStatus.WAITING);
        if (room == null) {
            return false;
        }

        // 验证玩家数量
        if (room.getPlayers().size() < 2) {
            logger.warn("用户 {} 尝试在玩家数量不足的房间 {} 中开始游戏", username, roomId);
            return false;
        }

        // 更新房间状态
        room.setStatus(Room.RoomStatus.PLAYING);
        roomRepository.save(room);

        // 广播游戏开始消息
        broadcastRoomUpdate(room, "STARTED", username);

        // 发送系统消息
        messageService.sendSystemMessage(
                MessageTarget.ROOM,
                roomId,
                "游戏已开始，祝大家游戏愉快！"
        );

        logger.info("房间 {} 的游戏已开始", roomId);
        return true;
    }

    /**
     * 结束游戏
     *
     * @param username 发起结束的用户名
     * @param roomId 房间ID
     * @return 结束成功返回true，否则返回false
     */
    public boolean endGame(String username, Long roomId) {
        // 验证房间和用户权限
        Room room = validateRoomOperation(username, roomId, Room.RoomStatus.PLAYING);
        if (room == null) {
            return false;
        }

        // 更新房间状态
        room.setStatus(Room.RoomStatus.WAITING);
        roomRepository.save(room);

        // 广播游戏结束消息
        broadcastRoomUpdate(room, "ENDED", username);

        // 发送系统消息
        messageService.sendSystemMessage(
                MessageTarget.ROOM,
                roomId,
                "游戏已结束，房间回到等待状态"
        );

        logger.info("房间 {} 的游戏已结束", roomId);
        return true;
    }

    /**
     * 检查房间名是否已存在
     *
     * @param roomName 房间名
     * @return 存在返回true，否则返回false
     */
    public boolean isRoomNameExists(String roomName) {
        return roomRepository.findByName(roomName).isPresent();
    }

    /**
     * 获取用户所在房间
     *
     * @param username 用户名
     * @return 用户所在房间，不在房间则返回null
     */
    public Room getUserRoom(String username) {
        List<Room> rooms = roomRepository.findByPlayerUsername(username);
        return rooms.isEmpty() ? null : rooms.get(0);
    }

    /**
     * 获取房间信息
     *
     * @param roomId 房间ID
     * @return 房间对象，不存在则返回null
     */
    public Room getRoomInfo(Long roomId) {
        return roomRepository.findById(roomId).orElse(null);
    }

    // ==================== 房间维护方法 ====================

    /**
     * 清理空房间
     * 定时任务中使用
     */
    public void cleanupEmptyRooms() {
        List<Room> emptyRooms = roomRepository.findEmptyRooms();
        if (emptyRooms.isEmpty()) {
            return;
        }

        logger.info("开始清理空房间，发现 {} 个空房间", emptyRooms.size());

        for (Room room : emptyRooms) {
            logger.debug("清理空房间: ID={}, 名称={}, 创建时间={}",
                    room.getId(), room.getName(), room.getCreationTime());

            // 清理房间相关资源
            cleanupRoomResources(room);

            // 删除房间
            deleteRoom(room.getId());
        }

        logger.info("空房间清理完成，共清理 {} 个房间", emptyRooms.size());
    }

    /**
     * 获取可加入的房间列表并清理无效房间
     * 定时任务中使用
     *
     * @return 清理后的可加入房间列表
     */
    @Transactional
    public List<Room> getJoinableRooms() {
        int offlineUsersRemoved = 0;
        int emptyRoomsRemoved = 0;
        List<Room> allRooms = roomRepository.findAll();
        List<Room> roomsToDelete = new ArrayList<>();

        // 检查所有房间
        for (Room room : allRooms) {
            boolean roomModified = false;
            Set<String> offlineUsers = new HashSet<>();

            // 检查每个房间内用户是否在线
            for (String playerUsername : new HashSet<>(room.getPlayers())) {
                User user = userService.findByUsername(playerUsername);
                if (user == null || !userService.isUserActive(user)) {
                    offlineUsers.add(playerUsername);
                }
            }

            // 从房间中移除所有离线用户
            for (String offlineUsername : offlineUsers) {
                room.removePlayer(offlineUsername);
                roomModified = true;
                offlineUsersRemoved++;
                logger.debug("从房间 {} 中移除离线用户: {}", room.getId(), offlineUsername);

                // 发送系统消息和广播更新
                messageService.sendSystemMessage(
                        MessageTarget.ROOM,
                        room.getId(),
                        "用户 " + offlineUsername + " 因长时间不活动已被系统移出房间"
                );
                broadcastRoomUpdate(room, "LEFT", offlineUsername);
            }

            // 判断房间是否为空
            if (room.isEmpty()) {
                roomsToDelete.add(room);
            } else if (roomModified) {
                roomRepository.save(room);
            }
        }

        // 删除所有空房间
        for (Room room : roomsToDelete) {
            cleanupRoomResources(room);
            deleteRoom(room.getId());
            emptyRoomsRemoved++;
        }

        // 如有清理操作，发送通知
        if (offlineUsersRemoved > 0 || emptyRoomsRemoved > 0) {
            String notification = String.format("系统自动清理: 移除了 %d 个离线用户, 删除了 %d 个空房间",
                    offlineUsersRemoved, emptyRoomsRemoved);
            logger.info(notification);
            messageService.sendSystemMessage(MessageTarget.LOBBY, null, notification);
        }

        return roomRepository.findJoinableRooms();
    }

    /**
     * 删除房间
     *
     * @param roomId 房间ID
     */
    @Transactional
    public void deleteRoom(Long roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            logger.debug("房间 {} 不存在或已被删除", roomId);
            return;
        }

        // 只删除空房间
        if (room.isEmpty()) {
            try {
                String roomName = room.getName();

                // 执行删除
                roomRepository.deleteById(roomId);

                // 发送系统消息
                messageService.sendSystemMessage(
                        MessageTarget.LOBBY,
                        null,
                        "房间 \"" + roomName + "\" 已被系统清理"
                );

                logger.info("成功删除空房间: ID={}, 名称={}", roomId, roomName);
            } catch (Exception e) {
                logger.error("删除房间 {} 时发生错误: {}", roomId, e.getMessage());
                throw e;
            }
        } else {
            logger.debug("房间 {} 不为空，跳过删除", roomId);
        }
    }

    /**
     * 广播房间状态更新消息
     */
    private void broadcastRoomUpdate(Room room, String action, String username) {
        // 构建通用广播消息
        Map<String, Object> message = new HashMap<>();
        message.put("roomId", room.getId());
        message.put("action", action);
        message.put("username", username);
        message.put("players", room.getPlayers());
        message.put("roomStatus", room.getStatus().name());
        message.put("timestamp", System.currentTimeMillis());

        // 广播给所有连接的客户端
        webSocketService.broadcastMessage("/topic/rooms.updates", message);

        // 给房间内的玩家发送详细信息
        Map<String, Object> detailMessage = new HashMap<>();
        detailMessage.put("id", room.getId());
        detailMessage.put("name", room.getName());
        detailMessage.put("gameName", room.getGameName());
        detailMessage.put("maxPlayers", room.getMaxPlayers());
        detailMessage.put("creatorUsername", room.getCreatorUsername());
        detailMessage.put("status", room.getStatus().name());
        detailMessage.put("players", room.getPlayers());
        detailMessage.put("networkId", room.getNetworkId());
        detailMessage.put("networkName", room.getNetworkName());
        detailMessage.put("networkType", room.getNetworkType());
        detailMessage.put("timestamp", System.currentTimeMillis());

        for (String player : room.getPlayers()) {
            webSocketService.sendMessageToUser(player, "/queue/room.detail", detailMessage);
        }
    }

    /**
     * 验证房间操作权限
     *
     * @param username 操作用户名
     * @param roomId 房间ID
     * @param expectedStatus 期望的房间状态
     * @return 验证成功返回房间对象，失败返回null
     */
    private Room validateRoomOperation(String username, Long roomId, Room.RoomStatus expectedStatus) {
        // 检查房间是否存在
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            logger.warn("用户 {} 尝试操作不存在的房间: {}", username, roomId);
            return null;
        }

        // 检查是否是房主
        if (!username.equals(room.getCreatorUsername())) {
            logger.warn("非房主用户 {} 尝试操作房间 {}", username, roomId);
            return null;
        }

        // 检查房间状态
        if (room.getStatus() != expectedStatus) {
            logger.warn("用户 {} 尝试在错误状态下操作房间 {}: 当前={}, 期望={}",
                    username, roomId, room.getStatus(), expectedStatus);
            return null;
        }

        return room;
    }

    /**
     * 清理用户的网络资源
     */
    private void cleanupUserNetworkResources(User user, String networkId) {
        if (user == null) return;

        try {
            // 释放用户的虚拟IP
            networkService.removeIpAddress(user.getUsername(), networkId);
            user.setVirtualIp(null);
            user.setRoomId(0L);
            userService.updateUser(user);
            logger.info("已释放用户 {} 的虚拟IP", user.getUsername());
        } catch (Exception e) {
            logger.error("释放用户 {} 的虚拟IP时出错: {}", user.getUsername(), e.getMessage(), e);
        }
    }

    /**
     * 处理房间变为空的情况
     */
    private void handleEmptyRoom(Room room) {
        try {
            // 删除虚拟网络
            boolean deleted = networkService.deleteNetwork(room.getNetworkId());
            if (deleted) {
                logger.info("已删除房间 {} 的虚拟网络: {}", room.getId(), room.getNetworkId());
            } else {
                logger.warn("删除房间 {} 的虚拟网络 {} 失败", room.getId(), room.getNetworkId());
            }
        } catch (Exception e) {
            logger.error("删除房间 {} 的虚拟网络时出错: {}", room.getId(), e.getMessage(), e);
        }

        logger.info("房间 {} 已清空并将被删除", room.getId());
    }

    /**
     * 处理用户离开后的房间状态
     */
    private void handleRoomAfterUserLeave(Room room, String username) {
        // 处理房主变更
        if (username.equals(room.getCreatorUsername())) {
            String newCreator = room.getPlayers().iterator().next();
            room.setCreatorUsername(newCreator);

            // 发送系统消息通知房主变更
            messageService.sendSystemMessage(
                    MessageTarget.ROOM,
                    room.getId(),
                    "用户 " + username + " 离开了房间，" + newCreator + " 成为新房主"
            );

            logger.info("房间 {} 的房主权限已从 {} 转移给 {}", room.getId(), username, newCreator);
        } else {
            // 普通用户离开时发送通知
            messageService.sendSystemMessage(
                    MessageTarget.ROOM,
                    room.getId(),
                    "用户 " + username + " 离开了房间"
            );
        }

        roomRepository.save(room);
    }

    /**
     * 清理房间相关资源
     */
    private void cleanupRoomResources(Room room) {
        if (room == null) return;

        // 清理消息历史
        messageService.clearRoomMessageHistory(room.getId());

        // 清理网络资源
        try {
            networkService.deleteNetwork(room.getNetworkId());
        } catch (Exception e) {
            logger.error("清理房间 {} 的网络资源时出错: {}", room.getId(), e.getMessage());
        }
    }
}