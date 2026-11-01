package com.nihonmukae.backend.question;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihonmukae.backend.question.dto.ImportDtos.CommitResponse;
import com.nihonmukae.backend.question.dto.ImportFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

/**
 * Nạp câu hỏi mẫu (tự soạn, không phải đề thật) khi chạy dev để có dữ liệu thử màn JLPT.
 * Chỉ chạy khi bật app.jlpt.seed-sample=true. Mỗi lần khởi động nó đồng bộ lại dữ liệu mẫu: tắt các dòng
 * có source "sample" rồi nạp lại từ file (câu không đổi được bật lại, câu đã sửa thành dòng mới), nên sửa
 * file mẫu là có hiệu lực ngay. Không đụng tới câu import từ người dùng. Dùng đúng luồng import JSON.
 */
@Component
@ConditionalOnProperty(name = "app.jlpt.seed-sample", havingValue = "true")
public class JlptSampleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JlptSampleSeeder.class);
    private static final List<String> FILES = List.of("jlpt/sample-n5.json", "jlpt/sample-n3.json", "jlpt/sample-extra.json");

    private final QuestionRepository questionRepository;
    private final QuestionGroupRepository groupRepository;
    private final QuestionImportService importService;
    private final ObjectMapper objectMapper;

    public JlptSampleSeeder(QuestionRepository questionRepository,
                            QuestionGroupRepository groupRepository,
                            QuestionImportService importService,
                            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.groupRepository = groupRepository;
        this.importService = importService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        questionRepository.deactivateBySource(QuestionImportService.SOURCE_SAMPLE);
        groupRepository.deactivateBySource(QuestionImportService.SOURCE_SAMPLE);
        for (String path : FILES) {
            try (InputStream in = new ClassPathResource(path).getInputStream()) {
                ImportFile file = objectMapper.readValue(in, ImportFile.class);
                CommitResponse result = importService.importFile(null, file, true, QuestionImportService.SOURCE_SAMPLE);
                log.info("Đã nạp dữ liệu mẫu {}: {} câu, {} nhóm", path, result.createdQuestions(), result.createdGroups());
            }
        }
    }
}
