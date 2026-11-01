"""2026 국제캠퍼스 교육과정 27~35쪽에서 배분이수 편성 과목을 추출한다.

사용법: python tools/generate_distribution_courses.py "교육과정 PDF 경로"
개발용 의존성: pypdf. 원본에 없는 학수번호와 영역 번호는 추정하지 않는다.
"""

import argparse
import hashlib
import json
import re
from pathlib import Path

from pypdf import PdfReader


def extract_catalog(pdf_path):
    reader = PdfReader(pdf_path)
    courses = []
    row_pattern = re.compile(
        r"^(\d+)\s+배분이수\s+(.*?)(?=^\d+\s+(?:배분이수|자유이수|필수교과)|\Z)",
        re.MULTILINE | re.DOTALL,
    )
    for page_number in range(27, 36):
        text = reader.pages[page_number - 1].extract_text() or ""
        for row in row_pattern.finditer(text):
            raw = row.group(2)
            fields = re.search(
                r"(?:(?P<code>[A-Z]{2,}\d+)\s+)?(?P<credits>\d+)\s+(?P<hours>\d+)(?=\s)",
                raw,
            )
            if fields is None:
                raise ValueError(f"{page_number}쪽 {row.group(1)}번 과목을 읽을 수 없습니다.")
            name = re.sub(r"\s+", " ", raw[: fields.start()]).strip()
            if not name or len(name) > 100 or int(fields["credits"]) != 3:
                raise ValueError(f"원문 확인이 필요한 과목: {row.group(1)} {name}")
            courses.append(
                {
                    "id": int(row.group(1)),
                    "courseName": name,
                    "courseCode": fields["code"],
                    "classification": "GEN_DISTRIBUTION",
                    "credits": int(fields["credits"]),
                    "sourcePage": page_number,
                }
            )

    if [course["id"] for course in courses] != list(range(7, 344)):
        raise ValueError("배분이수 편성표의 순번 7~343이 모두 추출되어야 합니다.")
    codes = [course["courseCode"] for course in courses if course["courseCode"]]
    if len(codes) != len(set(codes)):
        raise ValueError("학수번호가 중복됩니다. 원문을 확인하세요.")

    return {
        "source": {
            "title": "2026학년도 교육과정 - 국제캠퍼스 교양교육과정 편성표",
            "fileName": pdf_path.name,
            "sha256": hashlib.sha256(pdf_path.read_bytes()).hexdigest(),
            "pages": [27, 35],
            "year": 2026,
            "campus": "국제캠퍼스",
            "note": "편성 과목 목록이며 학기별 실제 개설 여부를 보장하지 않음. 영역 번호는 원문에 없어 추정하지 않음.",
        },
        "courses": courses,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pdf", type=Path)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "src/data/distributionCourses.json",
    )
    args = parser.parse_args()
    catalog = extract_catalog(args.pdf)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"배분이수 {len(catalog['courses'])}과목 추출: {args.output}")


if __name__ == "__main__":
    main()
