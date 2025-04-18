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
    private String gameName;

    @Column(name = "max_players")
    private int maxPlayers;

    @Column(name = "creation_time")
    private Instant creationTime;

    @Column(name = "creator_username")
    private String creatorUsername;

    @Column(name = "room_status")
    @Enumerated(EnumType.STRING)
    private RoomStatus status;

    @Column(name = "network_id")
    private String networkId;

    @Column(name = "network_name")
    private String networkName;

    @Column(name = "network_secret")
    private String networkSecret;

    @Column(name = "network_type")
    private String networkType;

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
    public Room(String name, String gameName, int maxPlayers, String creatorUsername) {
        this.name = name;
        this.gameName = gameName;
        this.maxPlayers = maxPlayers;
        this.creatorUsername = creatorUsername;
        this.players.add(creatorUsername);
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

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
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

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName;
    }

    public String getNetworkSecret() {
        return networkSecret;
    }

    public void setNetworkSecret(String networkSecret) {
        this.networkSecret = networkSecret;
    }

    public String getNetworkType() {
        return networkType;
    }

    public void setNetworkType(String networkType) {
        this.networkType = networkType;
    }

    public Set<String> getPlayers() { return players;}

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