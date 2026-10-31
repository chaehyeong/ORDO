import { useEffect, useState } from "react";
import { FiX } from "react-icons/fi";
import { CLASSIFICATION_LABELS } from "./CompletedCourses";
import { Select } from "./AddCompletedCourseModal";
import { termLabel } from "./SemesterPlanner";
import "./AddCompletedCourseModal.css";

// 학기별 배치하기 "수업 추가" 창 (남은 과목 목록에 없는 수업을 직접 넣을 때)
// 이수한 과목 추가 창과 같은 오른쪽 패널 모양. 백엔드가 받는 값: 과목명, 학수번호, 이수구분, 학점
// onSubmit(course)가 오류 문구를 돌려주면 창에 표시하고, 없으면 닫힌다
function AddPlanCourseModal({ terms, onClose, onSubmit }) {
    const [termKey, setTermKey] = useState(terms.length ? `${terms[0].year}-${terms[0].term}` : '');
    const [courseName, setCourseName] = useState('');
    const [courseCode, setCourseCode] = useState('');
    const [classification, setClassification] = useState('');
    const [credits, setCredits] = useState('');
    const [error, setError] = useState('');

    useEffect(() => {
        const onKey = (e) => e.key === 'Escape' && onClose();
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [onClose]);

    const creditsNum = Number(credits);
    const validCredits = credits !== '' && Number.isInteger(creditsNum) && creditsNum >= 0 && creditsNum <= 30;
    const canSubmit = termKey && courseName.trim() && validCredits;

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!canSubmit) return;
        const term = terms.find((t) => `${t.year}-${t.term}` === termKey);
        const message = onSubmit({
            year: term.year,
            term: term.term,
            courseName: courseName.trim(),
            courseCode: courseCode.trim() || null,
            classification: classification || null,
            credits: creditsNum,
        });
        if (message) setError(message);
    };

    return (
        <div className="acc-backdrop" onClick={onClose}>
            <form className="acc-panel" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
                <div className="acc-head">
                    <div>
                        <h2>수업 추가</h2>
                        <p>학기에 배치할 수업 정보를 입력해주세요.</p>
                    </div>
                    <button type="button" className="acc-close" onClick={onClose} aria-label="닫기"><FiX /></button>
                </div>

                <div className="acc-fields">
                    <label className="acc-label">학기</label>
                    <Select value={termKey} onChange={setTermKey}>
                        {terms.map((t) => (
                            <option key={`${t.year}-${t.term}`} value={`${t.year}-${t.term}`}>{termLabel(t)}</option>
                        ))}
                    </Select>

                    <label className="acc-label">과목명</label>
                    <input className="acc-input" value={courseName} onChange={(e) => { setCourseName(e.target.value); setError(''); }} placeholder="예) 평면디자인" maxLength={100} autoFocus />

                    <label className="acc-label">과목코드 (선택)</label>
                    <input className="acc-input" value={courseCode} onChange={(e) => { setCourseCode(e.target.value); setError(''); }} placeholder="예) D901023" maxLength={20} />

                    <label className="acc-label">이수구분 (선택)</label>
                    <Select value={classification} onChange={setClassification} placeholder="선택해주세요.">
                        {Object.entries(CLASSIFICATION_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                    </Select>

                    <label className="acc-label">학점</label>
                    <input className="acc-input" type="number" min="0" max="30" step="1" value={credits} onChange={(e) => setCredits(e.target.value)} placeholder="예) 3" />
                    {credits !== '' && !validCredits && <p className="acc-error">학점은 0~30 사이 정수로 입력해주세요.</p>}
                    {error && <p className="acc-error">{error}</p>}
                </div>

                <div className="acc-actions">
                    <button type="submit" className="acc-submit" disabled={!canSubmit}>추가하기</button>
                    <button type="button" className="acc-cancel" onClick={onClose}>취소</button>
                </div>
            </form>
        </div>
    );
}

export default AddPlanCourseModal;
