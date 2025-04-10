package com.platform.repository;

import com.platform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);

    User findBySessionId(String sessionId);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.active = true")
    List<User> findAllActiveUsers();

    @Modifying
    @Transactional
    @Query("DELETE FROM User u WHERE u.lastActiveTime < :timestamp")
    int deleteInactiveUsers(@Param("timestamp") Instant timestamp);
}