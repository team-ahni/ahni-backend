package com.ahni.backend.repository;

import com.ahni.backend.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    boolean existsByAuthUserIdAndDeletedAtIsNull(UUID authUserId);

    Optional<Admin> findByAuthUserIdAndDeletedAtIsNull(UUID authUserId);

    Optional<Admin> findByEntityIdAndDeletedAtIsNull(UUID entityId);

    List<Admin> findAllByDeletedAtIsNullOrderByNameAsc();
}
