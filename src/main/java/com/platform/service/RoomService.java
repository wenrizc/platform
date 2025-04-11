package com.platform.service;

import com.platform.entity.Room;
import com.platform.entity.User;
import com.platform.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);

    private final RoomRepository roomRepository;
    private final UserService userService;
    private final WebSocketService webSocketService;
    private final MessageService messageService;

    @Autowired
    public RoomService(RoomRepository roomRepository, UserService userService, WebSocketService webSocketService,
            MessageService messageService) {
        this.roomRepository = roomRepository;
        this.userService = userService;
        this.webSocketService = webSocketService;
        this.messageService = messageService;
    }

    /**
     * 创建新房间
     */
    public Room createRoom(String username, String roomName, String gameName, int maxPlayers) {
        // 检查用户是否存在且活跃
        User user = userService.findByUsername(username);
        if (user == null || !userService.isUserActive(user)) {
            logger.warn("用户 {} 尝试创建房间但未登录或不活跃", username);
            return null;
        }

        if (user.getRoomId() != 0) {
            logger.warn("用户 {} 已经在房间中，无法创建新房间", username);
            return null;
        }

        // 检查用户是否已经在房间中
        List<Room> existingRooms = roomRepository.findByPlayerUsername(username);
        if (!existingRooms.isEmpty()) {
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

        // TODO: 调用n2n服务创建虚拟网络
        // String networkId = n2nService.createNetwork();
        // String networkName = "room_" + room.getId();
        // String networkSecret = generateRandomSecret();
        // room.setN2nNetworkId(networkId);
        // room.setN2nNetworkName(networkName);
        // room.setN2nNetworkSecret(networkSecret);

        // 为创建者分配虚拟IP
        // TODO: 为用户分配虚拟IP
        // String virtualIp = userService.assignVirtualIp(username);

        Room savedRoom = roomRepository.save(room);

        // 广播房间创建消息
        broadcastRoomUpdate(savedRoom, "CREATED", username);

        logger.info("用户 {} 创建了房间: {}", username, roomName);
        return savedRoom;
    }

    /**
     * 加入房间
     */
    public boolean joinRoom(String username, Long roomId) {
        // 检查用户是否存在且活跃
        User user = userService.findByUsername(username);
        if (user == null || !userService.isUserActive(user)) {
            logger.warn("用户 {} 尝试加入房间但未登录或不活跃", username);
            return false;
        }

        // 检查用户是否已经在房间中
        List<Room> existingRooms = roomRepository.findByPlayerUsername(username);
        if (!existingRooms.isEmpty()) {
            logger.warn("用户 {} 已经在房间 {} 中，无法加入新房间", username, existingRooms.get(0).getId());
            return false;
        }

        // 检查房间是否存在
        Optional<Room> optionalRoom = roomRepository.findById(roomId);
        if (optionalRoom.isEmpty()) {
            logger.warn("用户 {} 尝试加入不存在的房间: {}", username, roomId);
            return false;
        }

        Room room = optionalRoom.get();

        // 检查房间状态
        if (room.getStatus() != Room.RoomStatus.WAITING) {
            logger.warn("用户 {} 尝试加入非等待状态的房间: {}", username, roomId);
            return false;
        }

        // 检查房间是否已满
        if (room.isFull()) {
            logger.warn("用户 {} 尝试加入已满的房间: {}", username, roomId);
            return false;
        }

        // 将用户添加到房间
        room.addPlayer(username);
        roomRepository.save(room);

        // TODO: 为用户分配虚拟IP
        // String virtualIp = userService.assignVirtualIp(username);

        // 广播用户加入消息
        broadcastRoomUpdate(room, "JOINED", username);

        logger.info("用户 {} 加入了房间: {}", username, roomId);
        return true;
    }

    /**
     * 离开房间
     */
    public boolean leaveRoom(String username) {
        // 检查用户是否存在
        User user = userService.findByUsername(username);
        if (user == null) {
            logger.warn("尝试让不存在的用户 {} 离开房间", username);
            return false;
        }

        // 查找用户所在的房间
        List<Room> userRooms = roomRepository.findByPlayerUsername(username);
        if (userRooms.isEmpty()) {
            logger.warn("用户 {} 不在任何房间中", username);
            return false;
        }

        Room room = userRooms.get(0);

        // 从房间中移除用户
        room.removePlayer(username);

        // 判断房间是否为空，如果为空则删除房间
        if (room.isEmpty()) {
            roomRepository.delete(room);
            // 清除消息历史
            messageService.clearRoomMessageHistory(room.getId());
            // TODO: 删除n2n网络
            // n2nService.deleteNetwork(room.getN2nNetworkId());
            logger.info("房间 {} 已清空并删除", room.getId());
        } else {
            // 如果离开的是创建者，转移房主权限
            if (username.equals(room.getCreatorUsername())) {
                String newCreator = room.getPlayers().iterator().next();
                room.setCreatorUsername(newCreator);
                logger.info("房间 {} 的房主权限已从 {} 转移给 {}", room.getId(), username, newCreator);
            }

            roomRepository.save(room);
        }

        // 广播用户离开消息
        broadcastRoomUpdate(room, "LEFT", username);

        logger.info("用户 {} 离开了房间: {}", username, room.getId());
        return true;
    }

    /**
     * 开始游戏
     */
    public boolean startGame(String username, Long roomId) {
        // 检查房间是否存在
        Optional<Room> optionalRoom = roomRepository.findById(roomId);
        if (optionalRoom.isEmpty()) {
            logger.warn("用户 {} 尝试在不存在的房间 {} 中开始游戏", username, roomId);
            return false;
        }

        Room room = optionalRoom.get();

        // 检查是否是房主
        if (!username.equals(room.getCreatorUsername())) {
            logger.warn("非房主用户 {} 尝试在房间 {} 中开始游戏", username, roomId);
            return false;
        }

        // 检查房间状态
        if (room.getStatus() != Room.RoomStatus.WAITING) {
            logger.warn("用户 {} 尝试在非等待状态的房间 {} 中开始游戏", username, roomId);
            return false;
        }

        // 检查玩家数量
        if (room.getPlayers().size() < 2) {
            logger.warn("用户 {} 尝试在玩家数量不足的房间 {} 中开始游戏", username, roomId);
            return false;
        }

        // 更新房间状态
        room.setStatus(Room.RoomStatus.PLAYING);
        roomRepository.save(room);

        // 广播游戏开始消息
        broadcastRoomUpdate(room, "STARTED", username);

        logger.info("房间 {} 的游戏已开始", roomId);
        return true;
    }

    /**
     * 结束游戏
     */
    public boolean endGame(String username, Long roomId) {
        // 检查房间是否存在
        Optional<Room> optionalRoom = roomRepository.findById(roomId);
        if (optionalRoom.isEmpty()) {
            logger.warn("用户 {} 尝试在不存在的房间 {} 中结束游戏", username, roomId);
            return false;
        }

        Room room = optionalRoom.get();

        // 检查是否是房主
        if (!username.equals(room.getCreatorUsername())) {
            logger.warn("非房主用户 {} 尝试在房间 {} 中结束游戏", username, roomId);
            return false;
        }

        // 检查房间状态
        if (room.getStatus() != Room.RoomStatus.PLAYING) {
            logger.warn("用户 {} 尝试在非游戏中状态的房间 {} 中结束游戏", username, roomId);
            return false;
        }

        // 更新房间状态
        room.setStatus(Room.RoomStatus.WAITING);
        roomRepository.save(room);

        // 广播游戏结束消息
        broadcastRoomUpdate(room, "ENDED", username);

        logger.info("房间 {} 的游戏已结束", roomId);
        return true;
    }

    /**
     * 检查房间名是否已存在
     */
    public boolean isRoomNameExists(String roomName) {
        return roomRepository.findByName(roomName).isPresent();
    }

    /**
     * 获取可加入的房间列表
     */
    public List<Room> getJoinableRooms() {
        return roomRepository.findJoinableRooms();
    }

    /**
     * 按游戏名称获取可加入的房间列表
     */
    public List<Room> getJoinableRoomsByGameName(String gameName) {
        return roomRepository.findJoinableRoomsByGameName(gameName);
    }

    /**
     * 获取用户所在房间
     */
    public Room getUserRoom(String username) {
        List<Room> rooms = roomRepository.findByPlayerUsername(username);
        return rooms.isEmpty() ? null : rooms.get(0);
    }

    /**
     * 获取房间信息
     */
    public Room getRoomInfo(Long roomId) {
        return roomRepository.findById(roomId).orElse(null);
    }

    /**
     * 广播房间更新消息
     */
    private void broadcastRoomUpdate(Room room, String action, String username) {
        // 构建消息
        Map<String, Object> message = new HashMap<>();
        message.put("roomId", room.getId());
        message.put("action", action);
        message.put("username", username);
        message.put("players", room.getPlayers());
        message.put("roomStatus", room.getStatus().name());
        message.put("timestamp", System.currentTimeMillis());

        // 通过WebSocket发送消息
        webSocketService.broadcastMessage("/topic/rooms.updates", message);

        // 给房间内的玩家发送更详细的消息
        Map<String, Object> detailMessage = new HashMap<>();
        detailMessage.put("id", room.getId());
        detailMessage.put("name", room.getName());
        detailMessage.put("gameName", room.getGameName());
        detailMessage.put("maxPlayers", room.getMaxPlayers());
        detailMessage.put("creatorUsername", room.getCreatorUsername());
        detailMessage.put("status", room.getStatus().name());
        detailMessage.put("players", room.getPlayers());
        detailMessage.put("n2nNetworkName", room.getN2nNetworkName());
        detailMessage.put("n2nNetworkSecret", room.getN2nNetworkSecret());

        for (String player : room.getPlayers()) {
            webSocketService.sendMessageToUser(player, "/queue/room.detail", detailMessage);
        }
    }

    /**
     * 发送房间消息
     */
    public void sendRoomMessage(Long roomId, String senderUsername, String message) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room != null && room.containsPlayer(senderUsername)) {
            // 构建消息
            Map<String, Object> chatMessage = new HashMap<>();
            chatMessage.put("roomId", roomId);
            chatMessage.put("sender", senderUsername);
            chatMessage.put("message", message);
            chatMessage.put("timestamp", System.currentTimeMillis());

            // 保存消息历史
            messageService.addRoomMessage(roomId, senderUsername, message);

            // 发送到房间特定频道
            String destination = "/topic/room." + roomId + ".messages";
            webSocketService.broadcastMessage(destination, chatMessage);

            logger.debug("用户 {} 在房间 {} 发送消息: {}", senderUsername, roomId, message);
        }
    }

    @Scheduled(cron = "0 0 * * * *") // 每小时整点执行
    public void cleanupEmptyRooms() {
        List<Room> emptyRooms = roomRepository.findEmptyRooms();

        if (!emptyRooms.isEmpty()) {
            logger.info("开始清理空房间，发现 {} 个空房间", emptyRooms.size());

            for (Room room : emptyRooms) {
                // 记录要清理的房间信息
                logger.debug("清理空房间: ID={}, 名称={}, 创建时间={}",
                        room.getId(), room.getName(), room.getCreationTime());

                // 清理房间相关的消息记录
                messageService.clearRoomMessageHistory(room.getId());

                // 清理N2N网络配置
                // Todo: 删除n2n网络

                // 删除房间
                deleteRoom(room.getId());
            }

            logger.info("空房间清理完成，共清理 {} 个房间", emptyRooms.size());

            // 发送系统通知
            webSocketService.sendSystemNotification("系统已自动清理了 " + emptyRooms.size() + " 个空房间");
        }
    }

    /**
     * 用户离开房间
     *
     * @param userId 用户ID
     * @param roomId 房间ID
     * @return 操作结果
     */
    public boolean leaveRoom(Long userId, Long roomId) {
        // 通过用户ID获取用户
        User user = userService.findById(userId);
        if (user == null) {
            logger.warn("尝试让不存在的用户ID {} 离开房间", userId);
            return false;
        }

        // 验证用户是否在指定房间中
        if (user.getRoomId() != roomId) {
            logger.warn("用户 {} 不在指定房间 {} 中", user.getUsername(), roomId);
            return false;
        }

        // 调用基于用户名的离开房间方法
        return leaveRoom(user.getUsername());
    }

    public List<Room> getJoinableRoomsWithCleanup() {
        int offlineUsersRemoved = 0;
        int emptyRoomsRemoved = 0;
        List<Room> allRooms = roomRepository.findAll();
        List<Room> roomsToDelete = new ArrayList<>();

        for (Room room : allRooms) {
            boolean roomModified = false;
            Set<String> offlineUsers = new HashSet<>();

            // 检查每个房间内的所有用户是否在线
            for (String playerUsername : new HashSet<>(room.getPlayers())) {
                User user = userService.findByUsername(playerUsername);

                // 如果用户不存在或不在线，将其标记为离线
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

                // 广播用户离开消息
                broadcastRoomUpdate(room, "LEFT", offlineUsername);
            }

            // 如果房间为空，标记为待删除
            if (room.isEmpty()) {
                roomsToDelete.add(room);
            }
            // 否则，如果房间被修改，保存更改
            else if (roomModified) {
                roomRepository.save(room);
            }
        }

        // 删除所有空房间
        for (Room room : roomsToDelete) {
            // 清理房间相关消息记录
            messageService.clearRoomMessageHistory(room.getId());

            logger.debug("清理空房间: ID={}, 名称={}, 创建时间={}",
                    room.getId(), room.getName(), room.getCreationTime());

            // 删除房间
            deleteRoom(room.getId());
            emptyRoomsRemoved++;
        }

        // 如果有任何清理操作，发送系统通知
        if (offlineUsersRemoved > 0 || emptyRoomsRemoved > 0) {
            String notification = String.format("系统自动清理: 移除了 %d 个离线用户, 删除了 %d 个空房间",
                    offlineUsersRemoved, emptyRoomsRemoved);
            webSocketService.sendSystemNotification(notification);
            logger.info(notification);
        }

        // 返回可加入的房间
        return roomRepository.findJoinableRooms();
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        // 使用悲观锁策略获取最新房间状态
        Room room = roomRepository.findById(roomId).orElse(null);

        if (room != null) {
            // 再次检查房间是否为空
            if (room.isEmpty()) {
                // 删除前先清理相关资源
                try {
                    // 清理房间相关消息
                    messageService.clearRoomMessageHistory(roomId);

                    // 执行删除
                    roomRepository.deleteById(roomId);

                    logger.info("成功删除空房间: ID={}, 名称={}", roomId, room.getName());
                } catch (Exception e) {
                    logger.error("删除房间 {} 时发生错误: {}", roomId, e.getMessage());
                    throw e;
                }
            } else {
                logger.debug("房间 {} 不为空，跳过删除", roomId);
            }
        } else {
            logger.debug("房间 {} 不存在或已被删除", roomId);
        }
    }

}