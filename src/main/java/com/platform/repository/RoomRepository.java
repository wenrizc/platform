package com.platform.repository;

import com.platform.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    // 查找指定用户创建的房间
    List<Room> findByCreatorUsername(String creatorUsername);

    // 查找包含指定用户的房间
    @Query("SELECT r FROM Room r JOIN r.players p WHERE p = :username")
    List<Room> findByPlayerUsername(@Param("username") String username);

    // 查找处于等待状态的房间
    List<Room> findByStatus(Room.RoomStatus status);

    // 查找指定游戏类型的房间
    List<Room> findByGameType(String gameType);

    // 查找可以加入的房间(等待状态且未满)
    @Query("SELECT r FROM Room r WHERE r.status = 'WAITING' AND SIZE(r.players) < r.maxPlayers")
    List<Room> findJoinableRooms();

    // 按游戏类型查找可加入的房间
    @Query("SELECT r FROM Room r WHERE r.gameType = :gameType AND r.status = 'WAITING' AND SIZE(r.players) < r.maxPlayers")
    List<Room> findJoinableRoomsByGameType(@Param("gameType") String gameType);
}