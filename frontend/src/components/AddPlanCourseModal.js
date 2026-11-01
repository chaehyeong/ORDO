import { useEffect, useMemo, useState } from "react";
import { FiX } from "react-icons/fi";
import { CLASSIFICATION_LABELS } from "./CompletedCourses";
import { Select } from "./AddCompletedCourseModal";
import { termLabel } from "./SemesterPlanner";
import distributionCatalog from "../data/distributionCourses.json";
import "./AddCompletedCourseModal.css";
import "./AddPlanCourseModal.css";

const normalizeSearch = (value) => value.normalize('NFKC').replace(/\s/g, '').toLocaleLowerCase();

// 배분이수 편성 과목 검색 또는 직접 입력 → 기존 학기별 계획 API로 저장한다.
// 선택 목록의 학수번호가 비어 있으면 원문대로 null을 보낸다.
function AddPlanCourseModal({ terms, onClose, onSubmit, initialMode = 'manual' }) {
    const [mode, setMode] = useState(initialMode);
    const [termKey, setTermKey] = useState(terms.length ? `${terms[0].year}-${terms[0].term}` : '');
    const [query, setQuery] = useState('');
    const [selectedCourse, setSelectedCourse] = useState(null);
    const [courseName, setCourseName] = useState('');
    const [classification, setClassification] = useState('');
    const [credits, setCredits] = useState('');
    const [error, setError] = useState('');

    useEffect(() => {
        const onKey = (e) => e.key === 'Escape' && onClose();
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [onClose]);

    const filteredCourses = useMemo(() => {
        const search = normalizeSearch(query);
        return distributionCatalog.courses.filter((course) =>
            normalizeSearch(course.courseName).includes(search)
            || normalizeSearch(course.courseCode ?? '').includes(search));
    }, [query]);

    const selectedTerm = terms.find((term) => `${term.year}-${term.term}` === termKey);
    const creditsNum = Number(credits);
    const validCredits = credits !== '' && Number.isInteger(creditsNum) && creditsNum >= 0 && creditsNum <= 30;
    const isDistribution = mode === 'distribution';
    const canSubmit = Boolean(selectedTerm && (isDistribution ? selectedCourse : courseName.trim() && validCredits));

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!canSubmit) return;
        const course = isDistribution ? selectedCourse : {
            courseName: courseName.trim(),
            classification: classification || null,
            credits: creditsNum,
        };
        const message = onSubmit({
            year: selectedTerm.year,
            term: selectedTerm.term,
            courseName: course.courseName,
            courseCode: course.courseCode ?? null,
            classification: course.classification,
            credits: course.credits,
        });
        if (message) setError(message);
    };

    const changeMode = (next) => {
        setMode(next);
        setError('');
    };

    return (
        <div className="acc-backdrop" onClick={onClose}>
            <form
                className="acc-panel"
                role="dialog"
                aria-modal="true"
                aria-labelledby="plan-course-title"
                onClick={(e) => e.stopPropagation()}
                onSubmit={handleSubmit}
            >
                <div className="acc-head">
                    <div>
                        <h2 id="plan-course-title">{isDistribution ? '배분이수 선택' : '수업 추가'}</h2>
                        <p>{isDistribution ? '과목을 찾아 원하는 학기에 배치하세요.' : '학기에 배치할 수업 정보를 입력해주세요.'}</p>
                    </div>
                    <button type="button" className="acc-close" onClick={onClose} aria-label="닫기"><FiX /></button>
                </div>

                <div className="acc-fields">
                    <div className="apc-modes" aria-label="수업 추가 방식">
                        <button type="button" className="apc-mode" aria-pressed={isDistribution} onClick={() => changeMode('distribution')}>배분이수 검색</button>
                        <button type="button" className="apc-mode" aria-pressed={!isDistribution} onClick={() => changeMode('manual')}>직접 입력</button>
                    </div>

                    <label className="acc-label">학기
                        <Select value={termKey} onChange={(value) => { setTermKey(value); setError(''); }}>
                            {terms.map((term) => (
                                <option key={`${term.year}-${term.term}`} value={`${term.year}-${term.term}`}>{termLabel(term)}</option>
                            ))}
                        </Select>
                    </label>

                    {isDistribution ? (
                        <>
                            <label className="acc-label" htmlFor="distribution-search">배분이수 과목 검색</label>
                            <input
                                id="distribution-search"
                                className="acc-input"
                                type="search"
                                value={query}
                                onChange={(e) => { setQuery(e.target.value); setSelectedCourse(null); setError(''); }}
                                placeholder="과목명 또는 학수번호"
                                autoFocus
                            />
                            <p className="apc-source">
                                {distributionCatalog.source.year}학년도 {distributionCatalog.source.campus} 교육과정 기준입니다.
                                실제 개설 여부는 수강신청에서 확인하세요.
                            </p>
                            <p className="apc-count" role="status">검색 결과 {filteredCourses.length}개</p>
                            <div className="apc-results" role="group" aria-label="배분이수 검색 결과">
                                {filteredCourses.length === 0 ? (
                                    <p className="apc-empty">검색 결과가 없어요. 다른 검색어로 찾아보거나 직접 입력해 주세요.</p>
                                ) : filteredCourses.map((course) => (
                                    <label key={course.id} className={`apc-course ${selectedCourse?.id === course.id ? 'is-selected' : ''}`}>
                                        <input
                                            type="radio"
                                            name="distribution-course"
                                            value={course.id}
                                            checked={selectedCourse?.id === course.id}
                                            onChange={() => { setSelectedCourse(course); setError(''); }}
                                        />
                                        <span className="apc-course-info">
                                            <span className="apc-course-name">{course.courseName}</span>
                                            <span className="apc-course-meta">{course.courseCode ?? '학수번호 미기재'} · 배분이수 · {course.credits}학점</span>
                                        </span>
                                    </label>
                                ))}
                            </div>
                            {selectedCourse && (
                                <p className="apc-selected">선택: {selectedCourse.courseName} · {selectedCourse.credits}학점</p>
                            )}
                        </>
                    ) : (
                        <>
                            <label className="acc-label" htmlFor="plan-course-name">과목명</label>
                            <input id="plan-course-name" className="acc-input" value={courseName} onChange={(e) => { setCourseName(e.target.value); setError(''); }} placeholder="예) 평면디자인" maxLength={100} autoFocus />

                            <label className="acc-label">이수구분 (선택)
                                <Select value={classification} onChange={setClassification} placeholder="선택해주세요.">
                                    {Object.entries(CLASSIFICATION_LABELS).map(([key, value]) => <option key={key} value={key}>{value}</option>)}
                                </Select>
                            </label>

                            <label className="acc-label" htmlFor="plan-course-credits">학점</label>
                            <input id="plan-course-credits" className="acc-input" type="number" min="0" max="30" step="1" value={credits} onChange={(e) => setCredits(e.target.value)} placeholder="예) 3" />
                            {credits !== '' && !validCredits && <p className="acc-error">학점은 0~30 사이 정수로 입력해주세요.</p>}
                        </>
                    )}
                    {error && <p className="acc-error" role="alert">{error}</p>}
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
