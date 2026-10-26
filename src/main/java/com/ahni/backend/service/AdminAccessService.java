package com.ahni.backend.service;

import com.ahni.backend.dto.AdminIdentityResponse;
import com.ahni.backend.entity.Admin;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.repository.AdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AdminAccessService {
    private final AdminRepository adminRepository;

    public AdminAccessService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public AdminIdentityResponse getCurrentAdmin(UUID authUserId) {
        return identity(getAdmin(authUserId));
    }

    public List<AdminIdentityResponse> findActiveAdmins(UUID authUserId) {
        getAdmin(authUserId);
        return adminRepository.findAllByDeletedAtIsNullOrderByNameAsc().stream()
            .map(AdminAccessService::identity)
            .toList();
    }

    public Optional<Admin> findActiveAdmin(UUID adminEntityId) {
        return adminRepository.findByEntityIdAndDeletedAtIsNull(adminEntityId);
    }

    public Admin getAdmin(UUID authUserId) {
        return adminRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)
            .orElseThrow(AdminAccessDeniedException::new);
    }

    public static AdminIdentityResponse identity(Admin admin) {
        return new AdminIdentityResponse(
            admin.getEntityId(),
            admin.getName(),
            admin.getEmail()
        );
    }
}
