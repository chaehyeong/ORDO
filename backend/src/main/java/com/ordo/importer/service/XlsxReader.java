package com.ordo.importer.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * xlsx → RawDocument. 학교 시스템 xlsx 는 논리적 칸 하나가 병합 영역 하나라서(빈 칸도),
 * "그 행에서 시작하는 병합 영역 1개 + 병합 안 된 값 있는 셀 1개 = 칸 1개"로 열 순서대로 읽으면 csv·txt 와 같은 행이 된다.
 * 여러 행에 걸친 세로 병합(학년도·학기)은 첫 행에만 나온다(csv 와 같음). 열 위치는 하드코딩하지 않는다.
 */
final class XlsxReader {

    static {
        ZipSecureFile.setMinInflateRatio(0.01);           // 압축 폭탄 방어 (POI 기본값을 명시)
        ZipSecureFile.setMaxEntrySize(50L * 1024 * 1024);  // 압축 해제 후 파일 하나 50MB 까지
    }

    private XlsxReader() {
    }

    static RawDocument read(byte[] bytes) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            for (Sheet sheet : workbook) {
                List<CellRangeAddress> merged = sheet.getMergedRegions();
                for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum() && r >= 0; r++) {
                    Row row = sheet.getRow(r);
                    Map<Integer, String> cells = new TreeMap<>();  // 열 번호 → 글자
                    for (CellRangeAddress region : merged) {
                        if (region.getFirstRow() == r) {
                            cells.put(region.getFirstColumn(), text(sheet, region.getFirstRow(), region.getFirstColumn(), formatter));
                        }
                    }
                    if (row != null) {
                        for (Cell cell : row) {
                            int c = cell.getColumnIndex();
                            boolean inRegion = merged.stream().anyMatch(region -> region.isInRange(cell));
                            String value = formatter.formatCellValue(cell).strip();
                            if (!inRegion && !value.isEmpty()) {
                                cells.put(c, value);
                            }
                        }
                    }
                    rows.add(List.copyOf(cells.values()));
                }
            }
        }
        return new RawDocument(rows);
    }

    private static String text(Sheet sheet, int r, int c, DataFormatter formatter) {
        Row row = sheet.getRow(r);
        Cell cell = row == null ? null : row.getCell(c);
        return cell == null ? "" : formatter.formatCellValue(cell).strip();
    }
}
