-- 포스트모던음악학과 2026학번 부전공 합계 21 → 33. 근거: 교육과정 p1177 제6조(전공필수 12학점 포함 33학점), 학사지원팀 답변(2026-07-23)
-- 학사 홈페이지 게시 자료의 합계 칸 오류가 엑셀을 거쳐 V2 로 들어왔다. 전공필수 12 + 전공선택 21 = 33 이다
UPDATE graduation_requirements SET minor_total_credits = 33 WHERE major_id = 41 AND admission_year = 2026;
