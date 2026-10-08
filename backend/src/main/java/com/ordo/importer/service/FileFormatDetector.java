package com.ordo.importer.service;

import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 확장자가 아니라 파일 앞부분 바이트로 형식을 정한다. xlsx·csv·txt 가 아니면 "엑셀로 저장" 안내로 거절.
 * PDF 도 거절한다: 글자는 읽히지만 표 구조(2줄 칸·세로 병합·빈 칸)가 사라져 정확히 읽을 수 없다(명세 9장 19).
 */
final class FileFormatDetector {

    enum Format { XLSX, CSV, TXT }

    /** text 는 CSV·TXT 일 때만 (디코딩한 내용) */
    record Detected(Format format, String text) {
    }

    private static final Charset MS949 = Charset.forName("MS949");

    private FileFormatDetector() {
    }

    static Detected detect(byte[] bytes) {
        if (startsWith(bytes, 'P', 'K', 3, 4)) {
            if (isXlsx(bytes)) {
                return new Detected(Format.XLSX, null);
            }
            throw unsupported();  // docx·hwpx·ozd 등 다른 압축 파일
        }
        if (startsWith(bytes, '%', 'P', 'D', 'F', '-')
                || startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0)    // OLE2: 옛 xls, hwp, doc
                || startsWith(bytes, 0x89, 'P', 'N', 'G') || startsWith(bytes, 0xFF, 0xD8, 0xFF)
                || startsWith(bytes, 'G', 'I', 'F', '8') || startsWith(bytes, 'B', 'M')
                || (startsWith(bytes, 'R', 'I', 'F', 'F') && bytes.length > 12 && bytes[8] == 'W')) {
            throw unsupported();
        }
        String text = decodeText(bytes);
        if (text == null || text.chars().anyMatch(c -> c < 0x20 && c != '\t' && c != '\n' && c != '\r')) {
            throw unsupported();  // 이진 파일
        }
        return new Detected(text.contains("\t") ? Format.TXT : Format.CSV, text);
    }

    /** UTF-8(BOM 허용)로 먼저, 안 되면 MS949 로 엄격하게 읽는다. 둘 다 안 되면 null */
    static String decodeText(byte[] bytes) {
        int offset = startsWith(bytes, 0xEF, 0xBB, 0xBF) ? 3 : 0;
        for (Charset charset : new Charset[]{StandardCharsets.UTF_8, MS949}) {
            try {
                return charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                        .toString();
            } catch (CharacterCodingException e) {
                offset = 0;  // MS949 는 처음부터
            }
        }
        return null;
    }

    private static boolean isXlsx(byte[] bytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (entry.getName().equals("xl/workbook.xml")) {
                    return true;
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
        return false;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static BusinessException unsupported() {
        return new BusinessException(ErrorCode.IMPORT_UNSUPPORTED_FORMAT);
    }
}
