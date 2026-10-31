"""
ORDO 부전공 지정 과목 시드 생성기
------------------------------------------------------------
입력: 2026 교육과정(전체).pdf (예소 폴더, 커밋하지 않는다), V2·V15 시드
출력: src/main/resources/db/migration/V23__seed_minor_designated_courses.sql

실행 (backend 폴더에서):
  pip install pymupdf
  python tools/seed/generate_minor_course_seed.py --pdf "<PDF 경로>" --out src/main/resources/db/migration/V23__seed_minor_designated_courses.sql

분류와 근거는 docs/plans/T21-minor-rules-survey.md. 이미 적용된 V23 은 덮어쓰지 않는다. 고칠 데이터는 새 마이그레이션으로 낸다(A V10~V49).
"""
import argparse
import re
from pathlib import Path

import pymupdf

MIGRATIONS = Path(__file__).resolve().parents[2] / "src/main/resources/db/migration"
CODE = re.compile(r"^[A-Z]{2,6}\d{3,5}[A-Z]?$")
MARKS = {"○", "◯", "〇"}

# 편성표에 인정 과목을 표시한 전공: 표시 과목만 인정 (major_id, course_unit, 쪽)
MARKED_ELECTIVE = {
    11: ("전자공학부 전자공학과", [315, 316]),
    62: ("전자공학부 반도체공학과", [344, 345]),
    12: ("생체의공학과", [377]),
    56: ("스마트팜과학과", [611, 612]),
    20: ("식품생명공학과", [657, 658]),
    70: ("첨단바이오소재 융합전공", [1487]),
    71: ("첨단종자푸드테크 융합전공", [1504, 1505]),
}
# 편성표에 필수 과목만 표시한 전공: 표시 학점이 전공필수 요구 학점과 같다. 러시아어학과는 표시 6학점 중 요구 3학점(p882, p885)
MARKED_REQUIRED = {
    57: ("식물·환경신소재공학과", [634, 635]),
    21: ("원예생명공학과", [678, 679]),
    26: ("프랑스어학과", [845]),
    27: ("스페인어학과", [864, 865]),
    28: ("러시아어학과", [885]),
    29: ("중국어학과", [903]),
    30: ("일본어학과", [924, 925]),
    32: ("글로벌커뮤니케이션학부 영미어문전공", [985]),
    33: ("글로벌커뮤니케이션학부 영미문화전공", [1003]),
    39: ("도예학과", [1128]),
}
# 전공선택은 3~4학년 대상(학수번호 300~400번대)만 (p981, p999)
UPPER_ELECTIVE_ONLY = {32, 33}
# 조항·별도 표에 과목명이 적힌 전공
NAMED_REQUIRED = {
    54: ("컴퓨터공학부 컴퓨터공학과", ["객체지향프로그래밍", "웹/파이선프로그래밍", "자료구조", "운영체제", "알고리즘"]),  # p398 표3
    14: ("소프트웨어융합학과", ["웹/파이선프로그래밍", "오픈소스SW개발방법및도구", "객체지향프로그래밍"]),  # p461 표4
    15: ("응용수학과", ["해석학1", "수치해석및연습", "현대대수학1", "미분기하학"]),  # p517
    18: ("우주과학과", ["천문학개론및실습1", "천문학개론및실습2", "우주환경Ⅰ", "우주환경Ⅱ", "천체물리학Ⅰ", "천체물리학Ⅱ",
                   "우주전자응용및실험", "우주관측및실습", "우주수치계산"]),  # p585 표3, 9과목 중 4과목
}
NAMED_ELECTIVE = {
    14: ("소프트웨어융합학과", ["기계학습", "마이크로서비스프로그래밍", "데이터사이언스및시각화", "게임프로그래밍입문", "게임엔진기초",
                       "게임그래픽프로그래밍", "게임공학", "가상현실", "3D데이터처리", "로봇프로그래밍", "수치해석프로그래밍",
                       "인공지능과게임프로그래밍", "자연언어처리", "풀스택서비스네트워킹", "강화학습", "영상분석과패턴인식",
                       "풀스택서비스프로그래밍", "디자인프로그래밍", "응용데이터분석", "데이터분석입문"]),  # p461 표4
}
EXPECTED_MARKS = {11: 40, 62: 43, 12: 32, 56: 29, 20: 35, 70: 29, 71: 36, 57: 5, 21: 4, 26: 3, 27: 1, 28: 2, 29: 4, 30: 4, 32: 5, 33: 5, 39: 3}


def load_courses():
    """(unit, code) -> (name, classification). V15 의 과목명 교정을 적용한다"""
    seed = (MIGRATIONS / "V2__seed_catalog.sql").read_text(encoding="utf-8")
    fixes = {(code, old): new for new, code, old in re.findall(
        r"UPDATE courses SET name = '([^']*)' WHERE course_code = '([^']*)' AND name = '([^']*)';",
        (MIGRATIONS / "V15__fix_course_names.sql").read_text(encoding="utf-8"))}
    courses = {}
    for unit, code, name, classification in re.findall(
            r"^\('[^']*', '([^']*)', '([^']*)', '((?:[^']|'')*)', '([A-Z_]+)', \d+,", seed, re.M):
        courses[(unit, code)] = (fixes.get((code, name), name), classification)
    return courses


def column_center(words, top):
    """표 머리의 부전공 칸 가로 위치. '부전공', '1학기2학기부전공', '부'+'전공' 세 모양이 있다"""
    for w in words:
        if w[1] < top and "부전공" in w[4]:
            width = (w[2] - w[0]) / len(w[4])
            return w[0] + width * (w[4].index("부전공") + 1.5)
    for w in words:
        if w[1] < top and w[4].endswith("부"):
            for below in words:
                if below[4] == "전공" and below[0] < w[2] + 6 and below[2] > w[2] - 14 and 0 < below[1] - w[1] < 22:
                    return (below[0] + below[2]) / 2
    raise ValueError("부전공 칸을 찾지 못함")


def marked_codes(document, pages):
    codes = []
    for page in pages:
        words = document[page - 1].get_text("words")
        code_words = [w for w in words if CODE.match(w[4])]
        center = column_center(words, min(w[1] for w in code_words))
        for c in code_words:
            y = (c[1] + c[3]) / 2
            if any(w[4] in MARKS and abs((w[1] + w[3]) / 2 - y) < 5 and abs((w[0] + w[2]) / 2 - center) < 8 for w in words):
                codes.append(c[4])
    return list(dict.fromkeys(codes))


def named_codes(courses, unit, names):
    result = []
    for name in names:
        found = [code for (u, code), (n, _) in courses.items() if u == unit and n == name]
        if len(found) != 1:
            raise ValueError(f"{unit} {name}: 학수번호 {found}")
        result.append(found[0])
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pdf", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    document = pymupdf.open(args.pdf)
    courses = load_courses()

    rows = []  # (major_id, course_code, role)
    for major_id, (unit, pages) in {**MARKED_ELECTIVE, **MARKED_REQUIRED}.items():
        codes = marked_codes(document, pages)
        assert len(codes) == EXPECTED_MARKS[major_id], (major_id, len(codes))
        role = "ELECTIVE" if major_id in MARKED_ELECTIVE else "REQUIRED"
        for code in codes:
            assert (unit, code) in courses, (unit, code)
            rows.append((major_id, code, role))
        if major_id in UPPER_ELECTIVE_ONLY:
            rows += [(major_id, code, "ELECTIVE") for (u, code), (_, classification) in courses.items()
                     if u == unit and classification == "MAJOR_ELECTIVE" and re.search(r"[34]\d\d$", code)]
    for major_id, (unit, names) in NAMED_REQUIRED.items():
        rows += [(major_id, code, "REQUIRED") for code in named_codes(courses, unit, names)]
    for major_id, (unit, names) in NAMED_ELECTIVE.items():
        rows += [(major_id, code, "ELECTIVE") for code in named_codes(courses, unit, names)]
    rows.sort()
    assert len(rows) == len({(m, c) for m, c, _ in rows}), "한 전공에 같은 과목 두 번"

    lines = [
        "-- 부전공 지정 과목. tools/seed/generate_minor_course_seed.py 로 생성했다",
        "-- 근거는 docs/plans/T21-minor-rules-survey.md, 판정은 명세 5장 14번",
        "INSERT INTO minor_designated_courses (major_id, course_code, role) VALUES",
        ",\n".join(f"({m}, '{c}', '{r}')" for m, c, r in rows) + ";",
        "",
    ]
    Path(args.out).write_text("\n".join(lines), encoding="utf-8", newline="\r\n")
    print(f"{len(rows)} rows, {len({m for m, _, _ in rows})} majors")


if __name__ == "__main__":
    main()
