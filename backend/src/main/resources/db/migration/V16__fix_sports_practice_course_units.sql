-- 전문실기 소속 교정 (백엔드 A, 명세 9장 24). 근거: 2026 교육과정(전체).pdf p1205~p1207 '체육대학(공통)' 편성표, 5개 학과 시행세칙.
-- 엑셀에서 앞 학과인 포스트모던음악학과 행으로 잘못 들어갔다. 전공기초 CPE103 처럼 체육대학 학과마다 1행씩 둔다.
-- PDF 는 학수번호마다 종목 17행이지만 종목은 수강할 때 고르므로 이름에서 뺀다. LEFT(name, 5) = '전문실기N'
INSERT INTO courses (college_name, unit_name, course_code, name, classification,
                     credits, variable_credits, target_grade, open_semester, track)
SELECT '체육대학', u.unit_name, c.course_code, LEFT(c.name, 5), c.classification,
       c.credits, c.variable_credits, c.target_grade, c.open_semester, c.track
FROM courses c
CROSS JOIN (SELECT '체육학과' AS unit_name
            UNION ALL SELECT '스포츠의학과'
            UNION ALL SELECT '스포츠지도학과'
            UNION ALL SELECT '골프산업학과'
            UNION ALL SELECT '태권도학과') u
WHERE c.unit_name = '포스트모던음악학과' AND c.course_code LIKE 'CPE%';

DELETE FROM courses WHERE unit_name = '포스트모던음악학과' AND course_code LIKE 'CPE%';
