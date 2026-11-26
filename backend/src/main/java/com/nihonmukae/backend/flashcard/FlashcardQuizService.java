package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.dictionary.DictionaryRepository;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Entry;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Example;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Meaning;
import com.nihonmukae.backend.flashcard.dto.QuizDtos.Attempt;
import com.nihonmukae.backend.flashcard.dto.QuizDtos.AttemptRequest;
import com.nihonmukae.backend.flashcard.dto.QuizDtos.QuizQuestion;
import com.nihonmukae.backend.shared.entity.Flashcard;
import com.nihonmukae.backend.shared.entity.FlashcardDeck;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Quiz 4 đáp án từ một bộ thẻ: chọn nghĩa, chọn cách đọc, điền từ vào câu ví dụ. Không đụng tới lịch ôn SM-2.
 * Câu hỏi sinh mới mỗi lần, máy khách tự chấm (đây là tự học) rồi gửi điểm lên để lưu lịch sử.
 */
@Service
public class FlashcardQuizService {

    public static final List<String> KINDS = List.of("meaning", "reading", "cloze");
    static final int MAX_COUNT = 100;
    private static final int KEEP_ATTEMPTS = 200;
    private static final int POOL_SIZE = 300;
    private static final String BLANK = "（　　）";

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final DictionaryRepository dictionary;
    private final NamedParameterJdbcTemplate jdbc;

    public FlashcardQuizService(DeckRepository deckRepository, CardRepository cardRepository,
                                DictionaryRepository dictionary, NamedParameterJdbcTemplate jdbc) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.dictionary = dictionary;
        this.jdbc = jdbc;
    }

    /** Dữ liệu một thẻ dùng để hỏi. reading và cloze null khi thẻ không hỏi được dạng đó. */
    record Item(UUID cardId, String word, String meaning, String reading, List<String> otherReadings, String pos,
                Cloze cloze) {
    }

    record Cloze(String sentence, String answer, String translation) {
    }

    // ─── Sinh câu hỏi ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuizQuestion> generate(UUID userId, UUID deckId, List<String> kinds, Integer count) {
        ownedDeck(userId, deckId);
        Set<String> wanted = new LinkedHashSet<>();
        for (String k : kinds == null ? List.<String>of() : kinds) {
            if (KINDS.contains(k)) {
                wanted.add(k);
            }
        }
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException("Hãy chọn ít nhất một dạng câu hỏi");
        }
        int limit = count == null || count <= 0 ? MAX_COUNT : Math.min(count, MAX_COUNT);

        List<Flashcard> cards = cardRepository.findByDeckIdOrderByCreatedAtAsc(deckId);
        Map<Long, Entry> entries = byId(dictionary.load(
                cards.stream().map(Flashcard::getWordId).filter(Objects::nonNull).distinct().toList()));
        List<Item> items = new ArrayList<>();
        for (Flashcard c : cards) {
            Item item = toItem(c, c.getWordId() == null ? null : entries.get(c.getWordId()));
            if (item != null) {
                items.add(item);
            }
        }

        // Đáp án nhiễu: thẻ khác trong bộ, từ cùng cấp JLPT, và từ có chung chữ Hán (cho câu hỏi cách đọc)
        Set<Short> levels = new HashSet<>();
        Set<String> kanji = new HashSet<>();
        for (Entry e : entries.values()) {
            if (e.jlptLevel() != null) {
                levels.add(e.jlptLevel());
            }
            if (e.kanji() != null) {
                e.kanji().codePoints().filter(FlashcardQuizService::isKanji)
                        .forEach(cp -> kanji.add(new String(Character.toChars(cp))));
            }
        }
        List<Entry> levelPool = dictionary.load(dictionary.randomQuizIds(levels, POOL_SIZE));
        List<Entry> kanjiPool = wanted.contains("reading")
                ? dictionary.load(dictionary.idsSharingKanji(kanji, POOL_SIZE)) : List.of();

        return build(items, wanted, limit, levelPool, kanjiPool, ThreadLocalRandom.current());
    }

    /** Phần không chạm database, để test được. */
    static List<QuizQuestion> build(List<Item> items, Set<String> wanted, int limit, List<Entry> levelPool,
                                    List<Entry> kanjiPool, Random random) {
        List<Item> order = new ArrayList<>(items);
        Collections.shuffle(order, random);
        Map<String, Integer> used = new HashMap<>();
        List<QuizQuestion> out = new ArrayList<>();
        for (Item item : order) {
            if (out.size() >= limit) {
                break;
            }
            // Dạng thẻ này hỏi được, ưu tiên dạng đang ít câu nhất để các dạng chia đều
            List<String> possible = new ArrayList<>();
            for (String k : wanted) {
                if (k.equals("meaning") || (k.equals("reading") && item.reading() != null)
                        || (k.equals("cloze") && item.cloze() != null)) {
                    possible.add(k);
                }
            }
            Collections.shuffle(possible, random);
            possible.sort((a, b) -> used.getOrDefault(a, 0) - used.getOrDefault(b, 0));
            for (String kind : possible) {
                QuizQuestion q = switch (kind) {
                    case "meaning" -> meaningQuestion(item, items, levelPool, random);
                    case "reading" -> readingQuestion(item, items, levelPool, kanjiPool, random);
                    default -> clozeQuestion(item, items, levelPool, random);
                };
                if (q != null) {
                    out.add(q);
                    used.merge(kind, 1, Integer::sum);
                    break;
                }
            }
        }
        return out;
    }

    private static QuizQuestion meaningQuestion(Item item, List<Item> deck, List<Entry> pool, Random random) {
        List<String> samePos = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (Item d : deck) {
            if (!d.cardId().equals(item.cardId())) {
                others.add(d.meaning());
            }
        }
        for (Entry e : pool) {
            String m = firstMeaning(e);
            if (m != null && !e.headword().equals(item.word())) {
                (Objects.equals(e.pos(), item.pos()) ? samePos : others).add(m);
            }
        }
        List<String> wrong = pick(List.of(samePos, others), 3, m -> !overlaps(m, item.meaning()), random);
        return wrong.size() < 3 ? null : question(item, "meaning", item.word(), item.meaning(), wrong, null, random);
    }

    private static QuizQuestion readingQuestion(Item item, List<Item> deck, List<Entry> pool, List<Entry> kanjiPool,
                                                Random random) {
        Set<String> wordKanji = new HashSet<>();
        item.word().codePoints().filter(FlashcardQuizService::isKanji)
                .forEach(cp -> wordKanji.add(new String(Character.toChars(cp))));
        List<String> sharing = new ArrayList<>();
        List<String> similar = new ArrayList<>();
        for (Entry e : kanjiPool) {
            if (e.kanji() != null && !e.kanji().equals(item.word()) && wordKanji.stream().anyMatch(e.kanji()::contains)) {
                sharing.add(e.kana());
            }
        }
        for (Item d : deck) {
            if (d.reading() != null && !d.cardId().equals(item.cardId())) {
                similar.add(d.reading());
            }
        }
        for (Entry e : pool) {
            if (e.kanji() != null && Math.abs(e.kana().length() - item.reading().length()) <= 1) {
                similar.add(e.kana());
            }
        }
        Set<String> correct = new HashSet<>(item.otherReadings());
        correct.add(item.reading());
        List<String> wrong = pick(List.of(sharing, similar), 3, r -> !correct.contains(r), random);
        return wrong.size() < 3 ? null : question(item, "reading", item.word(), item.reading(), wrong, null, random);
    }

    private static QuizQuestion clozeQuestion(Item item, List<Item> deck, List<Entry> pool, Random random) {
        Cloze c = item.cloze();
        boolean kanjiAnswer = c.answer().codePoints().anyMatch(FlashcardQuizService::isKanji);
        List<String> samePos = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (Item d : deck) {
            if (!d.cardId().equals(item.cardId()) && !overlaps(d.meaning(), item.meaning())) {
                (Objects.equals(d.pos(), item.pos()) ? samePos : others).add(d.word());
            }
        }
        for (Entry e : pool) {
            String m = firstMeaning(e);
            if (m != null && !overlaps(m, item.meaning())) {
                (Objects.equals(e.pos(), item.pos()) ? samePos : others).add(e.headword());
            }
        }
        // Cùng kiểu chữ với đáp án đúng (có Hán tự hay toàn kana), để đáp án đúng không lộ vì trông khác các đáp án kia
        Predicate<String> fits = w -> !w.equals(c.answer()) && !c.sentence().contains(w)
                && w.codePoints().anyMatch(FlashcardQuizService::isKanji) == kanjiAnswer;
        List<String> wrong = pick(List.of(samePos, others), 3, fits, random);
        return wrong.size() < 3 ? null
                : question(item, "cloze", c.sentence(), c.answer(), wrong, c.translation(), random);
    }

    private static QuizQuestion question(Item item, String kind, String prompt, String right, List<String> wrong,
                                         String translation, Random random) {
        List<String> options = new ArrayList<>(wrong);
        options.add(right);
        Collections.shuffle(options, random);
        return new QuizQuestion(item.cardId(), kind, prompt, options, options.indexOf(right), item.word(),
                item.reading(), item.meaning(), translation);
    }

    /** Lấy n phần tử khác nhau thỏa điều kiện, ưu tiên danh sách đứng trước, trong mỗi danh sách thì ngẫu nhiên. */
    static List<String> pick(List<List<String>> tiers, int n, Predicate<String> ok, Random random) {
        List<String> chosen = new ArrayList<>();
        for (List<String> tier : tiers) {
            List<String> shuffled = new ArrayList<>(tier);
            Collections.shuffle(shuffled, random);
            for (String s : shuffled) {
                if (chosen.size() >= n) {
                    return chosen;
                }
                if (s != null && !s.isBlank() && ok.test(s)
                        && chosen.stream().noneMatch(x -> x.equals(s) || overlaps(x, s))) {
                    chosen.add(s);
                }
            }
        }
        return chosen;
    }

    // ─── Dữ liệu thẻ ───────────────────────────────────────────────────

    static Item toItem(Flashcard card, Entry entry) {
        if (entry != null) {
            String meaning = firstMeaning(entry);
            if (meaning == null) {
                meaning = clean(card.getBack().split("\n")[0]);
            }
            if (meaning.isBlank()) {
                return null;
            }
            boolean hasKanji = entry.kanji() != null && !entry.kanji().equals(entry.kana());
            String word = entry.headword();
            return new Item(card.getId(), word, meaning, hasKanji ? entry.kana() : null,
                    entry.otherReadings() == null ? List.of() : entry.otherReadings(), entry.pos(),
                    cloze(entry));
        }
        // Thẻ tự viết: chỉ hỏi được nghĩa
        String word = card.getFront().split("\n")[0].trim();
        String meaning = clean(card.getBack().split("\n")[0]);
        if (word.isEmpty() || meaning.isEmpty()) {
            return null;
        }
        return new Item(card.getId(), word, meaning, null, List.of(), null, null);
    }

    /** Câu ví dụ có bản dịch chứa nguyên văn từ (dạng từ điển); từ đã chia (食べた) thì bỏ qua. */
    static Cloze cloze(Entry entry) {
        List<String> forms = new ArrayList<>();
        if (entry.kanji() != null) {
            forms.add(entry.kanji());
        }
        // Từ thuần kana ngắn (1 ký tự) dễ khớp nhầm vào giữa từ khác
        if (entry.kana() != null && entry.kana().length() >= 2) {
            forms.add(entry.kana());
        }
        for (Example x : entry.examples()) {
            if (x.sentence() == null || x.translation() == null || x.translation().isBlank()) {
                continue;
            }
            for (String f : forms) {
                int at = x.sentence().indexOf(f);
                if (at >= 0) {
                    String sentence = x.sentence().substring(0, at) + BLANK + x.sentence().substring(at + f.length());
                    return new Cloze(sentence.trim(), f, x.translation().trim());
                }
            }
        }
        return null;
    }

    static String firstMeaning(Entry e) {
        for (Meaning m : e.meanings()) {
            if ("vi".equals(m.lang()) && m.meaning() != null && !m.meaning().isBlank()) {
                return clean(m.meaning());
            }
        }
        return null;
    }

    static String clean(String s) {
        return s == null ? "" : s.replaceFirst("^◆\\s*", "").replaceFirst("[.。]\\s*$", "").trim();
    }

    /** Hai nghĩa trùng nhau ở ít nhất một ý (tách theo dấu phẩy, chấm phẩy), để không có hai đáp án cùng đúng. */
    static boolean overlaps(String a, String b) {
        Set<String> pa = parts(a);
        for (String p : parts(b)) {
            if (pa.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> parts(String s) {
        Set<String> out = new HashSet<>();
        for (String p : s.toLowerCase(Locale.ROOT).replaceAll("\\([^)]*\\)", "").split("[,;，、/]")) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static boolean isKanji(int cp) {
        return Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN;
    }

    private static Map<Long, Entry> byId(List<Entry> entries) {
        Map<Long, Entry> map = new HashMap<>();
        entries.forEach(e -> map.put(e.id(), e));
        return map;
    }

    private FlashcardDeck ownedDeck(UUID userId, UUID deckId) {
        return deckRepository.findByIdAndUserId(deckId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bộ thẻ"));
    }

    // ─── Lịch sử ───────────────────────────────────────────────────────

    @Transactional
    public Attempt saveAttempt(UUID userId, AttemptRequest req) {
        if (req.deckId() == null || req.total() == null || req.correct() == null) {
            throw new IllegalArgumentException("Thiếu thông tin lượt quiz");
        }
        int total = req.total();
        int correct = req.correct();
        if (total < 1 || total > MAX_COUNT || correct < 0 || correct > total) {
            throw new IllegalArgumentException("Điểm không hợp lệ");
        }
        List<String> kinds = req.kinds() == null ? List.of() : req.kinds().stream().filter(KINDS::contains).distinct().toList();
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("Thiếu dạng câu hỏi");
        }
        FlashcardDeck deck = ownedDeck(userId, req.deckId());
        UUID id = UUID.randomUUID();
        MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("user", userId)
                .addValue("deck", deck.getId()).addValue("name", deck.getName())
                .addValue("kinds", String.join(",", kinds)).addValue("total", total).addValue("correct", correct)
                .addValue("keep", KEEP_ATTEMPTS);
        jdbc.update("""
                INSERT INTO flashcard_quiz_attempts (id, user_id, deck_id, deck_name, kinds, total, correct)
                VALUES (:id, :user, :deck, :name, :kinds, :total, :correct)
                """, p);
        // Chỉ giữ các lượt gần nhất để lịch sử không phình mãi
        jdbc.update("""
                DELETE FROM flashcard_quiz_attempts WHERE user_id = :user AND id NOT IN (
                    SELECT id FROM flashcard_quiz_attempts WHERE user_id = :user ORDER BY created_at DESC LIMIT :keep)
                """, p);
        return attempts(userId, deck.getId(), 1).get(0);
    }

    @Transactional(readOnly = true)
    public List<Attempt> attempts(UUID userId, UUID deckId, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("user", userId).addValue("limit", Math.min(Math.max(limit, 1), 50));
        String deckFilter = "";
        if (deckId != null) {
            p.addValue("deck", deckId);
            deckFilter = " AND deck_id = :deck";
        }
        return jdbc.query("SELECT id, deck_id, deck_name, kinds, total, correct, created_at FROM flashcard_quiz_attempts"
                        + " WHERE user_id = :user" + deckFilter + " ORDER BY created_at DESC LIMIT :limit", p,
                (rs, i) -> new Attempt(rs.getObject("id", UUID.class), rs.getObject("deck_id", UUID.class),
                        rs.getString("deck_name"), Arrays.asList(rs.getString("kinds").split(",")),
                        rs.getInt("total"), rs.getInt("correct"),
                        rs.getObject("created_at", OffsetDateTime.class).atZoneSameInstant(ZoneOffset.UTC)));
    }
}
