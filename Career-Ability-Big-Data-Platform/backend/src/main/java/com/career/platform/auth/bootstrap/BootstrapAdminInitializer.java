package com.career.platform.auth.bootstrap;

import com.career.platform.auth.entity.SysRole;
import com.career.platform.auth.entity.SysUser;
import com.career.platform.auth.repository.SysRoleRepository;
import com.career.platform.auth.repository.SysUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a first administrator only when both one-time bootstrap variables are supplied.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final BootstrapAdminProperties properties;
    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            BootstrapAdminProperties properties,
            SysUserRepository userRepository,
            SysRoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.isPartiallyConfigured()) {
            throw new IllegalStateException(
                    "INITIAL_ADMIN_USERNAME and INITIAL_ADMIN_PASSWORD must be supplied together");
        }
        if (!properties.isConfigured()) {
            return;
        }
        if (properties.getUsername().length() < 4 || properties.getUsername().length() > 50) {
            throw new IllegalStateException("INITIAL_ADMIN_USERNAME must be 4-50 characters");
        }
        if (properties.getPassword().length() < 12) {
            throw new IllegalStateException("INITIAL_ADMIN_PASSWORD must contain at least 12 characters");
        }
        if (userRepository.existsByUsername(properties.getUsername())) {
            return;
        }

        SysRole adminRole = roleRepository.findByRoleCode(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN must be initialized before bootstrap"));
        SysUser admin = new SysUser();
        admin.setUsername(properties.getUsername());
        admin.setPassword(passwordEncoder.encode(properties.getPassword()));
        admin.setRealName("Platform Administrator");
        admin.setStatus(1);
        admin.setRoles(java.util.Set.of(adminRole));
        userRepository.save(admin);
        log.warn("Bootstrap administrator '{}' was created; remove INITIAL_ADMIN_PASSWORD after this first startup",
                properties.getUsername());
    }
}
