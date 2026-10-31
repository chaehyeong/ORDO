import { useMemo, useState } from "react";
import { FiChevronDown } from "react-icons/fi";
import "./CompletedCourses.css";

// 이수구분 (백엔드 classification 값 → 화면 글자), 필터 목록도 이 순서
export const CLASSIFICATION_LABELS = {
    MAJOR_BASIC: '전공기초',
    MAJOR_REQUIRED: '전공필수',
    MAJOR_ELECTIVE: '전공선택',
    GEN_REQUIRED: '필수교양',
    GEN_DISTRIBUTION: '배분이수',
    GEN_FREE: '자유이수',
    GENERAL_ELECTIVE: '일반선택',
    TEACHING: '교직',
    TEACHING_MAJOR: '교직전선',
};

export const TERM_LABELS = {
    FIRST: '1학기',
    SUMMER: '여름학기',
    SECOND: '2학기',
    WINTER: '겨울학기',
};

// F·NP는 학점에 들어가지 않는다 (성적 미정 = 수강 중은 들어감)
const NOT_EARNED = ['F', 'NP'];

export function FilterSelect({ value, onChange, allLabel, options }) {
    return (
        <div className="completed-select">
            <select value={value} onChange={(e) => onChange(e.target.value)}>
                <option value="">{allLabel}</option>
                {options.map((o) => (
                    <option key={o.value} value={o.value}>{o.label}</option>
                ))}
            </select>
            <span className="completed-select-icon"><FiChevronDown /></span>
        </div>
    );
}

// 이수한 과목 박스
// courses = GET /api/academic/completed-courses 응답 data, onAdd = "이수한 과목 추가하기" 버튼을 눌렀을 때
function CompletedCourses({ courses, onAdd }) {
    const [year, setYear] = useState('');
    const [term, setTerm] = useState('');
    const [classification, setClassification] = useState('');

    const totalCredits = courses
        .filter((c) => !NOT_EARNED.includes(c.grade))
        .reduce((sum, c) => sum + c.credits, 0);

    // 필터 목록은 내 이수내역에 있는 값만
    const yearOptions = useMemo(
        () => [...new Set(courses.map((c) => c.year))].sort().map((y) => ({ value: String(y), label: `${y}년` })),
        [courses]
    );
    const termOptions = useMemo(
        () => Object.keys(TERM_LABELS)
            .filter((t) => courses.some((c) => c.term === t))
            .map((t) => ({ value: t, label: TERM_LABELS[t] })),
        [courses]
    );
    const classificationOptions = useMemo(
        () => Object.keys(CLASSIFICATION_LABELS)
            .filter((k) => courses.some((c) => c.classification === k))
            .map((k) => ({ value: k, label: CLASSIFICATION_LABELS[k] })),
        [courses]
    );

    const filtered = courses.filter((c) =>
        (!year || String(c.year) === year) &&
        (!term || c.term === term) &&
        (!classification || c.classification === classification)
    );

    return (
        <div className="completed-courses">
            <div className="completed-header">
                <div className="completed-title-wrap">
                    <h4 className="completed-title">이수한 과목</h4>
                    <span className="completed-total">총 {totalCredits}학점</span>
                </div>
                <button type="button" className="completed-add-btn" onClick={onAdd}>
                    +이수한 과목 추가하기
                </button>
            </div>

            <div className="completed-filters">
                <FilterSelect value={year} onChange={setYear} allLabel="전체 연도" options={yearOptions} />
                <FilterSelect value={term} onChange={setTerm} allLabel="전체 학기" options={termOptions} />
                <FilterSelect value={classification} onChange={setClassification} allLabel="전체 이수구분" options={classificationOptions} />
            </div>

            <div className="completed-table">
                <div className="completed-row completed-head">
                    <span>연도</span>
                    <span>학기</span>
                    <span>과목명</span>
                    <span>이수구분</span>
                    <span>학점</span>
                    <span>성적</span>
                </div>
                <div className="completed-body">
                    {filtered.length === 0 ? (
                        <p className="completed-empty">
                            {courses.length === 0 ? '아직 추가한 이수 과목이 없습니다.' : '조건에 맞는 과목이 없습니다.'}
                        </p>
                    ) : (
                        filtered.map((c) => (
                            <div key={c.id} className="completed-row">
                                <span>{c.year}</span>
                                <span>{TERM_LABELS[c.term]}</span>
                                <span className="completed-name">{c.courseName}</span>
                                <span>{CLASSIFICATION_LABELS[c.classification] ?? c.classification}</span>
                                <span>{c.credits}</span>
                                <span>{c.grade ?? '-'}</span>
                            </div>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
}

export default CompletedCourses;
