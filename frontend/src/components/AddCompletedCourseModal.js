import { useEffect, useState } from "react";
import { FiChevronDown, FiX } from "react-icons/fi";
import { CLASSIFICATION_LABELS, TERM_LABELS } from "./CompletedCourses";
import "./AddCompletedCourseModal.css";

const GRADES = ['A+', 'A0', 'A-', 'B+', 'B0', 'B-', 'C+', 'C0', 'C-', 'D+', 'D0', 'D-', 'F', 'P', 'NP'];

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

// 오른쪽에서 열리는 이수한 과목 추가 창
// onSubmit(course): POST /api/academic/completed-courses 요청 body 모양으로 넘긴다
function AddCompletedCourseModal({ onClose, onSubmit }) {
    const now = currentTerm();
    const [year, setYear] = useState(String(now.year));
    const [term, setTerm] = useState(now.term);
    const [courseName, setCourseName] = useState('');
    const [courseCode, setCourseCode] = useState('');
    const [classification, setClassification] = useState('');
    const [distributionArea, setDistributionArea] = useState('');
    const [credits, setCredits] = useState('');
    const [grade, setGrade] = useState('');

    // Esc로 닫기
    useEffect(() => {
        const onKey = (e) => e.key === 'Escape' && onClose();
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [onClose]);

    const yearOptions = Array.from({ length: 8 }, (_, i) => now.year + 1 - i);
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
            courseCode: courseCode.trim() || null,
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
                        <h2>이수한 과목 추가</h2>
                        <p>이수한 과목 정보를 입력해주세요.</p>
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

                    <label className="acc-label">과목코드 (선택)</label>
                    <input className="acc-input" value={courseCode} onChange={(e) => setCourseCode(e.target.value)} placeholder="예) D901023" maxLength={20} />

                    <label className="acc-label">이수구분</label>
                    <Select value={classification} onChange={setClassification} placeholder="선택해주세요.">
                        {Object.entries(CLASSIFICATION_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                    </Select>

                    {/* 배분이수는 영역(1~5)이 있어야 저장된다 */}
                    {isDistribution && (
                        <>
                            <label className="acc-label">배분이수 영역</label>
                            <Select value={distributionArea} onChange={setDistributionArea} placeholder="선택해주세요.">
                                {[1, 2, 3, 4, 5].map((a) => <option key={a} value={a}>{a}영역</option>)}
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
                    <button type="submit" className="acc-submit" disabled={!canSubmit}>추가하기</button>
                    <button type="button" className="acc-cancel" onClick={onClose}>취소</button>
                </div>
            </form>
        </div>
    );
}

export default AddCompletedCourseModal;
