import { useState } from "react";
import { TERM_LABELS } from "./CompletedCourses";
import { CLASSIFICATION_COLORS, DEFAULT_COLOR, courseKey } from "./RemainingCourses";
import "./SemesterPlanner.css";

const SEMESTER_LABELS = ['1-1', '1-2', '2-1', '2-2', '3-1', '3-2', '4-1', '4-2'];
const TERM_ORDER = { FIRST: 0, SUMMER: 1, SECOND: 2, WINTER: 3 };

const nextTerm = ({ year, term }) =>
    term === 'FIRST' || term === 'SUMMER' ? { year, term: 'SECOND' } : { year: year + 1, term: 'FIRST' };

export const termLabel = (t) => `${t.year}년 ${TERM_LABELS[t.term]}`;
export const sameTerm = (a, b) => a.year === b.year && a.term === b.term;

// 배치할 학기 칸 목록 (FRONTEND_GUIDE.md "학기별 배치하기 (T18)")
// currentSemesterLabel 다음 학기부터 4-2까지, 이미 4-2 이상이면 다음 학기 1칸.
// 현재 학기의 연도·학기: gpa.terms에서 라벨이 같은 행 → 없으면 마지막 정규 학기의 다음 학기 → 없으면 오늘 날짜
// 배치된 과목이 이 범위 밖 학기에 있으면 그 칸도 함께 보여준다
export function getPlanTerms(gpa, plans) {
    const regular = gpa.terms.filter((t) => t.semesterLabel);
    const sameLabel = regular.find((t) => t.semesterLabel === gpa.currentSemesterLabel);
    let current;
    if (sameLabel) {
        current = { year: sameLabel.year, term: sameLabel.term };
    } else if (regular.length > 0) {
        const last = regular[regular.length - 1];
        current = nextTerm({ year: last.year, term: last.term });
    } else {
        const now = new Date();
        const m = now.getMonth() + 1;
        current = m >= 3 && m <= 8 ? { year: now.getFullYear(), term: 'FIRST' }
            : m >= 9 ? { year: now.getFullYear(), term: 'SECOND' }
            : { year: now.getFullYear() - 1, term: 'SECOND' };
    }

    const idx = SEMESTER_LABELS.indexOf(gpa.currentSemesterLabel);
    const count = idx === -1 ? 1 : Math.max(SEMESTER_LABELS.length - 1 - idx, 1);

    const terms = [];
    let t = current;
    for (let i = 0; i < count; i++) {
        t = nextTerm(t);
        terms.push(t);
    }
    plans.forEach((p) => {
        if (!terms.some((x) => sameTerm(x, p))) terms.push({ year: p.year, term: p.term });
    });
    return terms.sort((a, b) => a.year - b.year || TERM_ORDER[a.term] - TERM_ORDER[b.term]);
}

// 학기별 배치하기 박스
// plans = 배치된 과목 목록 (PUT /api/academic/plans 요청의 courses 모양 + id), onChange(새 목록)
function SemesterPlanner({ terms, plans, onChange, onAddClick, onReset }) {
    const [overKey, setOverKey] = useState(null);
    const [notice, setNotice] = useState('');

    const showNotice = (msg) => {
        setNotice(msg);
        setTimeout(() => setNotice(''), 2500);
    };

    const handleDrop = (e, target) => {
        e.preventDefault();
        setOverKey(null);
        let payload;
        try {
            payload = JSON.parse(e.dataTransfer.getData('application/json'));
        } catch {
            return;
        }

        const course = payload.source === 'remaining' ? payload.course : plans.find((p) => p.id === payload.id);
        if (!course) return;
        if (payload.source === 'plan' && sameTerm(course, target)) return;

        // 같은 학기에 같은 과목은 두 번 넣을 수 없다 (백엔드 INVALID_INPUT)
        const duplicated = plans.some((p) => sameTerm(p, target) && courseKey(p) === courseKey(course) && p.id !== course.id);
        if (duplicated) {
            showNotice(`${termLabel(target)}에 이미 있는 과목이에요.`);
            return;
        }

        if (payload.source === 'remaining') {
            onChange([
                ...plans,
                {
                    id: Date.now(),
                    year: target.year,
                    term: target.term,
                    courseName: course.courseName,
                    courseCode: course.courseCode,
                    classification: course.classification,
                    credits: course.credits,
                },
            ]);
        } else {
            onChange(plans.map((p) => (p.id === course.id ? { ...p, year: target.year, term: target.term } : p)));
        }
    };

    const handleChipDragStart = (e, course) => {
        e.dataTransfer.setData('application/json', JSON.stringify({ source: 'plan', id: course.id }));
        e.dataTransfer.effectAllowed = 'move';
    };

    const removeCourse = (id) => onChange(plans.filter((p) => p.id !== id));

    return (
        <div className="planner">
            <div className="planner-header">
                <div className="planner-title-wrap">
                    <h4 className="planner-title">졸업 시뮬레이션</h4>
                    <span className="planner-desc">과목을 드래그해서 원하는 학기에 배치해 보세요.</span>
                </div>
                <div className="planner-actions">
                    <button type="button" className="planner-btn" onClick={onAddClick}>수업 추가</button>
                    <button type="button" className="planner-btn" onClick={onReset} disabled={plans.length === 0}>초기화</button>
                </div>
            </div>
            {notice && <p className="planner-notice">{notice}</p>}

            <div className="planner-terms">
                {terms.map((t) => {
                    const key = `${t.year}-${t.term}`;
                    const courses = plans.filter((p) => sameTerm(p, t));
                    const credits = courses.reduce((sum, c) => sum + c.credits, 0);
                    return (
                        <div
                            key={key}
                            className={`planner-term ${overKey === key ? 'is-over' : ''}`}
                            onDragOver={(e) => { e.preventDefault(); setOverKey(key); }}
                            onDragLeave={(e) => { if (!e.currentTarget.contains(e.relatedTarget)) setOverKey(null); }}
                            onDrop={(e) => handleDrop(e, t)}
                        >
                            <div className="planner-term-head">
                                <span className="planner-term-label">{termLabel(t)}</span>
                                <span className="planner-term-credits">{credits}학점</span>
                            </div>
                            <div className="planner-drop">
                                {courses.length === 0 ? (
                                    <p className="planner-empty">과목을 여기로<br />드래그하세요.</p>
                                ) : (
                                    courses.map((c) => {
                                        const color = CLASSIFICATION_COLORS[c.classification] ?? DEFAULT_COLOR;
                                        return (
                                            <div
                                                key={c.id}
                                                className="planner-chip"
                                                style={{ backgroundColor: color.bg }}
                                                draggable
                                                onDragStart={(e) => handleChipDragStart(e, c)}
                                            >
                                                <span className="planner-chip-name">{c.courseName}</span>
                                                <span className="planner-chip-credits">{c.credits}학점</span>
                                                <button
                                                    type="button"
                                                    className="planner-chip-remove"
                                                    onClick={() => removeCourse(c.id)}
                                                    aria-label={`${c.courseName} 빼기`}
                                                >
                                                    ×
                                                </button>
                                            </div>
                                        );
                                    })
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

export default SemesterPlanner;
