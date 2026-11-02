package com.nihonmukae.backend.user;

import com.nihonmukae.backend.auth.UserRepository;
import com.nihonmukae.backend.shared.entity.User;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import com.nihonmukae.backend.user.dto.ProfileDtos.Profile;
import com.nihonmukae.backend.user.dto.ProfileDtos.UpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Profile getProfile(UUID userId) {
        return toProfile(find(userId));
    }

    @Transactional
    public Profile updateProfile(UUID userId, UpdateRequest req) {
        User user = find(userId);
        user.setName(req.name().trim());
        user.setTargetLevel(req.targetLevel());
        return toProfile(user);
    }

    private User find(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản"));
    }

    private Profile toProfile(User u) {
        // Tên mặc định lấy từ phần trước @ của email nếu lúc đăng ký không nhập
        String name = u.getName() == null || u.getName().isBlank() ? u.getEmail().split("@")[0] : u.getName();
        return new Profile(u.getId(), u.getEmail(), name, u.getRole(), u.getTargetLevel(), u.getCreatedAt());
    }
}
