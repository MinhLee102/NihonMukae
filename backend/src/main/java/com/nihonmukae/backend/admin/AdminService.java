package com.nihonmukae.backend.admin;

import com.nihonmukae.backend.admin.dto.AdminDtos.BankSummary;
import com.nihonmukae.backend.admin.dto.AdminDtos.QuestionPage;
import com.nihonmukae.backend.admin.dto.AdminDtos.QuestionRow;
import com.nihonmukae.backend.admin.dto.AdminDtos.UserPage;
import com.nihonmukae.backend.admin.dto.AdminDtos.UserRow;
import com.nihonmukae.backend.admin.dto.AdminDtos.UserSummary;
import com.nihonmukae.backend.auth.RefreshTokenRepository;
import com.nihonmukae.backend.auth.UserRepository;
import com.nihonmukae.backend.question.QuestionRepository;
import com.nihonmukae.backend.question.QuestionTypeRepository;
import com.nihonmukae.backend.shared.entity.Question;
import com.nihonmukae.backend.shared.entity.QuestionType;
import com.nihonmukae.backend.shared.entity.User;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Nghiệp vụ trang quản trị: kho câu hỏi và người dùng. */
@Service
public class AdminService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int PREVIEW_LENGTH = 160;

    private final QuestionRepository questionRepository;
    private final QuestionTypeRepository typeRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AdminService(QuestionRepository questionRepository,
                        QuestionTypeRepository typeRepository,
                        UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository) {
        this.questionRepository = questionRepository;
        this.typeRepository = typeRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // ─── Kho câu hỏi ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public QuestionPage listQuestions(Short level, String section, String keyword, int page, int size) {
        Map<Short, QuestionType> types = typesById();
        List<Short> sectionTypeIds = section == null || section.isBlank() ? null : types.values().stream()
                .filter(t -> section.equals(t.getSection())).map(QuestionType::getId).toList();

        Specification<Question> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.isTrue(root.get("active")));
            if (level != null) {
                p.add(cb.equal(root.get("level"), level));
            }
            if (sectionTypeIds != null) {
                p.add(sectionTypeIds.isEmpty() ? cb.disjunction() : root.get("typeId").in(sectionTypeIds));
            }
            if (keyword != null && !keyword.isBlank()) {
                p.add(cb.like(cb.lower(root.get("question")), "%" + keyword.trim().toLowerCase() + "%"));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };

        Page<Question> result = questionRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "id")));
        List<QuestionRow> rows = result.getContent().stream().map(q -> toRow(q, types.get(q.getTypeId()))).toList();
        return new QuestionPage(rows, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BankSummary bankSummary() {
        Map<String, Long> bySection = new LinkedHashMap<>();
        for (String s : List.of("moji", "goi", "bunpou", "dokkai", "chokai")) {
            bySection.put(s, 0L);
        }
        Map<Integer, Long> byLevel = new LinkedHashMap<>();
        for (int l = 5; l >= 1; l--) {
            byLevel.put(l, 0L);
        }
        long total = 0;
        for (Object[] row : questionRepository.countBySectionAndLevel()) {
            long count = (Long) row[2];
            bySection.merge((String) row[0], count, Long::sum);
            byLevel.merge(((Number) row[1]).intValue(), count, Long::sum);
            total += count;
        }
        return new BankSummary(bySection, byLevel, total);
    }

    /** Xóa mềm: câu ẩn khỏi kho và khỏi đề mới, nhưng lịch sử làm bài (snapshot) không bị ảnh hưởng. */
    @Transactional
    public void deleteQuestion(long id) {
        Question q = questionRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy câu hỏi"));
        q.setActive(false);
    }

    private QuestionRow toRow(Question q, QuestionType type) {
        String text = q.getQuestion().replaceAll("\\s+", " ");
        return new QuestionRow(q.getId(), q.getLevel(), type == null ? null : type.getCode(),
                type == null ? null : type.getNameVi(), type == null ? null : type.getSection(),
                text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "…" : text,
                q.getOptions().size(), q.getCorrect(), q.getAudioUrl() != null, q.getGroupId(), q.getTestId(),
                q.getSource(), q.getCreatedAt());
    }

    // ─── Người dùng ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserPage listUsers(String keyword, String status, int page, int size) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("email")), like), cb.like(cb.lower(root.get("name")), like)));
            }
            if ("active".equals(status)) {
                p.add(cb.isTrue(root.get("enabled")));
            } else if ("locked".equals(status)) {
                p.add(cb.isFalse(root.get("enabled")));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<User> result = userRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        List<UserRow> rows = result.getContent().stream().map(this::toUserRow).toList();
        return new UserPage(rows, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UserSummary userSummary() {
        ZonedDateTime since = ZonedDateTime.now().minusDays(30);
        return new UserSummary(userRepository.count(), userRepository.countByEnabledTrueAndLastLoginAtAfter(since),
                userRepository.countByEnabledFalse(), userRepository.countByCreatedAtAfter(since));
    }

    /** Khóa hoặc mở khóa. Không khóa chính mình và không khóa admin khác, tránh tự khóa mình ra khỏi hệ thống. */
    @Transactional
    public UserRow setEnabled(UUID adminId, UUID userId, boolean enabled) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
        if (!enabled && user.getId().equals(adminId)) {
            throw new IllegalArgumentException("Bạn không thể tự khóa tài khoản của mình");
        }
        if (!enabled && "ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("Không thể khóa tài khoản ADMIN");
        }
        user.setEnabled(enabled);
        if (!enabled) {
            // Hủy phiên đăng nhập đang có; access token hiện tại hết hạn trong tối đa 15 phút
            refreshTokenRepository.deleteByUserId(user.getId());
        }
        return toUserRow(user);
    }

    private UserRow toUserRow(User u) {
        String name = u.getName() == null || u.getName().isBlank() ? u.getEmail().split("@")[0] : u.getName();
        return new UserRow(u.getId(), name, u.getEmail(), u.getRole(), u.getTargetLevel(), u.isEnabled(),
                u.getCreatedAt(), u.getLastLoginAt());
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private Map<Short, QuestionType> typesById() {
        Map<Short, QuestionType> map = new HashMap<>();
        typeRepository.findAll().forEach(t -> map.put(t.getId(), t));
        return map;
    }
}
