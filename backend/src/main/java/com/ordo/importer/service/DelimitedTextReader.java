package com.ordo.importer.service;

import java.util.ArrayList;
import java.util.List;

/** csv(쉼표)·txt(탭) → RawDocument. 따옴표 안의 구분자·줄바꿈("재수강\n여부")과 "" 이스케이프를 처리한다 */
final class DelimitedTextReader {

    private DelimitedTextReader() {
    }

    static RawDocument read(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.toString().isBlank()) {
                quoted = true;
            } else if (c == delimiter) {
                row.add(cell.toString().strip());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString().strip());
                cell.setLength(0);
                rows.add(List.copyOf(row));
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString().strip());
            rows.add(List.copyOf(row));
        }
        return new RawDocument(rows);
    }
}
