package com.portafolio.pedidos.repository;

import com.portafolio.pedidos.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop20ByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
