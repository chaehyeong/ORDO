package com.ordo.importer.service;

import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.importer.dto.ImportPreviewResponse;
import com.ordo.importer.service.FileFormatDetector.Detected;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 성적·시간표 파일 → 미리보기 초안 (저장 안 함, 명세 4.9).
 * 파일은 메모리에서만 다루고 버린다. 학번·이름·성적이 로그에 남지 않도록 내용을 담은 예외는 원인 없이 바꿔 던진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImportService {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private final UserRepository userRepository;

    public ImportPreviewResponse preview(Long userId, MultipartFile file) {
        byte[] bytes = readAtMost(file);
        Detected detected = FileFormatDetector.detect(bytes);
        ImportPreviewResponse response;
        String studentNumber;
        try {
            RawDocument doc = switch (detected.format()) {
                case XLSX -> XlsxReader.read(bytes);
                case CSV -> DelimitedTextReader.read(detected.text(), ',');
                case TXT -> DelimitedTextReader.read(detected.text(), '\t');
            };
            String title = RawDocument.compact(doc.title());
            if (title.equals(EnrollmentInterpreter.TITLE)) {
                EnrollmentInterpreter.Result result = EnrollmentInterpreter.interpret(doc);
                studentNumber = result.studentNumber();
                response = new ImportPreviewResponse("TIMETABLE", detected.format().name(), result.draft(), null, result.warnings());
            } else if (title.equals(TranscriptInterpreter.TITLE)) {
                TranscriptInterpreter.Result result = TranscriptInterpreter.interpret(doc);
                studentNumber = result.studentNumber();
                response = new ImportPreviewResponse("GRADES", detected.format().name(), null, result.draft(), result.warnings());
            } else {
                throw new BusinessException(ErrorCode.IMPORT_UNRECOGNIZED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // 원인 예외 메시지에 파일 내용이 들어 있을 수 있어 연결하지 않는다
            throw new BusinessException(ErrorCode.IMPORT_UNRECOGNIZED);
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (studentNumber == null || !studentNumber.strip().equals(user.getStudentNumber())) {
            throw new BusinessException(ErrorCode.IMPORT_STUDENT_MISMATCH);
        }
        return response;
    }

    private static byte[] readAtMost(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.IMPORT_TOO_LARGE);
        }
        try (InputStream in = file.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new BusinessException(ErrorCode.IMPORT_TOO_LARGE);
            }
            return bytes;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IMPORT_UNRECOGNIZED);
        }
    }
}
