package com.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String gameType;

    @Column(name = "max_players")
    private int maxPlayers;

    @Column(name = "creation_time")
    private Instant creationTime;

    @Column(name = "creator_username")
    private String creatorUsername;

    @Column(name = "room_status")
    @Enumerated(EnumType.STRING)
    private RoomStatus status;

    @Column(name = "n2n_network_id")
    private String n2nNetworkId;

    @Column(name = "n2n_network_name")
    private String n2nNetworkName;

    @Column(name = "n2n_network_secret")
    private String n2nNetworkSecret;

    @ElementCollection
    @CollectionTable(name = "room_players", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "username")
    private Set<String> players = new HashSet<>();

    public enum RoomStatus {
        WAITING,    // 等待玩家加入
        PLAYING,    // 游戏进行中
        FINISHED    // 游戏结束
    }

    // 无参构造函数
    public Room() {
        this.creationTime = Instant.now();
        this.status = RoomStatus.WAITING;
    }

    // 带参数的构造函数
    public Room(String name, String gameType, int maxPlayers, String creatorUsername) {
        this.name = name;
        this.gameType = gameType;
        this.maxPlayers = maxPlayers;
        this.creatorUsername = creatorUsername;
        this.players.add(creatorUsername); // 创建者自动加入房间
        this.creationTime = Instant.now();
        this.status = RoomStatus.WAITING;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public String getCreatorUsername() {
        return creatorUsername;
    }

    public void setCreatorUsername(String creatorUsername) {
        this.creatorUsername = creatorUsername;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public String getN2nNetworkId() {
        return n2nNetworkId;
    }

    public void setN2nNetworkId(String n2nNetworkId) {
        this.n2nNetworkId = n2nNetworkId;
    }

    public String getN2nNetworkName() {
        return n2nNetworkName;
    }

    public void setN2nNetworkName(String n2nNetworkName) {
        this.n2nNetworkName = n2nNetworkName;
    }

    public String getN2nNetworkSecret() {
        return n2nNetworkSecret;
    }

    public void setN2nNetworkSecret(String n2nNetworkSecret) {
        this.n2nNetworkSecret = n2nNetworkSecret;
    }

    public Set<String> getPlayers() {
        return players;
    }

    public void addPlayer(String username) {
        this.players.add(username);
    }

    public void removePlayer(String username) {
        this.players.remove(username);
    }

    public boolean isFull() {
        return this.players.size() >= this.maxPlayers;
    }

    public boolean isEmpty() {
        return this.players.isEmpty();
    }

    public boolean containsPlayer(String username) {
        return this.players.contains(username);
    }
}