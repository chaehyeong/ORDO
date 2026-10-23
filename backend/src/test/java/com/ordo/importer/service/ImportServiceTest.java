package com.ordo.importer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.importer.dto.ImportPreviewResponse;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ImportServiceTest {

    @Mock
    UserRepository userRepository;
    @InjectMocks
    ImportService importService;

    @Test
    void previewsTimetableAndGradesForMyOwnFiles() throws IOException {
        givenStudent("2026000000");

        ImportPreviewResponse enrollment = importService.preview(1L, upload("enrollment.xlsx"));
        ImportPreviewResponse transcript = importService.preview(1L, upload("transcript.txt"));

        assertThat(enrollment.type()).isEqualTo("TIMETABLE");
        assertThat(enrollment.format()).isEqualTo("XLSX");
        assertThat(enrollment.timetable().entries()).hasSize(7);
        assertThat(enrollment.grades()).isNull();
        assertThat(transcript.type()).isEqualTo("GRADES");
        assertThat(transcript.format()).isEqualTo("TXT");
        assertThat(transcript.grades().totalCredits()).isEqualTo(20);
    }

    @Test
    void someoneElsesFileIsRejected() throws IOException {
        givenStudent("2026999999");
        MockMultipartFile enrollment = upload("enrollment.csv");

        assertErrorCode(() -> importService.preview(1L, enrollment), ErrorCode.IMPORT_STUDENT_MISMATCH);
    }

    @Test
    void unsupportedFilesAreRejected() throws IOException {
        MockMultipartFile pdf = upload("enrollment.pdf");
        MockMultipartFile otherZip = file(zipWith("word/document.xml"));
        assertErrorCode(() -> importService.preview(1L, file(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0})),
                ErrorCode.IMPORT_UNSUPPORTED_FORMAT);
        assertErrorCode(() -> importService.preview(1L, file(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 1})),
                ErrorCode.IMPORT_UNSUPPORTED_FORMAT);  // 옛 xls·hwp
        assertErrorCode(() -> importService.preview(1L, otherZip), ErrorCode.IMPORT_UNSUPPORTED_FORMAT);  // xlsx 가 아닌 압축 파일
        assertErrorCode(() -> importService.preview(1L, file(new byte[]{'a', 0, 'b', 1, 2})),
                ErrorCode.IMPORT_UNSUPPORTED_FORMAT);  // 이진 파일
        assertErrorCode(() -> importService.preview(1L, pdf), ErrorCode.IMPORT_UNSUPPORTED_FORMAT);  // PDF 는 받지 않음 (명세 9장 19)
        assertErrorCode(() -> importService.preview(1L, file(new byte[ImportService.MAX_BYTES + 1])),
                ErrorCode.IMPORT_TOO_LARGE);
    }

    @Test
    void unknownDocumentIsUnrecognized() {
        assertErrorCode(() -> importService.preview(1L, file("시간표,2026\n월,화\n".getBytes(StandardCharsets.UTF_8))),
                ErrorCode.IMPORT_UNRECOGNIZED);
    }

    @Test
    void textIsReadAsUtf8OrMs949() {
        String csv = "수강신청확인서\n2026학년도,2학기\n학번:,2026000000\n순번,학수번호-분반,과목명,학점,이수구분,재수강 여부,\"교강사, 강의시간, 강의실\"\n"
                + "1,CSE103-01,객체지향프로그래밍,3,전공필수,,김교수 화15:00-16:50 전205\n";
        givenStudent("2026000000");

        for (Charset charset : new Charset[]{StandardCharsets.UTF_8, Charset.forName("MS949")}) {
            ImportPreviewResponse response = importService.preview(1L, file(csv.getBytes(charset)));
            assertThat(response.timetable().courses().get(0).courseName()).isEqualTo("객체지향프로그래밍");
        }
    }

    @Test
    void personalDataNeverReachesTheLog(CapturedOutput output) throws IOException {
        givenStudent("2026000000");
        for (String name : new String[]{"enrollment.xlsx", "enrollment.csv", "enrollment.txt",
                "transcript.xlsx", "transcript.csv", "transcript.txt"}) {
            importService.preview(1L, upload(name));
        }
        givenStudent("2026999999");
        MockMultipartFile transcript = upload("transcript.xlsx");
        assertErrorCode(() -> importService.preview(1L, transcript), ErrorCode.IMPORT_STUDENT_MISMATCH);

        assertThat(output.getAll()).doesNotContain("홍길동", "2026000000", "이산구조");
    }

    private void givenStudent(String studentNumber) {
        given(userRepository.findById(1L)).willReturn(Optional.of(User.builder().email("haeun@khu.ac.kr")
                .password("encoded").name("홍길동").studentNumber(studentNumber).build()));
    }

    private static MockMultipartFile upload(String name) throws IOException {
        try (InputStream in = ImportServiceTest.class.getResourceAsStream("/import/" + name)) {
            return new MockMultipartFile("file", name, null, in.readAllBytes());
        }
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "upload.bin", null, bytes);
    }

    private static byte[] zipWith(String entryName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write("<w/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static void assertErrorCode(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }
}
