package com.nihonmukae.backend.auth;

import com.nihonmukae.backend.shared.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Cấp quyền ADMIN lúc khởi động, vì app không có chức năng tự nâng quyền.
 *
 * <ul>
 *   <li><b>app.admin.emails</b> (env APP_ADMIN_EMAILS, ngăn cách bằng dấu phẩy): các tài khoản ĐÃ đăng ký
 *       với email này sẽ được nâng lên ADMIN. Dùng cho production: tự đăng ký như bình thường, rồi đặt email.</li>
 *   <li><b>app.dev.admin-email / app.dev.admin-password</b>: chỉ để chạy dev, tạo sẵn 1 tài khoản admin nếu chưa có.
 *       Để trống ở production.</li>
 * </ul>
 * Người đã đăng nhập cần đăng nhập lại để token mang quyền mới.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final List<String> adminEmails;
    private final String devAdminEmail;
    private final String devAdminPassword;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.emails:}") String adminEmails,
                          @Value("${app.dev.admin-email:}") String devAdminEmail,
                          @Value("${app.dev.admin-password:}") String devAdminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(String::toLowerCase).toList();
        this.devAdminEmail = devAdminEmail.trim();
        this.devAdminPassword = devAdminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String email : adminEmails) {
            userRepository.findByEmail(email).ifPresentOrElse(user -> {
                if (!"ADMIN".equals(user.getRole())) {
                    user.setRole("ADMIN");
                    log.info("Đã nâng {} lên ADMIN", email);
                }
            }, () -> log.warn("APP_ADMIN_EMAILS có {} nhưng chưa có tài khoản này, hãy đăng ký trước rồi khởi động lại", email));
        }

        if (!devAdminEmail.isEmpty() && !devAdminPassword.isEmpty() && userRepository.findByEmail(devAdminEmail).isEmpty()) {
            userRepository.save(User.builder()
                    .email(devAdminEmail)
                    .password(passwordEncoder.encode(devAdminPassword))
                    .name("Administrator")
                    .provider("local")
                    .role("ADMIN")
                    .build());
            log.info("Đã tạo tài khoản admin dev: {}", devAdminEmail);
        }
    }
}
