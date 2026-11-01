package com.nihonmukae.backend.question;

import com.nihonmukae.backend.question.ExamConfigRepository.BlueprintRow;
import com.nihonmukae.backend.question.dto.JlptDtos.Blueprint;
import com.nihonmukae.backend.question.dto.JlptDtos.BlueprintBlock;
import com.nihonmukae.backend.question.dto.JlptDtos.BlueprintType;
import com.nihonmukae.backend.question.dto.JlptDtos.TestSummary;
import com.nihonmukae.backend.shared.entity.QuestionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JlptService {

    private static final List<String> SECTION_ORDER = List.of("moji", "goi", "bunpou", "dokkai", "chokai");

    private final JlptTestRepository testRepository;
    private final QuestionRepository questionRepository;
    private final QuestionTypeRepository typeRepository;
    private final ExamConfigRepository examConfig;

    public JlptService(JlptTestRepository testRepository,
                       QuestionRepository questionRepository,
                       QuestionTypeRepository typeRepository,
                       ExamConfigRepository examConfig) {
        this.testRepository = testRepository;
        this.questionRepository = questionRepository;
        this.typeRepository = typeRepository;
        this.examConfig = examConfig;
    }

    /** Các đề thi của 1 level (chỉ đề đã có câu hỏi). */
    @Transactional(readOnly = true)
    public List<TestSummary> listTests(short level) {
        Map<Long, Integer> counts = new HashMap<>();
        Map<Long, Set<String>> sectionsByTest = new HashMap<>();
        for (Object[] row : questionRepository.countByTestAndSection(level)) {
            Long testId = (Long) row[0];
            counts.merge(testId, ((Long) row[2]).intValue(), Integer::sum);
            sectionsByTest.computeIfAbsent(testId, k -> new LinkedHashSet<>()).add((String) row[1]);
        }

        // Mỗi phần thi thuộc 1 khối; thời gian của đề là tổng thời gian các khối mà đề có câu
        Map<String, Integer> blockOfSection = new HashMap<>();
        Map<Short, QuestionType> types = typesById();
        for (BlueprintRow row : examConfig.findBlueprint(level)) {
            QuestionType t = types.get(row.typeId());
            if (t != null) {
                blockOfSection.putIfAbsent(t.getSection(), row.blockNo());
            }
        }
        Map<Integer, Integer> durationOfBlock = examConfig.findBlocks(level).stream()
                .collect(Collectors.toMap(ExamConfigRepository.Block::blockNo, ExamConfigRepository.Block::durationMin));

        List<TestSummary> result = new ArrayList<>();
        for (var test : testRepository.findByLevelAndActiveTrueOrderByYearDescMonthDescIdDesc(level)) {
            if (!counts.containsKey(test.getId())) {
                continue;
            }
            List<String> sections = sectionsByTest.get(test.getId()).stream()
                    .sorted((a, b) -> Integer.compare(SECTION_ORDER.indexOf(a), SECTION_ORDER.indexOf(b))).toList();
            Set<Integer> blocks = sections.stream().map(blockOfSection::get).filter(b -> b != null)
                    .collect(Collectors.toSet());
            int duration = blocks.stream().mapToInt(b -> durationOfBlock.getOrDefault(b, 0)).sum();
            result.add(new TestSummary(test.getId(), test.getTitle(), test.getSourceNote(), test.getYear(),
                    test.getMonth(), counts.get(test.getId()), duration, sections));
        }
        return result;
    }

    /** Cấu trúc đề thật của level, kèm số câu kho đang có cho từng dạng. */
    @Transactional(readOnly = true)
    public Blueprint blueprint(short level) {
        Map<Short, Long> available = new HashMap<>();
        for (Object[] row : questionRepository.countByTypeForLevel(level)) {
            available.put((Short) row[0], (Long) row[1]);
        }
        Map<Short, QuestionType> types = typesById();

        List<BlueprintRow> rows = examConfig.findBlueprint(level);
        List<String> shortages = new ArrayList<>();
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (var block : examConfig.findBlocks(level)) {
            List<BlueprintType> list = new ArrayList<>();
            for (BlueprintRow row : rows) {
                if (row.blockNo() != block.blockNo()) {
                    continue;
                }
                QuestionType t = types.get(row.typeId());
                long have = available.getOrDefault(row.typeId(), 0L);
                list.add(new BlueprintType(row.typeId(), t.getCode(), t.getNameVi(), t.getNameJa(), row.quantity(),
                        row.pointsPerQuestion(), have));
                if (have < row.quantity()) {
                    shortages.add(t.getNameVi() + ": có " + have + "/" + row.quantity() + " câu");
                }
            }
            blocks.add(new BlueprintBlock(block.blockNo(), block.name(), block.durationMin(), list));
        }
        return new Blueprint(level, blocks, shortages);
    }

    private Map<Short, QuestionType> typesById() {
        return typeRepository.findAll().stream().collect(Collectors.toMap(QuestionType::getId, Function.identity()));
    }
}
