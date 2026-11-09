package com.nihonmukae.backend.shelf;

import com.nihonmukae.backend.auth.UserRepository;
import com.nihonmukae.backend.shared.entity.ShelfItem;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import com.nihonmukae.backend.shelf.dto.ShelfItemRequest;
import com.nihonmukae.backend.shelf.dto.ShelfItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ShelfService {

    private static final int MAX_TITLE_LENGTH = 60;
    private static final int EXCERPT_LENGTH = 120;
    public static final int MAX_CONTENT_LENGTH = 20000;

    private final ShelfRepository shelfRepository;
    private final UserRepository userRepository;
    private final FuriganaService furiganaService;

    public ShelfService(ShelfRepository shelfRepository,
                        UserRepository userRepository,
                        FuriganaService furiganaService) {
        this.shelfRepository = shelfRepository;
        this.userRepository = userRepository;
        this.furiganaService = furiganaService;
    }

    @Transactional(readOnly = true)
    public List<ShelfItemResponse> list(UUID userId) {
        return shelfRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShelfItemResponse get(UUID userId, UUID id) {
        return toDetail(getOwned(userId, id));
    }

    @Transactional
    public ShelfItemResponse create(UUID userId, ShelfItemRequest req) {
        String content = normalize(req.getContent());
        ShelfItem item = ShelfItem.builder()
                .user(userRepository.getReferenceById(userId))
                .title(resolveTitle(req.getTitle(), content))
                .content(content)
                .contentTokens(furiganaService.annotate(content))
                .build();
        return toDetail(shelfRepository.saveAndFlush(item));
    }

    /** Bài My Shelf dựng từ một phiên luyện thi: bản sao độc lập, không còn liên kết với kho câu hỏi. */
    @Transactional
    public ShelfItemResponse createFromPractice(UUID userId, UUID sessionId, String title, String content) {
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Nội dung quá dài để lưu, hãy chỉ lưu câu sai hoặc từng câu");
        }
        ShelfItem item = ShelfItem.builder()
                .user(userRepository.getReferenceById(userId))
                .title(resolveTitle(title, content))
                .content(content)
                .contentTokens(furiganaService.annotate(content))
                .sourceType("practice")
                .sourceSessionId(sessionId)
                .build();
        return toDetail(shelfRepository.saveAndFlush(item));
    }

    // Sửa nội dung thì tách từ và furigana lại từ đầu
    @Transactional
    public ShelfItemResponse update(UUID userId, UUID id, ShelfItemRequest req) {
        ShelfItem item = getOwned(userId, id);
        String content = normalize(req.getContent());
        item.setTitle(resolveTitle(req.getTitle(), content));
        item.setContent(content);
        item.setContentTokens(furiganaService.annotate(content));
        return toDetail(shelfRepository.saveAndFlush(item));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        shelfRepository.delete(getOwned(userId, id));
    }

    private ShelfItem getOwned(UUID userId, UUID id) {
        return shelfRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bài này"));
    }

    // Thống nhất xuống dòng kiểu \n và bỏ khoảng trắng thừa hai đầu
    private String normalize(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private String resolveTitle(String title, String content) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        String firstLine = content.lines().filter(l -> !l.isBlank()).findFirst().orElse(content).trim();
        return firstLine.length() > MAX_TITLE_LENGTH ? firstLine.substring(0, MAX_TITLE_LENGTH) + "…" : firstLine;
    }

    private ShelfItemResponse toSummary(ShelfItem item) {
        String flat = item.getContent().replaceAll("\\s+", " ");
        return ShelfItemResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .excerpt(flat.length() > EXCERPT_LENGTH ? flat.substring(0, EXCERPT_LENGTH) + "…" : flat)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private ShelfItemResponse toDetail(ShelfItem item) {
        return ShelfItemResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .content(item.getContent())
                .tokens(item.getContentTokens())
                .titleTokens(furiganaService.annotate(item.getTitle()))
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
