"""
ORDO 마이크로디그리 시드 생성기 (T20)
------------------------------------------------------------
입력: 오르도_T19복수전공_T20마이크로디그리_최종.xlsx 의 MD목록·MD과목 시트 (예소 폴더)
출력: src/main/resources/db/migration/V18__seed_micro_degrees.sql

실행:
  pip install openpyxl
  python tools/seed/generate_micro_degree_seed.py --xlsx "<엑셀 경로>" --out src/main/resources/db/migration/V18__seed_micro_degrees.sql

이미 적용된 V18 은 덮어쓰지 않는다. 고칠 데이터는 새 마이그레이션으로 낸다(A V10~V49).
"""
import argparse
from collections import Counter

import openpyxl

from generate_seed import q

# 과목·요건이 없어 판정할 수 없는 행
EXCLUDED = {"타대학 융합 마이크로디그리"}

# MD목록 P열이 '확인 필요'인 3개 중 2026 책자에 없어 2026 이후 신설로 판단한 것. 운영지침 제5조② 적용 (추정, 명세 9장 30)
MIN_DEPARTMENTS_ASSUMED = {"AI로봇"}

REQUIREMENT_TEXT_OVERRIDE = {"의료인공지능-Medical AI": "A,B,C,D 영역별 1과목 이수"}


def rows(workbook, sheet):
    ws = workbook[sheet]
    it = ws.iter_rows(values_only=True)
    headers = [str(h).replace("\n", " ") if h is not None else "" for h in next(it)]
    return [dict(zip(headers, r)) for r in it if r[1]]


def code(value):
    return str(value).strip() if value else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--xlsx", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    wb = openpyxl.load_workbook(a.xlsx, data_only=True)
    degrees = [d for d in rows(wb, "MD목록") if d["MD명"] not in EXCLUDED]
    courses = rows(wb, "MD과목")
    ids = {d["MD명"]: i for i, d in enumerate(degrees, 1)}
    assert not {c["MD명"] for c in courses} - set(ids), "MD목록에 없는 과목"
    department_rule = next(h for h in degrees[0] if h.startswith("참여학과 2개 이상"))

    degree_values = []
    for d in degrees:
        assert d["필요 과목수"] and d["필요 학점"], d["MD명"]
        min_departments = 2 if str(d[department_rule]).startswith("적용") or d["MD명"] in MIN_DEPARTMENTS_ASSUMED else 0
        selective = str(d["신청 방식"]).startswith("선발")
        scope = "공통" if d["구분"] == "공통" else "국제"
        text = REQUIREMENT_TEXT_OVERRIDE.get(d["MD명"], d["이수요건(사이트 원문)"])
        degree_values.append(f"({ids[d['MD명']]}, {q(d['MD명'])}, {q(scope)}, {q(d['운영(주관)대학'])}, {q(d['주관학과(**)'])}, "
                             f"{q(d['필요 과목수'])}, {q(d['필요 학점'])}, {q(min_departments)}, {q(selective)}, {q(text)}, {q(d['안내 URL'])})")

    course_values = []
    for c in courses:
        # 학점출처가 '추정'인 행은 확인되지 않은 숫자라 넣지 않는다. 판정은 내 이수 기록의 학점을 쓴다
        credits = None if "추정" in str(c["학점출처"]) else c["학점"]
        course_values.append(f"({ids[c['MD명']]}, {q(code(c['학수번호']))}, {q(code(c['학수번호 별칭 (구 학수번호·사이트 오기)']))}, "
                             f"{q(c['과목명'])}, {q(c['개설학과'])}, {q(credits)}, {q(c['필수/선택'] == '필수')}, "
                             f"{q(c['선택그룹'])}, {q(c['그룹 최소'])}, {q(c['그룹 최대'])})")

    with open(a.out, "w", encoding="utf-8") as f:
        f.write("-- ============================================================\n")
        f.write("-- V18__seed_micro_degrees.sql  (tools/seed/generate_micro_degree_seed.py 로 자동 생성 — 직접 수정 금지)\n")
        f.write("-- 출처: SDL 마이크로디그리 운영현황, 2026 교육과정(국제캠). 계획: docs/plans/T19-T20-double-major-micro-degree.md\n")
        f.write("-- ============================================================\n\n")
        f.write("INSERT INTO micro_degrees (id, name, scope, college_name, host_department, required_course_count, "
                "required_credits, min_departments, selective, requirement_text, guide_url) VALUES\n")
        f.write(",\n".join(degree_values) + ";\n\n")
        f.write("INSERT INTO micro_degree_courses (micro_degree_id, course_code, alias_code, course_name, department_name, "
                "credits, mandatory, group_key, group_min, group_max) VALUES\n")
        f.write(",\n".join(course_values) + ";\n")
    print(f"MD {len(degree_values)}개, 과목 {len(course_values)}행, "
          f"학점 NULL {sum('추정' in str(c['학점출처']) for c in courses)}행, "
          f"구분 {Counter(d['구분'] for d in degrees)}")


if __name__ == "__main__":
    main()
