package com.nihonmukae.backend.admin;

import com.nihonmukae.backend.admin.dto.AdminDtos.BankSummary;
import com.nihonmukae.backend.admin.dto.AdminDtos.QuestionPage;
import com.nihonmukae.backend.admin.dto.AdminDtos.QuestionDetail;
import com.nihonmukae.backend.admin.dto.AdminDtos.QuestionRow;
import com.nihonmukae.backend.admin.dto.AdminDtos.UpdateQuestionRequest;
import com.nihonmukae.backend.admin.dto.AdminDtos.TestRow;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;

    public AdminService(QuestionRepository questionRepository,
                        QuestionTypeRepository typeRepository,
                        UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        JdbcTemplate jdbc) {
        this.questionRepository = questionRepository;
        this.typeRepository = typeRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jdbc = jdbc;
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

    // ─── Đề thi ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TestRow> listTests() {
        return jdbc.query("""
                SELECT t.id, t.level, t.title, t.year, t.month, t.listening_audio_url IS NOT NULL AS has_audio,
                       (SELECT count(*) FROM questions q WHERE q.test_id = t.id) AS question_count,
                       (SELECT count(*) FROM questions q WHERE q.test_id = t.id AND q.is_active) AS active_count
                FROM jlpt_tests t ORDER BY t.level, t.year DESC NULLS LAST, t.month DESC NULLS LAST, t.id
                """, (rs, i) -> new TestRow(rs.getLong("id"), rs.getInt("level"), rs.getString("title"),
                (Integer) rs.getObject("year"), (Integer) rs.getObject("month"), rs.getBoolean("has_audio"),
                rs.getLong("question_count"), rs.getLong("active_count")));
    }

    /**
     * Xóa hẳn một đề cùng toàn bộ câu hỏi và group của nó. Lịch sử làm bài vẫn giữ (có snapshot):
     * các khóa ngoại từ phiên làm bài sang đề và câu hỏi tự chuyển về null.
     */
    @Transactional
    public TestRow deleteTest(long id) {
        TestRow test = listTests().stream().filter(t -> t.id() == id).findFirst()
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đề"));
        jdbc.update("DELETE FROM questions WHERE test_id = ?", id);
        jdbc.update("DELETE FROM question_groups WHERE test_id = ?", id);
        jdbc.update("DELETE FROM jlpt_tests WHERE id = ?", id);
        return test;
    }

    /** Dọn hẳn các câu và group đã bị ẩn (xóa mềm) để DB gọn lại. @return số câu đã xóa */
    @Transactional
    public int purgeHidden() {
        int questions = jdbc.update("DELETE FROM questions WHERE is_active = false");
        jdbc.update("DELETE FROM question_groups g WHERE g.is_active = false "
                + "AND NOT EXISTS (SELECT 1 FROM questions q WHERE q.group_id = g.id)");
        return questions;
    }

    @Transactional(readOnly = true)
    public QuestionDetail getQuestion(long id) {
        Question q = questionRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy câu hỏi"));
        return toDetail(q);
    }

    /**
     * Sửa nội dung một câu. Mã băm nội dung giữ nguyên, nên import lại file gốc vẫn nhận ra đây là câu cũ
     * (bỏ qua, không tạo bản trùng); chọn Ghi đè thì bản gốc được khôi phục.
     */
    @Transactional
    public QuestionDetail updateQuestion(long id, UpdateQuestionRequest req) {
        Question q = questionRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy câu hỏi"));
        if (req.question() != null) {
            String text = req.question().trim();
            if (text.isEmpty() || text.length() > 6000) {
                throw new IllegalArgumentException("Nội dung câu hỏi phải có từ 1 đến 6000 ký tự");
            }
            q.setQuestion(text);
        }
        List<String> options = req.options() != null ? req.options().stream().map(String::trim).toList() : q.getOptions();
        if (req.options() != null) {
            if (options.size() < 2 || options.size() > 6 || options.stream().anyMatch(o -> o.isEmpty() || o.length() > 1000)) {
                throw new IllegalArgumentException("Cần 2 đến 6 đáp án, mỗi đáp án không được để trống");
            }
            q.setOptions(options);
        }
        String correct = req.correct() != null ? req.correct().trim().toUpperCase() : q.getCorrect();
        if (req.correct() != null || req.options() != null) {
            if (correct.length() != 1 || correct.charAt(0) < 'A' || correct.charAt(0) - 'A' >= options.size()) {
                throw new IllegalArgumentException("Đáp án đúng phải là một chữ cái trong số các đáp án");
            }
            q.setCorrect(correct);
        }
        if (req.explanation() != null) {
            q.setExplanation(limited(req.explanation(), 6000, "Giải thích"));
        }
        if (req.transcript() != null) {
            q.setTranscript(limited(req.transcript(), 10000, "Transcript"));
        }
        return toDetail(q);
    }

    private static String limited(String value, int max, String label) {
        String text = value.trim();
        if (text.length() > max) {
            throw new IllegalArgumentException(label + " tối đa " + max + " ký tự");
        }
        return text.isEmpty() ? null : text;
    }

    private QuestionDetail toDetail(Question q) {
        QuestionType type = typeRepository.findById(q.getTypeId()).orElse(null);
        return new QuestionDetail(q.getId(), q.getLevel(), type == null ? null : type.getCode(),
                type == null ? null : type.getNameVi(), type == null ? null : type.getSection(), q.getQuestion(),
                q.getOptions(), q.getCorrect(), q.getExplanation(), q.getTranscript(), q.getAudioUrl(), q.getGroupId(),
                q.getTestId());
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
            if ("pending".equals(status)) {
                p.add(cb.isFalse(root.get("approved")));
            } else if ("active".equals(status)) {
                p.add(cb.isTrue(root.get("enabled")));
                p.add(cb.isTrue(root.get("approved")));
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
                userRepository.countByEnabledFalse(), userRepository.countByCreatedAtAfter(since),
                userRepository.countByApprovedFalse());
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

    /** Duyệt hoặc thu hồi duyệt. Thu hồi huỷ luôn phiên đang có; không thu hồi ADMIN hay chính mình. */
    @Transactional
    public UserRow setApproved(UUID adminId, UUID userId, boolean approved) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
        if (!approved && (user.getId().equals(adminId) || "ADMIN".equals(user.getRole()))) {
            throw new IllegalArgumentException("Không thể thu hồi duyệt tài khoản ADMIN");
        }
        user.setApproved(approved);
        if (!approved) {
            refreshTokenRepository.deleteByUserId(user.getId());
        }
        return toUserRow(user);
    }

    /** Từ chối đăng ký: xoá hẳn tài khoản đang chờ duyệt (tài khoản đã duyệt thì khoá, không xoá). */
    @Transactional
    public void rejectPending(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
        if (user.isApproved()) {
            throw new IllegalArgumentException("Chỉ từ chối được tài khoản đang chờ duyệt");
        }
        refreshTokenRepository.deleteByUserId(user.getId());
        userRepository.delete(user);
    }

    private UserRow toUserRow(User u) {
        String name = u.getName() == null || u.getName().isBlank() ? u.getEmail().split("@")[0] : u.getName();
        return new UserRow(u.getId(), name, u.getEmail(), u.getRole(), u.getTargetLevel(), u.isEnabled(),
                u.isApproved(), u.getCreatedAt(), u.getLastLoginAt());
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
