package com.career.platform.auth.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.career.platform.auth.entity.SysRole;
import com.career.platform.auth.entity.SysUser;
import com.career.platform.auth.repository.SysRoleRepository;
import com.career.platform.auth.repository.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class BootstrapAdminInitializerTest {

    private BootstrapAdminProperties properties;
    private SysUserRepository users;
    private SysRoleRepository roles;
    private PasswordEncoder passwordEncoder;
    private BootstrapAdminInitializer initializer;

    @BeforeEach
    void setUp() {
        properties = new BootstrapAdminProperties();
        users = mock(SysUserRepository.class);
        roles = mock(SysRoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        initializer = new BootstrapAdminInitializer(properties, users, roles, passwordEncoder);
    }

    @Test
    void createsAnAdministratorOnlyOnTheFirstConfiguredRun() throws Exception {
        properties.setUsername("bootstrap-admin");
        properties.setPassword("long-enough-bootstrap-password");
        SysRole adminRole = new SysRole();
        adminRole.setRoleCode("ROLE_ADMIN");
        when(users.existsByUsername("bootstrap-admin")).thenReturn(false);
        when(roles.findByRoleCode("ROLE_ADMIN")).thenReturn(java.util.Optional.of(adminRole));
        when(passwordEncoder.encode(properties.getPassword())).thenReturn("encoded-password");

        initializer.run(mock(ApplicationArguments.class));

        ArgumentCaptor<SysUser> user = ArgumentCaptor.forClass(SysUser.class);
        verify(users).save(user.capture());
        assertEquals("bootstrap-admin", user.getValue().getUsername());
        assertEquals("encoded-password", user.getValue().getPassword());
        assertEquals(1, user.getValue().getStatus());
        assertEquals(java.util.Set.of(adminRole), user.getValue().getRoles());
    }

    @Test
    void neverResetsAnExistingBootstrapUser() throws Exception {
        properties.setUsername("bootstrap-admin");
        properties.setPassword("long-enough-bootstrap-password");
        when(users.existsByUsername("bootstrap-admin")).thenReturn(true);

        initializer.run(mock(ApplicationArguments.class));

        verify(users, never()).save(any());
        verify(roles, never()).findByRoleCode(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void rejectsPartialOrWeakBootstrapConfiguration() {
        properties.setUsername("bootstrap-admin");
        assertThrows(IllegalStateException.class, () -> initializer.run(mock(ApplicationArguments.class)));

        properties.setPassword("short");
        assertThrows(IllegalStateException.class, () -> initializer.run(mock(ApplicationArguments.class)));
    }
}
