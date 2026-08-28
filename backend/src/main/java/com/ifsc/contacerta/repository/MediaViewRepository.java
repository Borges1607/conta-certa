package com.ifsc.contacerta.repository;

import com.ifsc.contacerta.entity.MediaView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MediaViewRepository extends JpaRepository<MediaView, UUID> {
}
