package com.platform.repository;

import com.platform.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    // 查找包含指定用户的房间
    @Query("SELECT r FROM Room r JOIN r.players p WHERE p = :username")
    List<Room> findByPlayerUsername(@Param("username") String username);

    // 查找指定房间名称的房间
    Optional<Room> findByName(String name);

    // 查找可以加入的房间(等待状态且未满)
    @Query("SELECT r FROM Room r WHERE r.status = 'WAITING' AND SIZE(r.players) < r.maxPlayers")
    List<Room> findJoinableRooms();

    // 修改查询语句中的字段名
    @Query("SELECT r FROM Room r WHERE r.gameName = :gameName AND r.status = 'WAITING' AND SIZE(r.players) < r.maxPlayers")
    List<Room> findJoinableRoomsByGameName(@Param("gameName") String gameName);

    // 查找没有玩家的房间
    @Query("SELECT r FROM Room r WHERE r.players IS EMPTY")
    List<Room> findEmptyRooms();
}