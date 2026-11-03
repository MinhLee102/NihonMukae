package com.nihonmukae.backend.practice;

import com.nihonmukae.backend.practice.UnitSelector.Unit;
import com.nihonmukae.backend.question.ExamConfigRepository;
import com.nihonmukae.backend.question.ExamConfigRepository.BlueprintRow;
import com.nihonmukae.backend.question.JlptTestRepository;
import com.nihonmukae.backend.question.QuestionGroupRepository;
import com.nihonmukae.backend.question.QuestionRepository;
import com.nihonmukae.backend.question.QuestionTypeRepository;
import com.nihonmukae.backend.shared.entity.JlptTest;
import com.nihonmukae.backend.shared.entity.Question;
import com.nihonmukae.backend.shared.entity.QuestionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Bốc câu hỏi từ kho cho 1 phiên: ôn theo dạng bài, đề thi cụ thể, hoặc đề random theo blueprint. */
@Component
public class QuestionPicker {

    /** 1 câu được chọn, kèm khối thi và điểm của nó trong phiên. */
    public record Entry(Question question, int blockNo, BigDecimal points) {
    }

    /** listeningTestId: đề cung cấp audio cả phần nghe cho các câu nghe trong phiên (null nếu không có). */
    public record Picked(List<Entry> entries, List<String> warnings, Long listeningTestId) {
        public Picked(List<Entry> entries, List<String> warnings) {
            this(entries, warnings, null);
        }
    }

    /** Một bộ nghe: đề có audio cả phần nghe, kèm toàn bộ câu nghe của đề đó theo thứ tự. */
    public record ListeningSet(JlptTest test, List<Question> questions) {
    }

    private final QuestionRepository questionRepository;
    private final QuestionGroupRepository groupRepository;
    private final QuestionTypeRepository typeRepository;
    private final ExamConfigRepository examConfig;
    private final JlptTestRepository testRepository;
    private final Random random = new Random();

    public QuestionPicker(QuestionRepository questionRepository,
                          QuestionGroupRepository groupRepository,
                          QuestionTypeRepository typeRepository,
                          ExamConfigRepository examConfig,
                          JlptTestRepository testRepository) {
        this.questionRepository = questionRepository;
        this.groupRepository = groupRepository;
        this.typeRepository = typeRepository;
        this.examConfig = examConfig;
        this.testRepository = testRepository;
    }

    // ─── Ôn theo dạng bài ───────────────────────────────────────────────

    public Picked pickPractice(short level, List<Short> typeIds, int count) {
        Map<Short, QuestionType> types = typesById();
        List<Short> ordered = typeIds.stream().distinct().filter(types::containsKey)
                .sorted(Comparator.comparing(id -> types.get(id).getSortOrder())).toList();

        Map<Short, List<Unit>> pools = new LinkedHashMap<>();
        Map<Short, Integer> available = new HashMap<>();
        for (Short typeId : ordered) {
            List<Unit> units = unitsForType(typeId, level);
            pools.put(typeId, units);
            available.put(typeId, UnitSelector.countQuestions(units));
        }

        // Chia đều số câu giữa các dạng, dạng nào hết câu thì phần dư dồn cho dạng khác
        Map<Short, Integer> quota = new HashMap<>();
        int remaining = count;
        boolean progress = true;
        while (remaining > 0 && progress) {
            progress = false;
            for (Short typeId : ordered) {
                if (remaining > 0 && quota.getOrDefault(typeId, 0) < available.get(typeId)) {
                    quota.merge(typeId, 1, Integer::sum);
                    remaining--;
                    progress = true;
                }
            }
        }

        List<Entry> entries = new ArrayList<>();
        for (Short typeId : ordered) {
            int q = quota.getOrDefault(typeId, 0);
            if (q == 0) {
                continue;
            }
            List<Unit> chosen = UnitSelector.select(pools.get(typeId), q, random);
            for (Question question : loadOrdered(chosen)) {
                entries.add(new Entry(question, 1, BigDecimal.ONE));
            }
        }

        List<String> warnings = new ArrayList<>();
        int totalAvailable = available.values().stream().mapToInt(Integer::intValue).sum();
        if (totalAvailable < count) {
            warnings.add("Kho chỉ có " + totalAvailable + " câu phù hợp, ít hơn " + count + " câu bạn chọn.");
        }
        return new Picked(entries, warnings);
    }

    // ─── Thi JLPT ───────────────────────────────────────────────────────

    /** Đề cụ thể: lấy toàn bộ câu của đề, xếp theo khối rồi theo thứ tự trong đề. */
    public Picked pickExamTest(long testId, short level) {
        Map<Short, BlueprintRow> blueprint = examConfig.findBlueprint(level).stream()
                .collect(Collectors.toMap(b -> b.typeId(), Function.identity(), (a, b) -> a));
        List<Entry> entries = new ArrayList<>();
        for (Question q : questionRepository.findByTestIdAndActiveTrueOrderByTestOrderAscIdAsc(testId)) {
            BlueprintRow row = blueprint.get(q.getTypeId());
            entries.add(new Entry(q, row == null ? 1 : row.blockNo(), row == null ? BigDecimal.ONE : row.pointsPerQuestion()));
        }
        entries.sort(Comparator.comparingInt(Entry::blockNo));
        return new Picked(entries, List.of());
    }

    /** Đề random: với mỗi dòng blueprint bốc đúng số câu (hoặc cả nhóm); phần nghe lấy nguyên một bộ nghe nếu có. */
    public Picked pickExamRandom(short level) {
        Map<Short, QuestionType> types = typesById();
        List<Entry> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<BlueprintRow> listeningRows = new ArrayList<>();

        for (BlueprintRow row : examConfig.findBlueprint(level)) {
            QuestionType type = types.get(row.typeId());
            if (type != null && "chokai".equals(type.getSection())) {
                listeningRows.add(row);
            } else {
                pickRow(row, level, types, entries, warnings);
            }
        }

        Long listeningTestId = null;
        if (!listeningRows.isEmpty()) {
            Optional<ListeningSet> set = pickListeningSet(level);
            if (set.isPresent()) {
                Map<Short, BlueprintRow> rowByType = listeningRows.stream()
                        .collect(Collectors.toMap(BlueprintRow::typeId, Function.identity(), (x, y) -> x));
                int block = listeningRows.get(0).blockNo();
                for (Question q : set.get().questions()) {
                    BlueprintRow row = rowByType.get(q.getTypeId());
                    entries.add(new Entry(q, row == null ? block : row.blockNo(),
                            row == null ? BigDecimal.ONE : row.pointsPerQuestion()));
                }
                listeningTestId = set.get().test().getId();
            } else {
                // Chưa có bộ nghe nào có audio cả phần: bốc từng câu nghe có audio riêng như trước
                for (BlueprintRow row : listeningRows) {
                    pickRow(row, level, types, entries, warnings);
                }
            }
        }
        return new Picked(entries, warnings, listeningTestId);
    }

    private void pickRow(BlueprintRow row, short level, Map<Short, QuestionType> types, List<Entry> entries,
                         List<String> warnings) {
        List<Unit> pool = unitsForType(row.typeId(), level);
        List<Unit> chosen = UnitSelector.select(pool, row.quantity(), random);
        List<Question> questions = loadOrdered(chosen);
        for (Question q : questions) {
            entries.add(new Entry(q, row.blockNo(), row.pointsPerQuestion()));
        }
        if (questions.size() < row.quantity()) {
            QuestionType type = types.get(row.typeId());
            warnings.add("Kho thiếu câu cho dạng " + (type == null ? row.typeId() : type.getNameVi())
                    + ": có " + questions.size() + "/" + row.quantity() + " câu.");
        }
    }

    // ─── Luyện nghe nguyên bộ ───────────────────────────────────────────

    /** Một bộ nghe ngẫu nhiên: đề của level có audio cả phần nghe, kèm toàn bộ câu nghe của đề đó. */
    public Optional<ListeningSet> pickListeningSet(short level) {
        List<JlptTest> tests = new ArrayList<>(testRepository.findByLevelAndActiveTrueAndListeningAudioUrlIsNotNull(level));
        Collections.shuffle(tests, random);
        Map<Short, QuestionType> types = typesById();
        for (JlptTest test : tests) {
            List<Question> questions = questionRepository.findByTestIdAndActiveTrueOrderByTestOrderAscIdAsc(test.getId())
                    .stream()
                    .filter(q -> {
                        QuestionType type = types.get(q.getTypeId());
                        return type != null && "chokai".equals(type.getSection());
                    }).toList();
            if (!questions.isEmpty()) {
                return Optional.of(new ListeningSet(test, questions));
            }
        }
        return Optional.empty();
    }

    // ─── Dùng chung ─────────────────────────────────────────────────────

    /** Các đơn vị bốc được của 1 dạng bài: câu đứng riêng (1 câu) và từng nhóm (cả đoạn văn hoặc audio). */
    private List<Unit> unitsForType(short typeId, short level) {
        List<Unit> units = new ArrayList<>();
        for (Long id : questionRepository.findStandaloneIds(typeId, level)) {
            units.add(new Unit(null, List.of(id)));
        }
        List<Long> groupIds = groupRepository.findActiveIds(typeId, level);
        if (!groupIds.isEmpty()) {
            Map<Long, List<Long>> byGroup = new LinkedHashMap<>();
            for (Question q : questionRepository.findByGroupIdInAndActiveTrueOrderByTestOrderAscIdAsc(groupIds)) {
                byGroup.computeIfAbsent(q.getGroupId(), k -> new ArrayList<>()).add(q.getId());
            }
            byGroup.forEach((gid, ids) -> units.add(new Unit(gid, ids)));
        }
        return units;
    }

    private List<Question> loadOrdered(List<Unit> units) {
        List<Long> ids = units.stream().flatMap(u -> u.questionIds().stream()).toList();
        Map<Long, Question> byId = questionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(q -> q != null).toList();
    }

    private Map<Short, QuestionType> typesById() {
        return typeRepository.findAll().stream().collect(Collectors.toMap(QuestionType::getId, Function.identity()));
    }
}
