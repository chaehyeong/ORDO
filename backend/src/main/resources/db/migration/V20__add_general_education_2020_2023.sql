-- 2019~2023학번 교양 기준 (교육과정 p45 경과조치): 필수 17, 배분 12(영역 제한 없음), 자유 3, 합계 32. 필수 과목은 2026 과 같다
-- 2024·2025학번은 p24 기준이 2026 과 같아 행을 넣지 않고 대체 기준(approximate)을 그대로 쓴다
INSERT INTO general_education_requirements (admission_year, required_credits, distribution_credits, distribution_min_areas, free_credits, total_credits) VALUES
(2020, 17, 12, 0, 3, 32),
(2021, 17, 12, 0, 3, 32),
(2022, 17, 12, 0, 3, 32),
(2023, 17, 12, 0, 3, 32);

INSERT INTO general_education_required_courses (admission_year, group_name, course_name, credits, recommended_grade, note)
SELECT y.admission_year, c.group_name, c.course_name, c.credits,
       CASE WHEN c.course_name = '빅뱅에서문명까지' THEN '1-2' ELSE c.recommended_grade END,
       CASE WHEN c.course_name = '전공탐색및기업가정신세미나' THEN '자유이수교과 필수' ELSE c.note END
FROM general_education_required_courses c
CROSS JOIN (SELECT 2020 AS admission_year UNION ALL SELECT 2021 UNION ALL SELECT 2022 UNION ALL SELECT 2023) y
WHERE c.admission_year = 2026
ORDER BY y.admission_year, c.id;
