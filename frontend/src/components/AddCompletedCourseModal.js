import { useEffect, useState } from "react";
import { FiChevronDown, FiX } from "react-icons/fi";
import { CLASSIFICATION_LABELS, TERM_LABELS } from "./CompletedCourses";
import "./AddCompletedCourseModal.css";

const GRADES = ['A+', 'A0', 'A-', 'B+', 'B0', 'B-', 'C+', 'C0', 'C-', 'D+', 'D0', 'D-', 'F', 'P', 'NP'];

// 배분이수 영역 이름 (서버는 번호 1~5만 저장한다. BACKEND_SPEC.md 3.1)
export const DISTRIBUTION_AREAS = {
    1: '생명·우주·인간',
    2: '분석·추론·논리',
    3: '상징·문화·소통',
    4: '사회·공동체·평화',
    5: '지능·정보·미래',
};

// 오늘 날짜 기준 현재 학기 (3~8월 1학기, 9~12월 2학기, 1~2월 전년도 2학기)
function currentTerm() {
    const now = new Date();
    const m = now.getMonth() + 1;
    if (m >= 3 && m <= 8) return { year: now.getFullYear(), term: 'FIRST' };
    if (m >= 9) return { year: now.getFullYear(), term: 'SECOND' };
    return { year: now.getFullYear() - 1, term: 'SECOND' };
}

export function Select({ value, onChange, placeholder, children }) {
    return (
        <div className="acc-select">
            <select
                className={`acc-input ${value === '' ? 'is-empty' : ''}`}
                value={value}
                onChange={(e) => onChange(e.target.value)}
            >
                {placeholder && <option value="" disabled>{placeholder}</option>}
                {children}
            </select>
            <span className="acc-select-icon"><FiChevronDown /></span>
        </div>
    );
}

// 오른쪽에서 열리는 이수한 과목 추가·수정 창
// initialCourse 가 있으면 수정 창: 그 과목 값이 채워진 채로 열린다
// onSubmit(course): POST·PATCH /api/academic/completed-courses 요청 body 모양으로 넘긴다
function AddCompletedCourseModal({ initialCourse, onClose, onSubmit }) {
    const now = currentTerm();
    const isEdit = !!initialCourse;
    const [year, setYear] = useState(String(initialCourse?.year ?? now.year));
    const [term, setTerm] = useState(initialCourse?.term ?? now.term);
    const [courseName, setCourseName] = useState(initialCourse?.courseName ?? '');
    const [classification, setClassification] = useState(initialCourse?.classification ?? '');
    const [distributionArea, setDistributionArea] = useState(String(initialCourse?.distributionArea ?? ''));
    const [credits, setCredits] = useState(String(initialCourse?.credits ?? ''));
    const [grade, setGrade] = useState(initialCourse?.grade ?? '');

    // Esc로 닫기
    useEffect(() => {
        const onKey = (e) => e.key === 'Escape' && onClose();
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [onClose]);

    const yearOptions = Array.from({ length: 8 }, (_, i) => now.year + 1 - i);
    // 수정하는 과목의 학년도가 목록 범위 밖이면 맨 아래에 붙인다
    if (isEdit && !yearOptions.includes(initialCourse.year)) yearOptions.push(initialCourse.year);
    const isDistribution = classification === 'GEN_DISTRIBUTION';
    const creditsNum = Number(credits);
    const validCredits = credits !== '' && Number.isInteger(creditsNum) && creditsNum >= 0 && creditsNum <= 30;
    const canSubmit = courseName.trim() && classification && validCredits && (!isDistribution || distributionArea);

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!canSubmit) return;
        onSubmit({
            year: Number(year),
            term,
            courseName: courseName.trim(),
            classification,
            distributionArea: isDistribution ? Number(distributionArea) : null,
            credits: creditsNum,
            grade: grade || null,
        });
    };

    return (
        <div className="acc-backdrop" onClick={onClose}>
            <form className="acc-panel" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
                <div className="acc-head">
                    <div>
                        <h2>{isEdit ? '이수한 과목 수정' : '이수한 과목 추가'}</h2>
                        <p>{isEdit ? '수정할 내용을 입력해주세요.' : '이수한 과목 정보를 입력해주세요.'}</p>
                    </div>
                    <button type="button" className="acc-close" onClick={onClose} aria-label="닫기"><FiX /></button>
                </div>

                <div className="acc-fields">
                    <label className="acc-label">학년도</label>
                    <Select value={year} onChange={setYear}>
                        {yearOptions.map((y) => <option key={y} value={y}>{y}</option>)}
                    </Select>

                    <label className="acc-label">학기</label>
                    <Select value={term} onChange={setTerm}>
                        {Object.entries(TERM_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                    </Select>

                    <label className="acc-label">과목명</label>
                    <input className="acc-input" value={courseName} onChange={(e) => setCourseName(e.target.value)} placeholder="예) 평면디자인" maxLength={100} autoFocus />

                    <label className="acc-label">이수구분</label>
                    <Select value={classification} onChange={setClassification} placeholder="선택해주세요.">
                        {Object.entries(CLASSIFICATION_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                    </Select>

                    {/* 배분이수는 영역(1~5)이 있어야 저장된다 */}
                    {isDistribution && (
                        <>
                            <label className="acc-label">배분이수 영역</label>
                            <Select value={distributionArea} onChange={setDistributionArea} placeholder="선택해주세요.">
                                {Object.entries(DISTRIBUTION_AREAS).map(([a, name]) => <option key={a} value={a}>{a}영역 · {name}</option>)}
                            </Select>
                        </>
                    )}

                    <label className="acc-label">학점</label>
                    <input className="acc-input" type="number" min="0" max="30" step="1" value={credits} onChange={(e) => setCredits(e.target.value)} placeholder="예) 3" />
                    {credits !== '' && !validCredits && <p className="acc-error">학점은 0~30 사이 정수로 입력해주세요.</p>}

                    <label className="acc-label">성적 (선택)</label>
                    <Select value={grade} onChange={setGrade} placeholder="선택해주세요.">
                        {GRADES.map((g) => <option key={g} value={g}>{g}</option>)}
                    </Select>
                </div>

                <div className="acc-actions">
                    <button type="submit" className="acc-submit" disabled={!canSubmit}>{isEdit ? '수정하기' : '추가하기'}</button>
                    <button type="button" className="acc-cancel" onClick={onClose}>취소</button>
                </div>
            </form>
        </div>
    );
}

export default AddCompletedCourseModal;
