package com.nihonmukae.backend.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihonmukae.backend.practice.dto.ResultDtos.JlptScore;
import com.nihonmukae.backend.practice.dto.ResultDtos.Result;
import com.nihonmukae.backend.practice.dto.ResultDtos.ResultGroup;
import com.nihonmukae.backend.practice.dto.ResultDtos.ResultQuestion;
import com.nihonmukae.backend.practice.dto.ResultDtos.SectionScoreView;
import com.nihonmukae.backend.practice.dto.ResultDtos.TypeStat;
import com.nihonmukae.backend.practice.dto.SessionDtos.AnswerResult;
import com.nihonmukae.backend.practice.dto.SessionDtos.BlockView;
import com.nihonmukae.backend.practice.dto.SessionDtos.GroupView;
import com.nihonmukae.backend.practice.dto.SessionDtos.QuestionView;
import com.nihonmukae.backend.practice.dto.SessionDtos.SessionSummary;
import com.nihonmukae.backend.practice.dto.SessionDtos.SessionView;
import com.nihonmukae.backend.practice.dto.SessionDtos.StartRequest;
import com.nihonmukae.backend.question.ExamConfigRepository;
import com.nihonmukae.backend.question.ExamConfigRepository.Block;
import com.nihonmukae.backend.question.JlptTestRepository;
import com.nihonmukae.backend.question.QuestionGroupRepository;
import com.nihonmukae.backend.question.QuestionRepository;
import com.nihonmukae.backend.question.QuestionTypeRepository;
import com.nihonmukae.backend.shared.entity.JlptTest;
import com.nihonmukae.backend.shared.entity.PracticeAnswer;
import com.nihonmukae.backend.shared.entity.PracticeSession;
import com.nihonmukae.backend.shared.entity.PracticeSession.BlockState;
import com.nihonmukae.backend.shared.entity.Question;
import com.nihonmukae.backend.shared.entity.QuestionGroup;
import com.nihonmukae.backend.shared.entity.QuestionType;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PracticeSessionService {

    private static final long DEADLINE_GRACE_SECONDS = 5;
    private static final int MAX_QUESTION_COUNT = 200;

    private final PracticeSessionRepository sessionRepository;
    private final PracticeAnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final QuestionGroupRepository groupRepository;
    private final QuestionTypeRepository typeRepository;
    private final JlptTestRepository testRepository;
    private final ExamConfigRepository examConfig;
    private final QuestionPicker picker;
    private final ObjectMapper objectMapper;

    public PracticeSessionService(PracticeSessionRepository sessionRepository,
                                  PracticeAnswerRepository answerRepository,
                                  QuestionRepository questionRepository,
                                  QuestionGroupRepository groupRepository,
                                  QuestionTypeRepository typeRepository,
                                  JlptTestRepository testRepository,
                                  ExamConfigRepository examConfig,
                                  QuestionPicker picker,
                                  ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.groupRepository = groupRepository;
        this.typeRepository = typeRepository;
        this.testRepository = testRepository;
        this.examConfig = examConfig;
        this.picker = picker;
        this.objectMapper = objectMapper;
    }

    // ─── Tạo phiên ──────────────────────────────────────────────────────

    @Transactional
    public SessionView create(UUID userId, StartRequest req) {
        short level = req.level();
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("Level phải từ N5 đến N1");
        }
        boolean exam = PracticeSession.MODE_EXAM.equals(req.mode());
        if (!exam && !PracticeSession.MODE_PRACTICE.equals(req.mode())) {
            throw new IllegalArgumentException("mode phải là practice hoặc exam");
        }

        QuestionPicker.Picked picked;
        String title;
        PracticeSession.PracticeSessionBuilder builder = PracticeSession.builder()
                .userId(userId).mode(req.mode()).level(level).status(PracticeSession.STATUS_IN_PROGRESS)
                .startedAt(ZonedDateTime.now());

        if (exam) {
            builder.feedbackMode("after");
            if (req.testId() != null) {
                JlptTest test = testRepository.findById(req.testId())
                        .filter(t -> t.isActive() && t.getLevel() == level)
                        .orElseThrow(() -> new NotFoundException("Không tìm thấy đề thi"));
                picked = picker.pickExamTest(test.getId(), level);
                title = "N" + level + " · " + test.getTitle();
                builder.testId(test.getId());
            } else if (Boolean.TRUE.equals(req.random())) {
                picked = picker.pickExamRandom(level);
                title = "N" + level + " · Đề thi thử ngẫu nhiên";
                builder.random(true);
            } else {
                throw new IllegalArgumentException("Chọn một đề thi hoặc đề ngẫu nhiên");
            }
        } else {
            if (req.typeIds() == null || req.typeIds().isEmpty()) {
                throw new IllegalArgumentException("Hãy chọn ít nhất một dạng bài");
            }
            int count = req.questionCount() == null ? 10 : req.questionCount();
            if (count < 1 || count > MAX_QUESTION_COUNT) {
                throw new IllegalArgumentException("Số câu phải từ 1 đến " + MAX_QUESTION_COUNT);
            }
            if (req.timeLimitMin() != null && (req.timeLimitMin() < 1 || req.timeLimitMin() > 300)) {
                throw new IllegalArgumentException("Thời gian phải từ 1 đến 300 phút");
            }
            String feedback = req.feedbackMode() == null ? "after" : req.feedbackMode();
            if (!feedback.equals("instant") && !feedback.equals("after")) {
                throw new IllegalArgumentException("feedbackMode phải là instant hoặc after");
            }
            picked = picker.pickPractice(level, req.typeIds(), count);
            title = "N" + level + " · Luyện theo dạng bài";
            builder.typeIds(req.typeIds()).questionCount((short) count).feedbackMode(feedback)
                    .timeLimitMin(req.timeLimitMin() == null ? null : req.timeLimitMin().shortValue());
        }

        if (picked.entries().isEmpty()) {
            throw new IllegalArgumentException("Kho chưa có câu hỏi phù hợp với lựa chọn này");
        }

        ZonedDateTime now = ZonedDateTime.now();
        List<BlockState> blocks = exam ? examBlocks(level, picked, now) : List.of(practiceBlock(req.timeLimitMin(), now));
        PracticeSession session = sessionRepository.save(builder.title(title).blockState(blocks).build());

        List<PracticeAnswer> answers = new ArrayList<>();
        short position = 1;
        for (QuestionPicker.Entry e : picked.entries()) {
            answers.add(PracticeAnswer.builder()
                    .sessionId(session.getId()).questionId(e.question().getId()).position(position++)
                    .blockNo((short) e.blockNo()).typeId(e.question().getTypeId()).points(e.points())
                    .locked(false).build());
        }
        answerRepository.saveAll(answers);
        return buildView(session, answers, picked.warnings(), now);
    }

    /** Làm lại: tạo phiên mới cùng cấu hình (cùng đề, hoặc random/chọn lại câu cho cùng dạng bài và số câu). */
    @Transactional
    public SessionView retry(UUID userId, UUID id) {
        PracticeSession old = getOwned(userId, id);
        StartRequest req = new StartRequest(old.getMode(), old.getLevel(), old.getTypeIds(),
                old.getQuestionCount() == null ? null : old.getQuestionCount().intValue(),
                old.getTimeLimitMin() == null ? null : old.getTimeLimitMin().intValue(), old.getFeedbackMode(),
                old.getTestId(), old.isRandom());
        return create(userId, req);
    }

    private BlockState practiceBlock(Integer timeLimitMin, ZonedDateTime now) {
        return new BlockState(1, "Luyện tập", timeLimitMin, now, timeLimitMin == null ? null : now.plusMinutes(timeLimitMin), null);
    }

    /** Chỉ lấy các khối thật sự có câu hỏi; khối đầu tiên bắt đầu ngay, các khối sau đợi người dùng bấm bắt đầu. */
    private List<BlockState> examBlocks(short level, QuestionPicker.Picked picked, ZonedDateTime now) {
        Set<Integer> used = picked.entries().stream().map(QuestionPicker.Entry::blockNo).collect(Collectors.toSet());
        List<BlockState> blocks = new ArrayList<>();
        for (Block b : examConfig.findBlocks(level)) {
            if (!used.contains(b.blockNo())) {
                continue;
            }
            boolean first = blocks.isEmpty();
            blocks.add(new BlockState(b.blockNo(), b.name(), b.durationMin(), first ? now : null,
                    first ? now.plusMinutes(b.durationMin()) : null, null));
        }
        if (blocks.isEmpty()) {
            blocks.add(new BlockState(1, "Làm bài", null, now, null, null));
        }
        return blocks;
    }

    // ─── Xem phiên ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SessionView get(UUID userId, UUID id) {
        PracticeSession session = getOwned(userId, id);
        return buildView(session, answerRepository.findBySessionIdOrderByPositionAsc(id), List.of(), ZonedDateTime.now());
    }

    private SessionView buildView(PracticeSession session, List<PracticeAnswer> answers, List<String> warnings,
                                  ZonedDateTime now) {
        boolean submitted = PracticeSession.STATUS_SUBMITTED.equals(session.getStatus());
        boolean exam = PracticeSession.MODE_EXAM.equals(session.getMode());

        List<BlockView> blocks = new ArrayList<>();
        Integer current = null;
        for (BlockState b : session.getBlockState()) {
            String status = blockStatus(b, now);
            if (status.equals("active")) {
                current = b.blockNo();
            }
            int count = (int) answers.stream().filter(a -> a.getBlockNo().intValue() == b.blockNo()).count();
            blocks.add(new BlockView(b.blockNo(), b.name(), b.durationMin(), count, status, b.startedAt(), b.deadline()));
        }

        if (submitted) {
            return new SessionView(session.getId(), session.getMode(), session.getLevel(), session.getTitle(),
                    session.getStatus(), session.getFeedbackMode(), now, session.getStartedAt(), null, blocks,
                    List.of(), List.of(), List.of());
        }

        Map<Integer, String> statusByBlock = blocks.stream().collect(Collectors.toMap(BlockView::blockNo, BlockView::status));
        List<Long> questionIds = answers.stream().map(PracticeAnswer::getQuestionId).filter(q -> q != null).toList();
        Map<Long, Question> questions = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Short, QuestionType> types = typesById();
        Map<Integer, Integer> mondaiNos = mondaiNumbers(answers);

        List<QuestionView> views = new ArrayList<>();
        Set<Long> groupIds = new java.util.LinkedHashSet<>();
        for (PracticeAnswer a : answers) {
            Question q = questions.get(a.getQuestionId());
            boolean hidden = exam && !statusByBlock.getOrDefault((int) a.getBlockNo(), "active").equals("active");
            QuestionType type = types.get(a.getTypeId());
            if (q == null || hidden) {
                views.add(new QuestionView(a.getPosition(), a.getBlockNo(), true, null, null, null,
                        type == null ? null : type.getSection(), null, null, null, null, a.getSelected(),
                        a.isLocked(), null, null, null, null, null, null));
                continue;
            }
            if (q.getGroupId() != null) {
                groupIds.add(q.getGroupId());
            }
            boolean reveal = a.isLocked();
            views.add(new QuestionView(a.getPosition(), a.getBlockNo(), false, q.getId(),
                    type == null ? null : type.getCode(), type == null ? null : type.getNameVi(),
                    type == null ? null : type.getSection(), q.getGroupId(), q.getQuestion(), q.getOptions(),
                    q.getAudioUrl(), a.getSelected(), a.isLocked(),
                    reveal ? a.getCorrect() : null, reveal ? q.getCorrect() : null, reveal ? q.getExplanation() : null,
                    mondaiNos.get((int) a.getPosition()), type == null ? null : type.getInstructionJa(),
                    type == null ? null : type.getInstructionVi()));
        }

        List<GroupView> groups = groupRepository.findAllById(groupIds).stream()
                .map(g -> new GroupView(g.getId(), g.getTitle(), g.getPassage(), g.getAudioUrl(), g.getImageUrl()))
                .toList();

        return new SessionView(session.getId(), session.getMode(), session.getLevel(), session.getTitle(),
                session.getStatus(), session.getFeedbackMode(), now, session.getStartedAt(), current, blocks, views,
                groups, warnings);
    }

    /**
     * Số "問題 N" của từng câu: trong mỗi khối thi, mỗi lần đổi sang dạng bài khác thì sang 問題 kế tiếp
     * (các câu liền nhau cùng dạng bài, hoặc cùng đoạn văn, thuộc cùng 1 問題). Key là vị trí câu trong phiên.
     */
    static Map<Integer, Integer> mondaiNumbers(List<PracticeAnswer> answers) {
        Map<Integer, Integer> result = new HashMap<>();
        Map<Integer, Integer> counterByBlock = new HashMap<>();
        Map<Integer, Short> lastTypeByBlock = new HashMap<>();
        for (PracticeAnswer a : answers) {
            int block = a.getBlockNo();
            Short last = lastTypeByBlock.get(block);
            if (last == null || !last.equals(a.getTypeId())) {
                counterByBlock.merge(block, 1, Integer::sum);
                lastTypeByBlock.put(block, a.getTypeId());
            }
            result.put((int) a.getPosition(), counterByBlock.get(block));
        }
        return result;
    }

    // ─── Trả lời ────────────────────────────────────────────────────────

    @Transactional
    public AnswerResult answer(UUID userId, UUID id, long questionId, String selected) {
        PracticeSession session = getInProgress(userId, id);
        PracticeAnswer answer = answerRepository.findBySessionIdAndQuestionId(id, questionId)
                .orElseThrow(() -> new NotFoundException("Câu hỏi không thuộc phiên này"));

        BlockState block = findBlock(session, answer.getBlockNo());
        ZonedDateTime now = ZonedDateTime.now();
        if (!isAcceptingAnswers(block, now)) {
            throw new IllegalArgumentException("Đã hết giờ hoặc khối thi này đã đóng");
        }
        if (answer.isLocked()) {
            throw new IllegalArgumentException("Câu này đã xem đáp án, không đổi được nữa");
        }

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("Câu hỏi không còn trong kho"));
        String value = selected == null || selected.isBlank() ? null : selected.trim().toUpperCase();
        if (value != null && (value.length() != 1 || value.charAt(0) < 'A'
                || value.charAt(0) >= 'A' + question.getOptions().size())) {
            throw new IllegalArgumentException("Đáp án không hợp lệ");
        }

        answer.setSelected(value);
        answer.setAnsweredAt(now);

        boolean instant = "instant".equals(session.getFeedbackMode());
        if (instant && value != null) {
            boolean correct = value.equals(question.getCorrect());
            answer.setCorrect(correct);
            answer.setLocked(true);
            answerRepository.save(answer);
            return new AnswerResult(true, true, correct, question.getCorrect(), question.getExplanation());
        }
        answerRepository.save(answer);
        return new AnswerResult(true, false, null, null, null);
    }

    // ─── Khối thi ───────────────────────────────────────────────────────

    @Transactional
    public SessionView startBlock(UUID userId, UUID id, int blockNo) {
        PracticeSession session = getInProgress(userId, id);
        ZonedDateTime now = ZonedDateTime.now();
        List<BlockState> blocks = new ArrayList<>(session.getBlockState());

        int index = indexOfBlock(blocks, blockNo);
        BlockState block = blocks.get(index);
        if (block.startedAt() != null) {
            throw new IllegalArgumentException("Khối thi này đã bắt đầu");
        }
        if (index > 0 && !isClosed(blocks.get(index - 1), now)) {
            throw new IllegalArgumentException("Hãy nộp hoặc đợi hết giờ khối trước");
        }
        blocks.set(index, new BlockState(block.blockNo(), block.name(), block.durationMin(), now,
                block.durationMin() == null ? null : now.plusMinutes(block.durationMin()), null));
        session.setBlockState(blocks);
        sessionRepository.save(session);
        return buildView(session, answerRepository.findBySessionIdOrderByPositionAsc(id), List.of(), now);
    }

    @Transactional
    public SessionView submitBlock(UUID userId, UUID id, int blockNo) {
        PracticeSession session = getInProgress(userId, id);
        ZonedDateTime now = ZonedDateTime.now();
        List<BlockState> blocks = new ArrayList<>(session.getBlockState());
        int index = indexOfBlock(blocks, blockNo);
        BlockState block = blocks.get(index);
        if (block.startedAt() == null) {
            throw new IllegalArgumentException("Khối thi này chưa bắt đầu");
        }
        if (!isClosed(block, now)) {
            blocks.set(index, new BlockState(block.blockNo(), block.name(), block.durationMin(), block.startedAt(),
                    block.deadline(), now));
            session.setBlockState(blocks);
            sessionRepository.save(session);
        }
        return buildView(session, answerRepository.findBySessionIdOrderByPositionAsc(id), List.of(), now);
    }

    // ─── Nộp bài và chấm ────────────────────────────────────────────────

    @Transactional
    public Result submit(UUID userId, UUID id) {
        PracticeSession session = getOwned(userId, id);
        if (PracticeSession.STATUS_SUBMITTED.equals(session.getStatus())) {
            return storedResult(session);
        }
        ZonedDateTime now = ZonedDateTime.now();

        List<BlockState> closed = new ArrayList<>();
        for (BlockState b : session.getBlockState()) {
            closed.add(b.submittedAt() == null
                    ? new BlockState(b.blockNo(), b.name(), b.durationMin(), b.startedAt(), b.deadline(), now) : b);
        }
        session.setBlockState(closed);

        List<PracticeAnswer> answers = answerRepository.findBySessionIdOrderByPositionAsc(id);
        Map<Long, Question> questions = questionRepository.findAllById(
                answers.stream().map(PracticeAnswer::getQuestionId).filter(q -> q != null).toList()).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Short, QuestionType> types = typesById();
        Map<Integer, Integer> mondaiNos = mondaiNumbers(answers);

        List<ResultQuestion> resultQuestions = new ArrayList<>();
        List<ScoreCalculator.Item> scoreItems = new ArrayList<>();
        Map<Short, int[]> typeCounts = new TreeMap<>();
        Set<Long> groupIds = new java.util.LinkedHashSet<>();
        int correctCount = 0;
        int unanswered = 0;

        for (PracticeAnswer a : answers) {
            Question q = questions.get(a.getQuestionId());
            if (q == null) {
                continue;
            }
            boolean correct = a.getSelected() != null && a.getSelected().equals(q.getCorrect());
            a.setCorrect(correct);
            a.setLocked(true);
            if (a.getSelected() == null) {
                unanswered++;
            }
            if (correct) {
                correctCount++;
            }
            QuestionType type = types.get(a.getTypeId());
            if (q.getGroupId() != null) {
                groupIds.add(q.getGroupId());
            }
            String section = type == null ? "" : type.getSection();
            scoreItems.add(new ScoreCalculator.Item(section, a.getPoints(), correct));
            int[] counts = typeCounts.computeIfAbsent(a.getTypeId(), k -> new int[2]);
            counts[1]++;
            if (correct) {
                counts[0]++;
            }
            resultQuestions.add(new ResultQuestion(a.getPosition(), a.getBlockNo(), q.getId(),
                    type == null ? null : type.getCode(), type == null ? null : type.getNameVi(), section,
                    q.getGroupId(), q.getQuestion(), q.getOptions(), q.getAudioUrl(), a.getSelected(), q.getCorrect(),
                    correct, q.getExplanation(), a.getPoints().doubleValue(), mondaiNos.get((int) a.getPosition()),
                    type == null ? null : type.getInstructionJa(), type == null ? null : type.getInstructionVi()));
        }
        answerRepository.saveAll(answers);

        List<ResultGroup> resultGroups = groupRepository.findAllById(groupIds).stream()
                .map(g -> new ResultGroup(g.getId(), g.getTitle(), g.getPassage(), g.getAudioUrl(), g.getImageUrl()))
                .toList();
        List<TypeStat> byType = typeCounts.entrySet().stream().map(e -> {
            QuestionType t = types.get(e.getKey());
            return new TypeStat(t == null ? null : t.getCode(), t == null ? null : t.getNameVi(),
                    t == null ? null : t.getSection(), e.getValue()[0], e.getValue()[1]);
        }).toList();

        JlptScore jlpt = null;
        if (PracticeSession.MODE_EXAM.equals(session.getMode())) {
            ScoreCalculator.Result r = ScoreCalculator.calculate(scoreItems,
                    examConfig.findScoringSections(session.getLevel()), examConfig.findPassScore(session.getLevel()).orElse(null));
            jlpt = new JlptScore(r.total(), r.maxTotal(), r.passScore(), r.passed(), r.complete(),
                    r.sections().stream().map(s -> new SectionScoreView(s.code(), s.score(), s.max(), s.min(), s.belowMin())).toList());
            session.setScore(BigDecimal.valueOf(r.total()));
            session.setMaxScore(BigDecimal.valueOf(r.maxTotal()));
        } else {
            session.setScore(BigDecimal.valueOf(correctCount));
            session.setMaxScore(BigDecimal.valueOf(resultQuestions.size()));
        }

        Result result = new Result(session.getId(), session.getMode(), session.getLevel(), session.getTitle(),
                session.getStartedAt(), now, correctCount, resultQuestions.size(), unanswered, jlpt, byType,
                resultQuestions, resultGroups);

        session.setStatus(PracticeSession.STATUS_SUBMITTED);
        session.setFinishedAt(now);
        session.setSnapshot(objectMapper.convertValue(result, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
        }));
        sessionRepository.save(session);
        return result;
    }

    @Transactional(readOnly = true)
    public Result result(UUID userId, UUID id) {
        PracticeSession session = getOwned(userId, id);
        if (!PracticeSession.STATUS_SUBMITTED.equals(session.getStatus())) {
            throw new IllegalArgumentException("Phiên này chưa nộp bài");
        }
        return storedResult(session);
    }

    private Result storedResult(PracticeSession session) {
        return objectMapper.convertValue(session.getSnapshot(), Result.class);
    }

    // ─── Lịch sử ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SessionSummary> list(UUID userId, String mode) {
        List<PracticeSession> sessions = mode == null || mode.isBlank()
                ? sessionRepository.findByUserIdOrderByStartedAtDesc(userId)
                : sessionRepository.findByUserIdAndModeOrderByStartedAtDesc(userId, mode);
        Map<UUID, long[]> counts = new HashMap<>();
        if (!sessions.isEmpty()) {
            for (Object[] row : answerRepository.countBySessions(sessions.stream().map(PracticeSession::getId).toList())) {
                counts.put((UUID) row[0], new long[]{(Long) row[1], row[2] == null ? 0 : ((Number) row[2]).longValue()});
            }
        }
        return sessions.stream().map(s -> {
            long[] c = counts.getOrDefault(s.getId(), new long[]{0, 0});
            return new SessionSummary(s.getId(), s.getMode(), s.getLevel(), s.getTitle(), s.getStatus(),
                    s.getScore() == null ? null : s.getScore().doubleValue(),
                    s.getMaxScore() == null ? null : s.getMaxScore().doubleValue(), (int) c[1], (int) c[0],
                    s.getStartedAt(), s.getFinishedAt());
        }).toList();
    }

    // ─── Tiện ích ───────────────────────────────────────────────────────

    private PracticeSession getOwned(UUID userId, UUID id) {
        return sessionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiên làm bài"));
    }

    private PracticeSession getInProgress(UUID userId, UUID id) {
        PracticeSession session = getOwned(userId, id);
        if (!PracticeSession.STATUS_IN_PROGRESS.equals(session.getStatus())) {
            throw new IllegalArgumentException("Phiên này đã nộp bài");
        }
        return session;
    }

    private BlockState findBlock(PracticeSession session, int blockNo) {
        return session.getBlockState().get(indexOfBlock(session.getBlockState(), blockNo));
    }

    private int indexOfBlock(List<BlockState> blocks, int blockNo) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).blockNo() == blockNo) {
                return i;
            }
        }
        throw new NotFoundException("Không có khối thi số " + blockNo);
    }

    private String blockStatus(BlockState b, ZonedDateTime now) {
        if (b.startedAt() == null) {
            return "pending";
        }
        return isClosed(b, now) ? "closed" : "active";
    }

    private boolean isClosed(BlockState b, ZonedDateTime now) {
        return b.submittedAt() != null || (b.deadline() != null && now.isAfter(b.deadline()));
    }

    // Server là nơi giữ deadline: câu trả lời đến sau deadline (cộng vài giây bù trễ mạng) bị từ chối
    private boolean isAcceptingAnswers(BlockState b, ZonedDateTime now) {
        if (b.startedAt() == null || b.submittedAt() != null) {
            return false;
        }
        return b.deadline() == null || !now.isAfter(b.deadline().plusSeconds(DEADLINE_GRACE_SECONDS));
    }

    private Map<Short, QuestionType> typesById() {
        return typeRepository.findAll().stream().collect(Collectors.toMap(QuestionType::getId, Function.identity()));
    }
}
