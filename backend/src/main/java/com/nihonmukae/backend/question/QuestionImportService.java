package com.nihonmukae.backend.question;

import com.nihonmukae.backend.question.dto.ImportDtos.CommitResponse;
import com.nihonmukae.backend.question.dto.ImportDtos.Duplicate;
import com.nihonmukae.backend.question.dto.ImportDtos.PreviewResponse;
import com.nihonmukae.backend.question.dto.ImportDtos.Problem;
import com.nihonmukae.backend.question.dto.ImportFile;
import com.nihonmukae.backend.shared.entity.JlptTest;
import com.nihonmukae.backend.shared.entity.Question;
import com.nihonmukae.backend.shared.entity.QuestionGroup;
import com.nihonmukae.backend.shared.entity.QuestionType;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Import đề và câu hỏi từ JSON theo 2 bước: preview (validate, tìm trùng, chưa lưu) rồi commit. */
@Service
public class QuestionImportService {

    public static final String SOURCE_IMPORT = "import_json";
    public static final String SOURCE_SAMPLE = "sample";
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(30);

    private record StoredPreview(UUID userId, ImportFile file, Instant expiresAt) {
    }

    // Preview giữ trong bộ nhớ (1 instance backend), tự hết hạn sau 30 phút
    private final Map<UUID, StoredPreview> previews = new ConcurrentHashMap<>();

    private final QuestionTypeRepository typeRepository;
    private final QuestionRepository questionRepository;
    private final QuestionGroupRepository groupRepository;
    private final JlptTestRepository testRepository;

    public QuestionImportService(QuestionTypeRepository typeRepository,
                                 QuestionRepository questionRepository,
                                 QuestionGroupRepository groupRepository,
                                 JlptTestRepository testRepository) {
        this.typeRepository = typeRepository;
        this.questionRepository = questionRepository;
        this.groupRepository = groupRepository;
        this.testRepository = testRepository;
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(UUID userId, ImportFile file) {
        previews.values().removeIf(p -> p.expiresAt().isBefore(Instant.now()));

        Map<String, QuestionType> types = typesByCode();
        List<Problem> errors = ImportValidator.validate(file, types).stream()
                .map(p -> new Problem(p.path(), p.message())).collect(Collectors.toCollection(ArrayList::new));

        List<Duplicate> duplicates = new ArrayList<>();
        if (errors.isEmpty()) {
            findDuplicates(file, types, duplicates);
        }

        int groupQuestions = file.getGroups().stream().mapToInt(g -> g.getQuestions().size()).sum();
        UUID previewId = UUID.randomUUID();
        if (errors.isEmpty()) {
            previews.put(previewId, new StoredPreview(userId, file, Instant.now().plus(PREVIEW_TTL)));
        }
        String testTitle = file.getTest() == null ? null : testTitle(file.getTest());
        return new PreviewResponse(errors.isEmpty() ? previewId : null, file.getQuestions().size() + groupQuestions,
                file.getGroups().size(), testTitle, errors, duplicates, errors.isEmpty());
    }

    @Transactional
    public CommitResponse commit(UUID userId, boolean isAdmin, UUID previewId, String onDuplicate) {
        StoredPreview stored = previews.get(previewId);
        if (stored == null || !stored.userId().equals(userId) || stored.expiresAt().isBefore(Instant.now())) {
            throw new NotFoundException("Bản xem trước không tồn tại hoặc đã hết hạn, hãy xem trước lại");
        }
        boolean overwrite = "overwrite".equals(onDuplicate);
        if (overwrite && !isAdmin) {
            throw new AccessDeniedException("Chỉ ADMIN mới được ghi đè câu trùng");
        }
        CommitResponse response = importFile(userId, stored.file(), overwrite);
        previews.remove(previewId);
        return response;
    }

    /** Dùng chung cho commit và seeder dữ liệu mẫu: lưu thẳng, đã validate ở nơi gọi. */
    @Transactional
    public CommitResponse importFile(UUID userId, ImportFile file, boolean overwrite) {
        return importFile(userId, file, overwrite, SOURCE_IMPORT);
    }

    /** source ghi vào các dòng tạo mới (import_json cho người dùng, sample cho dữ liệu mẫu của seeder). */
    @Transactional
    public CommitResponse importFile(UUID userId, ImportFile file, boolean overwrite, String source) {
        Map<String, QuestionType> types = typesByCode();
        JlptTest test = file.getTest() == null ? null : resolveTest(file.getTest());
        int[] counters = new int[4]; // createdQuestions, createdGroups, skipped, overwritten
        short order = 0;
        ImportKeys keys = new ImportKeys(ImportKeys.scopeOf(test));

        for (ImportFile.Item item : file.getQuestions()) {
            order++;
            saveQuestion(userId, item, null, null, null, test, order, types, overwrite, counters, null, source, keys);
        }
        for (ImportFile.Group group : file.getGroups()) {
            order++;
            QuestionType groupType = group.getType() == null ? null : types.get(group.getType());
            int level = group.getLevel() != null ? group.getLevel() : test.getLevel();
            String typeCode = group.getType();
            String hash = keys.group(typeCode == null ? "" : typeCode, level, group.getPassage());
            QuestionGroup saved = groupRepository.findByContentHash(hash).orElse(null);
            if (saved != null) {
                // Nhóm đã có (có thể đã bị xóa mềm): bật lại, và khi ghi đè thì cập nhật audio, ảnh, tiêu đề
                saved.setActive(true);
                if (overwrite) {
                    saved.setAudioUrl(blankToNull(group.getAudioUrl()));
                    saved.setImageUrl(blankToNull(group.getImageUrl()));
                    saved.setTitle(group.getTitle());
                }
                groupRepository.save(saved);
            } else {
                saved = groupRepository.save(QuestionGroup.builder()
                        .typeId(groupType == null ? null : groupType.getId())
                        .level((short) level)
                        .testId(test == null ? null : test.getId())
                        .testOrder(test == null ? null : order)
                        .title(group.getTitle())
                        .passage(group.getPassage())
                        .audioUrl(blankToNull(group.getAudioUrl()))
                        .imageUrl(blankToNull(group.getImageUrl()))
                        .topic(group.getTopic())
                        .source(source)
                        .contentHash(hash)
                        .active(true)
                        .build());
                counters[1]++;
            }
            for (ImportFile.Item item : group.getQuestions()) {
                saveQuestion(userId, item, group.getType(), level, saved, test, order, types, overwrite, counters,
                        group.getPassage(), source, keys);
            }
        }
        return new CommitResponse(counters[0], counters[1], counters[2], counters[3], test == null ? null : test.getId());
    }

    private void saveQuestion(UUID userId, ImportFile.Item item, String inheritedType, Integer inheritedLevel,
                              QuestionGroup group, JlptTest test, short order, Map<String, QuestionType> types,
                              boolean overwrite, int[] counters, String passage, String source, ImportKeys keys) {
        String typeCode = item.getType() != null && !item.getType().isBlank() ? item.getType() : inheritedType;
        QuestionType type = types.get(typeCode);
        int level = item.getLevel() != null ? item.getLevel() : inheritedLevel != null ? inheritedLevel : test.getLevel();
        String hash = keys.question(typeCode, level, item.getQuestion(), item.getOptions(), passage);

        Question existing = questionRepository.findByContentHash(hash).orElse(null);
        if (existing != null) {
            if (!existing.isActive()) {
                // Câu đã bị xóa mềm trong Ngân hàng đề: import lại thì khôi phục kèm dữ liệu mới
                existing.setCorrect(item.getCorrect());
                existing.setExplanation(item.getExplanation());
                existing.setAudioUrl(blankToNull(item.getAudioUrl()));
                applyTranscript(existing, item);
                existing.setActive(true);
                counters[0]++;
            } else if (overwrite) {
                existing.setCorrect(item.getCorrect());
                existing.setExplanation(item.getExplanation());
                existing.setAudioUrl(blankToNull(item.getAudioUrl()));
                applyTranscript(existing, item);
                counters[3]++;
            } else {
                counters[2]++;
            }
            return;
        }
        questionRepository.save(Question.builder()
                .typeId(type.getId())
                .level((short) level)
                .testId(test == null ? null : test.getId())
                .testOrder(test == null ? null : order)
                .groupId(group == null ? null : group.getId())
                .question(item.getQuestion().trim())
                .audioUrl(blankToNull(item.getAudioUrl()))
                .options(item.getOptions().stream().map(String::trim).toList())
                .correct(item.getCorrect())
                .explanation(item.getExplanation())
                .transcript(blankToNull(item.getTranscript()))
                .source(source)
                .contentHash(hash)
                .active(true)
                .createdBy(userId)
                .build());
        counters[0]++;
    }

    private Optional<JlptTest> findTest(ImportFile.TestInfo info) {
        short level = info.getLevel().shortValue();
        if (info.getYear() != null && info.getMonth() != null) {
            return testRepository.findByLevelAndYearAndMonth(level, info.getYear().shortValue(), info.getMonth().shortValue());
        }
        return testRepository.findByLevelAndTitle(level, info.getTitle().trim());
    }

    private JlptTest resolveTest(ImportFile.TestInfo info) {
        short level = info.getLevel().shortValue();
        JlptTest existing = findTest(info).orElse(null);
        if (existing != null) {
            if (notBlank(info.getAudioUrl())) {
                existing.setListeningAudioUrl(info.getAudioUrl().trim());
                testRepository.save(existing);
            }
            return existing;
        }
        return testRepository.save(JlptTest.builder()
                .level(level)
                .title(testTitle(info))
                .sourceNote(info.getSourceNote())
                .listeningAudioUrl(notBlank(info.getAudioUrl()) ? info.getAudioUrl().trim() : null)
                .year(info.getYear() == null ? null : info.getYear().shortValue())
                .month(info.getMonth() == null ? null : info.getMonth().shortValue())
                .active(true)
                .build());
    }

    private String testTitle(ImportFile.TestInfo info) {
        if (info.getTitle() != null && !info.getTitle().isBlank()) {
            return info.getTitle().trim();
        }
        return String.format("Đề thi %02d/%d", info.getMonth(), info.getYear());
    }

    /**
     * Câu "đã có" là câu cùng đề, cùng nội dung, cùng thứ tự xuất hiện (xem ImportKeys). Câu trùng nội dung nhưng
     * nằm ở đề khác, hoặc lặp lại trong cùng file (câu nghe giống nhau), không phải là trùng. Câu đã xóa không tính.
     */
    private void findDuplicates(ImportFile file, Map<String, QuestionType> types, List<Duplicate> out) {
        JlptTest test = file.getTest() == null ? null : findTest(file.getTest()).orElse(null);
        if (file.getTest() != null && test == null) {
            return; // đề chưa có trong hệ thống nên chưa có câu nào để trùng
        }
        Integer testLevel = file.getTest() == null ? null : file.getTest().getLevel();
        ImportKeys keys = new ImportKeys(ImportKeys.scopeOf(test));
        String where = test == null ? "đã có trong kho luyện tập" : "đã có trong đề này";

        for (int i = 0; i < file.getQuestions().size(); i++) {
            ImportFile.Item item = file.getQuestions().get(i);
            checkDuplicate(keys.question(typeOf(item, null), levelOf(item, null, testLevel), item.getQuestion(),
                    item.getOptions(), null), "questions[" + i + "]", where, out);
        }
        for (int g = 0; g < file.getGroups().size(); g++) {
            ImportFile.Group group = file.getGroups().get(g);
            Integer level = group.getLevel() != null ? group.getLevel() : testLevel;
            for (int i = 0; i < group.getQuestions().size(); i++) {
                ImportFile.Item item = group.getQuestions().get(i);
                checkDuplicate(keys.question(typeOf(item, group.getType()), levelOf(item, level, testLevel),
                        item.getQuestion(), item.getOptions(), group.getPassage()),
                        "groups[" + g + "].questions[" + i + "]", where, out);
            }
        }
    }

    private static String typeOf(ImportFile.Item item, String inheritedType) {
        return item.getType() != null && !item.getType().isBlank() ? item.getType() : inheritedType;
    }

    private static int levelOf(ImportFile.Item item, Integer inheritedLevel, Integer testLevel) {
        return item.getLevel() != null ? item.getLevel() : inheritedLevel != null ? inheritedLevel : testLevel;
    }

    private void checkDuplicate(String hash, String path, String where, List<Duplicate> out) {
        if (questionRepository.findByContentHash(hash).filter(Question::isActive).isPresent()) {
            out.add(new Duplicate(path, where));
        }
    }

    private Map<String, QuestionType> typesByCode() {
        return typeRepository.findAll().stream().collect(Collectors.toMap(QuestionType::getCode, Function.identity()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Chỉ ghi đè transcript khi file có cung cấp, để không xóa transcript đã sửa tay. */
    private static void applyTranscript(Question question, ImportFile.Item item) {
        if (notBlank(item.getTranscript())) {
            question.setTranscript(item.getTranscript().trim());
        }
    }
}
