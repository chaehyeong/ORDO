// 졸업 로드맵 더미 데이터. 백엔드 연동 시 각 키를 API 응답 data로 바꾸면 된다.
// gpa: GET /api/academic/gpa (FRONTEND_GUIDE.md "졸업 로드맵 화면 (T17)")
//   - terms 중 semesterLabel이 있는 행만 곡선의 점 (계절학기는 null)
//   - currentSemesterLabel = "지금 여기" (수강 중인 학기는 gpa null, earnedCredits 0)
export const mockRoadmap = {
    gpa: {
        gpa: 3.861,
        majorGpa: 3.912,
        gradedCredits: 87,
        earnedCredits: 87,
        currentSemesterLabel: '3-2',
        terms: [
            { year: 2024, term: 'FIRST',  semesterLabel: '1-1', gpa: 3.531, gradedCredits: 18, earnedCredits: 18 },
            { year: 2024, term: 'SECOND', semesterLabel: '1-2', gpa: 3.694, gradedCredits: 15, earnedCredits: 15 },
            { year: 2025, term: 'FIRST',  semesterLabel: '2-1', gpa: 4.021, gradedCredits: 18, earnedCredits: 18 },
            { year: 2025, term: 'SECOND', semesterLabel: '2-2', gpa: 3.903, gradedCredits: 18, earnedCredits: 18 },
            { year: 2026, term: 'FIRST',  semesterLabel: '3-1', gpa: 4.203, gradedCredits: 18, earnedCredits: 18 },
            { year: 2026, term: 'SECOND', semesterLabel: '3-2', gpa: null,  gradedCredits: 0,  earnedCredits: 0 },
        ],
    },
    // completedCourses: GET /api/academic/completed-courses (학년도 → 학기 순)
    //   - grade: A+ A0 A- B+ B0 B- C+ C0 C- D+ D0 D- F P NP, 수강 중(성적 미정)이면 null
    completedCourses: [
        { id: 1, year: 2024, term: 'FIRST', courseName: '드로잉', courseCode: 'FD101', classification: 'MAJOR_BASIC', distributionArea: null, credits: 3, grade: 'A+' },
        { id: 2, year: 2024, term: 'FIRST', courseName: '기초디자인', courseCode: 'FD102', classification: 'MAJOR_BASIC', distributionArea: null, credits: 3, grade: 'A0' },
        { id: 3, year: 2024, term: 'FIRST', courseName: '대학영어', courseCode: null, classification: 'GEN_REQUIRED', distributionArea: null, credits: 3, grade: 'B+' },
        { id: 4, year: 2024, term: 'FIRST', courseName: '성찰과 표현', courseCode: null, classification: 'GEN_REQUIRED', distributionArea: null, credits: 3, grade: 'A0' },
        { id: 5, year: 2024, term: 'FIRST', courseName: '철학의 이해', courseCode: null, classification: 'GEN_DISTRIBUTION', distributionArea: 1, credits: 3, grade: 'B0' },
        { id: 6, year: 2024, term: 'FIRST', courseName: '디자인 개론', courseCode: 'VID1001', classification: 'MAJOR_REQUIRED', distributionArea: null, credits: 3, grade: 'A-' },
        { id: 7, year: 2024, term: 'SECOND', courseName: '색채학', courseCode: 'FD103', classification: 'MAJOR_BASIC', distributionArea: null, credits: 3, grade: 'A0' },
        { id: 8, year: 2024, term: 'SECOND', courseName: '타이포그래피 기초', courseCode: 'VID1002', classification: 'MAJOR_REQUIRED', distributionArea: null, credits: 3, grade: 'A+' },
        { id: 9, year: 2024, term: 'SECOND', courseName: '글쓰기', courseCode: null, classification: 'GEN_REQUIRED', distributionArea: null, credits: 3, grade: 'B+' },
        { id: 10, year: 2024, term: 'SECOND', courseName: '현대사회와 윤리', courseCode: null, classification: 'GEN_DISTRIBUTION', distributionArea: 1, credits: 3, grade: 'A-' },
        { id: 11, year: 2024, term: 'SECOND', courseName: '사진의 이해', courseCode: 'VID1003', classification: 'MAJOR_ELECTIVE', distributionArea: null, credits: 3, grade: 'B0' },
        { id: 12, year: 2025, term: 'FIRST', courseName: '편집디자인', courseCode: 'VID2001', classification: 'MAJOR_REQUIRED', distributionArea: null, credits: 3, grade: 'A+' },
        { id: 13, year: 2025, term: 'FIRST', courseName: '브랜드 디자인', courseCode: 'VID2002', classification: 'MAJOR_ELECTIVE', distributionArea: null, credits: 3, grade: 'A+' },
        { id: 14, year: 2025, term: 'FIRST', courseName: '일러스트레이션', courseCode: 'VID2003', classification: 'MAJOR_ELECTIVE', distributionArea: null, credits: 3, grade: 'A0' },
        { id: 15, year: 2025, term: 'FIRST', courseName: '세계와 시민', courseCode: null, classification: 'GEN_REQUIRED', distributionArea: null, credits: 3, grade: 'A0' },
        { id: 16, year: 2025, term: 'FIRST', courseName: '경제학의 이해', courseCode: null, classification: 'GEN_DISTRIBUTION', distributionArea: 1, credits: 3, grade: 'B+' },
        { id: 17, year: 2025, term: 'FIRST', courseName: '영화와 문화', courseCode: null, classification: 'GEN_FREE', distributionArea: null, credits: 3, grade: 'P' },
        { id: 18, year: 2025, term: 'SECOND', courseName: '모션그래픽', courseCode: 'VID2004', classification: 'MAJOR_ELECTIVE', distributionArea: null, credits: 3, grade: 'A0' },
        { id: 19, year: 2025, term: 'SECOND', courseName: '인터랙션 디자인', courseCode: 'VID2005', classification: 'MAJOR_REQUIRED', distributionArea: null, credits: 3, grade: 'A+' },
        { id: 20, year: 2025, term: 'SECOND', courseName: '디자인 방법론', courseCode: 'VID2006', classification: 'MAJOR_REQUIRED', distributionArea: null, credits: 3, grade: 'B+' },
        { id: 21, year: 2025, term: 'SECOND', courseName: '패키지 디자인', courseCode: 'VID2007', classification: 'MAJOR_ELECTIVE', distributionArea: null, credits: 3, grade: 'A-' },
        { id: 22, year: 2025, term: 'SECOND', courseName: '통계의 이해', courseCode: null, classification: 'GEN_DISTRIBUTION', distributionArea: 1, credits: 3, grade: 'B0' },
        { id: 23, year: 2025, term: 'SECOND', courseName: '봉사활동', courseCode: null, classification: 'GENERAL_ELECTIVE', distributionArea: null, credits: 3, grade: 'P' },
        { id: 24, year: 2026, term: 'FIRST', courseName: '현대미술사', courseCode: 'FD104', classification: 'MAJOR_BASIC', distributionArea: null, credits: 3, grade: 'A+' },
        { id: 25, year: 2026, term: 'FIRST', courseName: '컴퓨터 그래픽 기초', courseCode: null, classification: 'GEN_DISTRIBUTION', distributionArea: 1, credits: 3, grade: 'A0' },
        { id: 26, year: 2026, term: 'FIRST', courseName: '인간의 가치 탐색', courseCode: null, classification: 'GEN_REQUIRED', distributionArea: null, credits: 3, grade: 'B+' },
        { id: 27, year: 2026, term: 'FIRST', courseName: '정보디자인', courseCode: 'VID3001', classification: 'MAJOR_REQUIRED', distributionArea: null, credits: 3, grade: 'A+' },
        { id: 28, year: 2026, term: 'FIRST', courseName: 'UX 리서치', courseCode: 'VID3002', classification: 'MAJOR_ELECTIVE', distributionArea: null, credits: 3, grade: 'A0' },
        { id: 29, year: 2026, term: 'FIRST', courseName: '디자인 경영', courseCode: 'VID3003', classification: 'MAJOR_ELECTIVE', distributionArea: null, credits: 3, grade: 'A+' },
        { id: 30, year: 2026, term: 'SECOND', courseName: '빅뱅에서 문명까지', courseCode: null, classification: 'GEN_REQUIRED', distributionArea: null, credits: 3, grade: null },
        { id: 31, year: 2026, term: 'SECOND', courseName: '한자의 이해', courseCode: null, classification: 'GEN_FREE', distributionArea: null, credits: 3, grade: null },
        { id: 32, year: 2026, term: 'SECOND', courseName: '서비스 디자인', courseCode: 'VID3004', classification: 'MAJOR_ELECTIVE', distributionArea: null, credits: 3, grade: null },
        { id: 33, year: 2026, term: 'SECOND', courseName: '졸업연구 준비', courseCode: 'VID3005', classification: 'MAJOR_REQUIRED', distributionArea: null, credits: 3, grade: null },
    ],
    // remainingCourses: GET /api/academic/remaining-courses
    //   - remainingCredits = 졸업까지 남은 학점 (courses 학점의 합이 아님)
    //   - 순서: 필수교양 → 전공기초 → 전공필수 → 전공선택
    remainingCourses: {
        remainingCredits: 31,
        majorCoursesAvailable: true,
        courses: [
            { courseName: '주제연구', courseCode: null, classification: 'GEN_REQUIRED', credits: 3, targetGrade: '2', openSemester: null, unitName: null },
            { courseName: '영어회화', courseCode: null, classification: 'GEN_REQUIRED', credits: 3, targetGrade: '2', openSemester: null, unitName: null },
            { courseName: '글로벌 리더십', courseCode: null, classification: 'GEN_REQUIRED', credits: 3, targetGrade: '전학년', openSemester: null, unitName: null },
            { courseName: '디지털디자인', courseCode: 'FD105', classification: 'MAJOR_BASIC', credits: 3, targetGrade: '1', openSemester: '1,2', unitName: '시각디자인학과' },
            { courseName: '평면디자인', courseCode: 'FD106', classification: 'MAJOR_BASIC', credits: 3, targetGrade: '1', openSemester: '1', unitName: '시각디자인학과' },
            { courseName: '타이포그래피1', courseCode: 'VID1004', classification: 'MAJOR_REQUIRED', credits: 3, targetGrade: '2', openSemester: '1', unitName: '시각디자인학과' },
            { courseName: '졸업연구', courseCode: 'VID4001', classification: 'MAJOR_REQUIRED', credits: 3, targetGrade: '4', openSemester: '1,2', unitName: '시각디자인학과' },
            { courseName: '졸업논문', courseCode: 'VID4002', classification: 'MAJOR_REQUIRED', credits: 0, targetGrade: '4', openSemester: '2', unitName: '시각디자인학과' },
            { courseName: '시각적사고와 일러스트레이션', courseCode: 'VID2008', classification: 'MAJOR_ELECTIVE', credits: 3, targetGrade: '2', openSemester: '2', unitName: '시각디자인학과' },
            { courseName: 'UX/UI 디자인', courseCode: 'VID3006', classification: 'MAJOR_ELECTIVE', credits: 3, targetGrade: '3', openSemester: '1', unitName: '시각디자인학과' },
            { courseName: '영상과 미디어', courseCode: 'VID3007', classification: 'MAJOR_ELECTIVE', credits: 3, targetGrade: '3', openSemester: '2', unitName: '시각디자인학과' },
            { courseName: '사진1', courseCode: 'VID2009', classification: 'MAJOR_ELECTIVE', credits: 3, targetGrade: '2', openSemester: '1', unitName: '시각디자인학과' },
            { courseName: '웹디자인', courseCode: 'VID3008', classification: 'MAJOR_ELECTIVE', credits: 3, targetGrade: '3', openSemester: '1,2', unitName: '시각디자인학과' },
            { courseName: '3D 모델링', courseCode: 'VID3009', classification: 'MAJOR_ELECTIVE', credits: 3, targetGrade: '3-4', openSemester: '2', unitName: '시각디자인학과' },
        ],
    },
    // plans: GET /api/academic/plans (과목이 있는 학기만 온다. 빈 학기 칸은 프론트가 만든다)
    plans: {
        totalCredits: 12,
        terms: [
            { year: 2027, term: 'FIRST', label: '2027년 1학기', credits: 12,
              courses: [
                  { id: 1, courseName: '주제연구', courseCode: null, classification: 'GEN_REQUIRED', credits: 3 },
                  { id: 2, courseName: '타이포그래피1', courseCode: 'VID1004', classification: 'MAJOR_REQUIRED', credits: 3 },
                  { id: 3, courseName: 'UX/UI 디자인', courseCode: 'VID3006', classification: 'MAJOR_ELECTIVE', credits: 3 },
                  { id: 4, courseName: '영상과 미디어', courseCode: 'VID3007', classification: 'MAJOR_ELECTIVE', credits: 3 },
              ] },
        ],
    },
};
