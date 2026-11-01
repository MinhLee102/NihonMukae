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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Import đề và câu hỏi từ JSON theo 2 bước: preview (validate, tìm trùng, chưa lưu) rồi commit. */
@Service
public class QuestionImportService {

    public static final String SOURCE_IMPORT = "import_json";
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
        Map<String, QuestionType> types = typesByCode();
        JlptTest test = file.getTest() == null ? null : resolveTest(file.getTest());
        int[] counters = new int[4]; // createdQuestions, createdGroups, skipped, overwritten
        short order = 0;

        for (ImportFile.Item item : file.getQuestions()) {
            order++;
            saveQuestion(userId, item, null, null, null, test, order, types, overwrite, counters, null);
        }
        for (ImportFile.Group group : file.getGroups()) {
            order++;
            QuestionType groupType = group.getType() == null ? null : types.get(group.getType());
            int level = group.getLevel() != null ? group.getLevel() : test.getLevel();
            String typeCode = group.getType();
            String hash = ContentHasher.hashGroup(typeCode == null ? "" : typeCode, level, group.getPassage(),
                    group.getAudioUrl(), group.getImageUrl());
            QuestionGroup saved = groupRepository.findByContentHash(hash).orElse(null);
            if (saved == null) {
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
                        .source(SOURCE_IMPORT)
                        .contentHash(hash)
                        .active(true)
                        .build());
                counters[1]++;
            }
            for (ImportFile.Item item : group.getQuestions()) {
                saveQuestion(userId, item, group.getType(), level, saved, test, order, types, overwrite, counters,
                        group.getPassage());
            }
        }
        return new CommitResponse(counters[0], counters[1], counters[2], counters[3], test == null ? null : test.getId());
    }

    private void saveQuestion(UUID userId, ImportFile.Item item, String inheritedType, Integer inheritedLevel,
                              QuestionGroup group, JlptTest test, short order, Map<String, QuestionType> types,
                              boolean overwrite, int[] counters, String passage) {
        String typeCode = item.getType() != null && !item.getType().isBlank() ? item.getType() : inheritedType;
        QuestionType type = types.get(typeCode);
        int level = item.getLevel() != null ? item.getLevel() : inheritedLevel != null ? inheritedLevel : test.getLevel();
        String hash = ContentHasher.hashQuestion(typeCode, level, item.getQuestion(), item.getOptions(), passage);

        Question existing = questionRepository.findByContentHash(hash).orElse(null);
        if (existing != null) {
            if (overwrite) {
                existing.setCorrect(item.getCorrect());
                existing.setExplanation(item.getExplanation());
                existing.setAudioUrl(blankToNull(item.getAudioUrl()));
                existing.setActive(true);
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
                .source(SOURCE_IMPORT)
                .contentHash(hash)
                .active(true)
                .createdBy(userId)
                .build());
        counters[0]++;
    }

    private JlptTest resolveTest(ImportFile.TestInfo info) {
        short level = info.getLevel().shortValue();
        JlptTest existing;
        if (info.getYear() != null && info.getMonth() != null) {
            existing = testRepository.findByLevelAndYearAndMonth(level, info.getYear().shortValue(), info.getMonth().shortValue())
                    .orElse(null);
        } else {
            existing = testRepository.findByLevelAndTitle(level, info.getTitle().trim()).orElse(null);
        }
        if (existing != null) {
            return existing;
        }
        return testRepository.save(JlptTest.builder()
                .level(level)
                .title(testTitle(info))
                .sourceNote(info.getSourceNote())
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

    private void findDuplicates(ImportFile file, Map<String, QuestionType> types, List<Duplicate> out) {
        Set<String> seen = new HashSet<>();
        Integer testLevel = file.getTest() == null ? null : file.getTest().getLevel();

        for (int i = 0; i < file.getQuestions().size(); i++) {
            ImportFile.Item item = file.getQuestions().get(i);
            String hash = hashItem(item, null, null, testLevel, null);
            checkDuplicate(hash, "questions[" + i + "]", seen, out);
        }
        for (int g = 0; g < file.getGroups().size(); g++) {
            ImportFile.Group group = file.getGroups().get(g);
            Integer level = group.getLevel() != null ? group.getLevel() : testLevel;
            for (int i = 0; i < group.getQuestions().size(); i++) {
                String hash = hashItem(group.getQuestions().get(i), group.getType(), level, testLevel, group.getPassage());
                checkDuplicate(hash, "groups[" + g + "].questions[" + i + "]", seen, out);
            }
        }
    }

    private String hashItem(ImportFile.Item item, String inheritedType, Integer inheritedLevel, Integer testLevel,
                            String passage) {
        String typeCode = item.getType() != null && !item.getType().isBlank() ? item.getType() : inheritedType;
        int level = item.getLevel() != null ? item.getLevel() : inheritedLevel != null ? inheritedLevel : testLevel;
        return ContentHasher.hashQuestion(typeCode, level, item.getQuestion(), item.getOptions(), passage);
    }

    private void checkDuplicate(String hash, String path, Set<String> seen, List<Duplicate> out) {
        if (!seen.add(hash)) {
            out.add(new Duplicate(path, "trùng với câu khác trong cùng file"));
        } else if (questionRepository.findByContentHash(hash).isPresent()) {
            out.add(new Duplicate(path, "đã có trong kho"));
        }
    }

    private Map<String, QuestionType> typesByCode() {
        return typeRepository.findAll().stream().collect(Collectors.toMap(QuestionType::getCode, Function.identity()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
