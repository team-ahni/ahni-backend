package com.ahni.backend.service;

import com.ahni.backend.entity.Admin;
import com.ahni.backend.exception.AdminAccessDeniedException;
import com.ahni.backend.repository.AdminRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccessServiceTest {
    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private AdminAccessService adminAccessService;

    @Test
    void 활성_관리자의_본인_정보를_반환한다() {
        UUID authUserId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        Admin admin = mock(Admin.class);
        when(admin.getEntityId()).thenReturn(entityId);
        when(admin.getName()).thenReturn("관리자 이름");
        when(admin.getEmail()).thenReturn("admin@inha.edu");
        when(adminRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId))
            .thenReturn(Optional.of(admin));

        var response = adminAccessService.getCurrentAdmin(authUserId);

        assertThat(response.entityId()).isEqualTo(entityId);
        assertThat(response.name()).isEqualTo("관리자 이름");
        assertThat(response.email()).isEqualTo("admin@inha.edu");
    }

    @Test
    void 활성_관리자_매핑이_없으면_접근을_거부한다() {
        UUID authUserId = UUID.randomUUID();
        when(adminRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminAccessService.getCurrentAdmin(authUserId))
            .isInstanceOf(AdminAccessDeniedException.class);
    }

    @Test
    void 활성_관리자_목록을_반환한다() {
        UUID authUserId = UUID.randomUUID();
        Admin admin = mock(Admin.class);
        when(admin.getName()).thenReturn("관리자 이름");
        when(adminRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId))
            .thenReturn(Optional.of(admin));
        when(adminRepository.findAllByDeletedAtIsNullOrderByNameAsc()).thenReturn(List.of(admin));

        var response = adminAccessService.findActiveAdmins(authUserId);

        assertThat(response).extracting("name").containsExactly("관리자 이름");
    }

    @Test
    void 관리자가_아니면_관리자_목록을_조회하지_않는다() {
        UUID authUserId = UUID.randomUUID();
        when(adminRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminAccessService.findActiveAdmins(authUserId))
            .isInstanceOf(AdminAccessDeniedException.class);
        verify(adminRepository, never()).findAllByDeletedAtIsNullOrderByNameAsc();
    }
}
