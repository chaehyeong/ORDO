import { useRef, useState } from "react";
import { createPortal } from "react-dom";
import { api } from "../api/client";
import { DISTRIBUTION_AREAS, GRADES } from "./AddCompletedCourseModal";
import { CLASSIFICATION_LABELS, TERM_LABELS } from "./CompletedCourses";
import "./AddCourseModal.css"; // 확인 창은 수업 추가 창과 같은 모양을 쓴다
import "./ImportButton.css";

const WRONG_FILE = {
    TIMETABLE: "성적표 파일이에요. 졸업 로드맵의 '성적표 불러오기'에서 올려 주세요.",
    GRADES: "수강신청확인서 파일이에요. 시간표의 '수강신청확인서 불러오기'에서 올려 주세요.",
};
const FAILED = "파일을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.";
// 이 경고들은 과목 줄의 고르는 칸으로 대신 보여준다
const PICK_WARNINGS = ["UNKNOWN_CLASSIFICATION", "DISTRIBUTION_AREA_REQUIRED", "UNKNOWN_GRADE"];

const termLabel = ({ year, term }) => `${year}년 ${TERM_LABELS[term]}`;
const isDistribution = (course) => course.classification === "GEN_DISTRIBUTION";
const isMissingChoice = (course) => !course.classification || (isDistribution(course) && !course.distributionArea);

// 파일을 고르면 서버 미리보기를 확인 창에 보여주고, 사용자가 누르면 저장한다
// kind: "TIMETABLE"(수강신청확인서) 또는 "GRADES"(성적표). 서버 응답의 type 과 같은 값
// className: 옆 버튼과 같은 모양을 쓰도록 페이지에서 넘긴다
// onSaved(draft): 저장 후 부모가 다시 불러온다. draft 는 시간표면 timetable, 성적표면 grades
function ImportButton({ kind, label, className, onSaved }) {
    const inputRef = useRef(null);
    const [reading, setReading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [preview, setPreview] = useState(null); // 파일 내용은 저장 전까지 화면 state 에만 둔다
    const [courses, setCourses] = useState([]); // 성적표 과목. 사용자가 고른 값이 여기에 반영된다

    const handleFile = async (e) => {
        const file = e.target.files[0];
        e.target.value = ""; // 같은 파일을 다시 골라도 동작하게 비운다
        if (!file) return;

        const form = new FormData();
        form.append("file", file);
        setReading(true);
        try {
            const res = await api("/api/import", { method: "POST", body: form });
            if (!res.success) {
                alert(res.error.message); // 예: 다른 학번의 파일, PDF
            } else if (res.data.type !== kind) {
                alert(WRONG_FILE[kind]);
            } else {
                // 값을 골라야 하는 과목을 맨 위로 올린다
                setCourses([...(res.data.grades?.courses ?? [])].sort((a, b) => b.needs.length - a.needs.length));
                setPreview(res.data);
            }
        } catch {
            alert(FAILED);
        } finally {
            setReading(false);
        }
    };

    const updateCourse = (index, changes) => {
        setCourses((prev) => prev.map((c, i) => (i === index ? { ...c, ...changes } : c)));
    };

    const save = async () => {
        const { timetable, grades } = preview;
        setSaving(true);
        try {
            const res = timetable
                ? await api("/api/timetables/entries/bulk", {
                    method: "POST",
                    body: JSON.stringify({ year: timetable.year, term: timetable.term, mode: "REPLACE", entries: timetable.entries }),
                })
                : await api("/api/academic/completed-courses/bulk", {
                    method: "POST",
                    body: JSON.stringify({
                        courses: courses.map((c) => ({
                            courseCode: c.courseCode,
                            courseName: c.courseName,
                            credits: c.credits,
                            classification: c.classification,
                            distributionArea: isDistribution(c) ? c.distributionArea : null,
                            grade: c.grade,
                            year: c.year,
                            term: c.term,
                        })),
                        replaceTerms: grades.terms,
                        keepClassification: true,
                    }),
                });
            if (res.success) {
                setPreview(null);
                onSaved(timetable ?? grades);
            } else {
                alert(res.error.message); // 예: 파일 안에서 수업 시간이 겹치면 TIMETABLE_OVERLAP
            }
        } catch {
            alert(FAILED);
        } finally {
            setSaving(false);
        }
    };

    const renderPreview = () => {
        const { timetable, grades, warnings } = preview;
        const draft = timetable ?? grades;
        const canSave = !saving && (timetable ? timetable.entries.length > 0 : courses.length > 0 && !courses.some(isMissingChoice));

        return (
            <div className="course-backdrop" onClick={() => setPreview(null)}>
                <div className="course-modal" onClick={(e) => e.stopPropagation()}>
                    <h2 className="course-title">{timetable ? "수강신청확인서 확인" : "성적표 확인"}</h2>
                    <p className="import-summary">
                        {timetable ? termLabel(timetable) : `${grades.terms.length}개 학기`} · {draft.courseCount}과목 · {draft.totalCredits}학점
                    </p>

                    <ul className="import-list">
                        {timetable && timetable.courses.map((c) => (
                            <li key={`${c.courseCode}-${c.section}`} className="import-row">
                                <span className="import-name">{c.courseName}</span>
                                <span className="import-meta">{c.classificationText} · {c.credits}학점</span>
                            </li>
                        ))}
                        {grades && courses.map((c, i) => (
                            <li key={i} className="import-row">
                                <span className="import-name">{c.courseName}</span>
                                <span className="import-meta">{termLabel(c)} · {c.credits}학점{c.grade ? ` · ${c.grade}` : ""}</span>
                                {c.needs.includes("CLASSIFICATION") && (
                                    <select
                                        className="course-input import-select"
                                        aria-label={`${c.courseName} 이수구분`}
                                        value={c.classification ?? ""}
                                        onChange={(e) => updateCourse(i, { classification: e.target.value })}
                                    >
                                        <option value="" disabled>이수구분 선택</option>
                                        {Object.entries(CLASSIFICATION_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                                    </select>
                                )}
                                {isDistribution(c) && (
                                    <select
                                        className="course-input import-select"
                                        aria-label={`${c.courseName} 배분이수 영역`}
                                        value={c.distributionArea ?? ""}
                                        onChange={(e) => updateCourse(i, { distributionArea: Number(e.target.value) })}
                                    >
                                        <option value="" disabled>배분이수 영역 선택</option>
                                        {Object.entries(DISTRIBUTION_AREAS).map(([a, name]) => <option key={a} value={a}>{a}영역 · {name}</option>)}
                                    </select>
                                )}
                                {c.needs.includes("GRADE") && (
                                    <select
                                        className="course-input import-select"
                                        aria-label={`${c.courseName} 성적`}
                                        value={c.grade ?? ""}
                                        onChange={(e) => updateCourse(i, { grade: e.target.value || null })}
                                    >
                                        <option value="">성적 없음</option>
                                        {GRADES.map((g) => <option key={g} value={g}>{g}</option>)}
                                    </select>
                                )}
                            </li>
                        ))}
                    </ul>

                    {courses.some(isMissingChoice) && <p className="import-warning">맨 위 과목의 값을 고르면 저장할 수 있어요.</p>}
                    {warnings.filter((w) => !PICK_WARNINGS.includes(w.code)).map((w, i) => (
                        <p key={i} className="import-warning">{w.message}</p>
                    ))}

                    <p className="import-notice">
                        {timetable
                            ? `저장하면 ${termLabel(timetable)} 시간표가 이 파일 내용으로 바뀌어요. 직접 넣은 수업도 지워져요.`
                            : `저장하면 ${grades.terms.map(termLabel).join(", ")}의 이수 과목이 이 파일 내용으로 바뀌어요. 직접 넣은 과목도 지워져요.`}
                    </p>

                    <div className="course-actions">
                        <button type="button" className="course-cancel" onClick={() => setPreview(null)}>취소</button>
                        <button type="button" className="course-confirm" disabled={!canSave} onClick={save}>저장</button>
                    </div>
                </div>
            </div>
        );
    };

    return (
        <>
            <button type="button" className={`${className} import-btn`} disabled={reading} onClick={() => inputRef.current.click()}>
                {reading ? "읽는 중…" : label}
            </button>
            <input ref={inputRef} type="file" accept=".xlsx,.csv,.txt" hidden onChange={handleFile} />
            {preview && createPortal(renderPreview(), document.body)}
        </>
    );
}

export default ImportButton;
