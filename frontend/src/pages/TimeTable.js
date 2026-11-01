import { useEffect, useMemo, useState } from "react";
import { api } from "../api/client";
import Sidebar from "../components/Sidebar";
import AddCourseModal from "../components/AddCourseModal";
import ImportButton from "../components/ImportButton";
import "./TimeTable.css";

const DAYS = ["월", "화", "수", "목", "금"];
// 서버는 요일을 숫자(1=월 … 7=일)로 주고받는다
const ALL_DAYS = ["월", "화", "수", "목", "금", "토", "일"];
// "2026-2" 의 뒷자리 → 서버 학기 값
const TERMS = { 1: "FIRST", 2: "SECOND" };
const HOUR_H = 66; // 1시간당 높이(px) — 클수록 세로로 길어짐
const GRID_BOTTOM = 20; // 마지막 시간선 아래로 흰 격자를 더 늘리는 높이(px)
const DEFAULT_START = 9; // 기본 표시 범위 09:00 ~ 18:00 (이 범위 밖 수업이 있으면 자동 확장)
const DEFAULT_END = 18;

// 드롭다운에 보여줄 학기 수 (현재 학기 포함, 최근 순)
const SEMESTER_COUNT = 4;

// 서버와 같은 규칙: 3~8월은 1학기, 9~2월은 2학기 (1~2월은 전년도 2학기)
const getCurrentSemester = (date = new Date()) => {
    const y = date.getFullYear();
    const m = date.getMonth() + 1;
    if (m >= 3 && m <= 8) return `${y}-1`;
    return m >= 9 ? `${y}-2` : `${y - 1}-2`;
};

// 현재 학기에서 거꾸로 count개 → 오래된 순으로 정렬 (예: 2025-1, 2025-2, 2026-1, 2026-2)
const getRecentSemesters = (current, count) => {
    let [y, n] = current.split("-").map(Number);
    const list = [];
    for (let i = 0; i < count; i++) {
        list.unshift(`${y}-${n}`);
        if (n === 2) n = 1;
        else { n = 2; y -= 1; }
    }
    return list;
};

const CURRENT_SEMESTER = getCurrentSemester();
const SEMESTERS = getRecentSemesters(CURRENT_SEMESTER, SEMESTER_COUNT);

// 수업별 블록 색 (수업 이름 순서대로 돌아가며 배정)
const COLORS = ["#86C0CB", "#C3EDE5", "#F2F3D9", "#F4F8F9", "#F6DCDC", "#E3DCF5"];

// 블록 투명도 (색 코드 뒤에 붙는 2자리). 낮을수록 더 투명
// FF=100%, E6=90%, CC=80%, B3=70%, 99=60%, 80=50%
const BLOCK_ALPHA = "FF";

const toMinutes = (time) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
// 서버 응답 한 칸 → 화면에서 쓰던 모양 (day: '월')
const toCourse = (entry) => ({
    ...entry,
    day: entry.dayOfWeek ? ALL_DAYS[entry.dayOfWeek - 1] : null,
});
const semesterLabel = (s) => {
    const [y, n] = s.split("-");
    return `${y}년 ${n}학기`;
};

function TimeTable() {
    const [courses, setCourses] = useState([]);
    const [semester, setSemester] = useState(CURRENT_SEMESTER);
    const [showModal, setShowModal] = useState(false);
    const [editingCourse, setEditingCourse] = useState(null); // 수정 중인 수업 (있으면 수정 창)
    const [error, setError] = useState("");
    const [reloadKey, setReloadKey] = useState(0); // 1 올리면 시간표를 다시 불러온다

    const [year, term] = semester.split("-");

    // 학기가 바뀔 때마다 그 학기 시간표를 불러온다
    useEffect(() => {
        let ignore = false; // 학기를 빨리 바꿨을 때 늦게 온 예전 응답은 버린다
        setCourses([]);
        setError("");
        api(`/api/timetables?year=${year}&term=${TERMS[term]}`).then((res) => {
            if (ignore) return;
            if (res.success) {
                setCourses(res.data.entries.map(toCourse));
            } else {
                setError(res.error.message);
            }
        });
        return () => { ignore = true; };
    }, [year, term, reloadKey]);

    // 서버가 고른 학기의 수업만 주므로 학기로 거를 필요가 없다
    const semesterCourses = courses;
    const classes = semesterCourses.filter((c) => !c.online);
    const onlineCourses = semesterCourses.filter((c) => c.online);

    // 수업 이름별 색 배정
    const colorOf = useMemo(() => {
        const map = {};
        semesterCourses.forEach((c) => {
            if (!(c.courseName in map)) map[c.courseName] = COLORS[Object.keys(map).length % COLORS.length];
        });
        return map;
    }, [semesterCourses]);

    // 시간표 표시 범위
    const startHour = Math.min(DEFAULT_START, ...classes.map((c) => Math.floor(toMinutes(c.startTime) / 60)));
    const endHour = Math.max(DEFAULT_END, ...classes.map((c) => Math.ceil(toMinutes(c.endTime) / 60)));
    const hours = Array.from({ length: endHour - startHour + 1 }, (_, i) => startHour + i);
    const gridHeight = (endHour - startHour) * HOUR_H + GRID_BOTTOM;

    const blockStyle = (c) => ({
        top: ((toMinutes(c.startTime) - startHour * 60) / 60) * HOUR_H,
        height: ((toMinutes(c.endTime) - toMinutes(c.startTime)) / 60) * HOUR_H,
        backgroundColor: `${colorOf[c.courseName]}${BLOCK_ALPHA}`,
    });

    const handleAdd = async (course) => {
        const res = await api("/api/timetables/entries", {
            method: "POST",
            body: JSON.stringify({
                year: Number(year),
                term: TERMS[term],
                courseName: course.courseName,
                dayOfWeek: course.online ? null : ALL_DAYS.indexOf(course.day) + 1,
                startTime: course.startTime ?? null,
                endTime: course.endTime ?? null,
                location: course.location ?? null,
                online: !!course.online,
            }),
        });
        if (res.success) {
            setCourses((prev) => [...prev, toCourse(res.data)]);
            setShowModal(false);
        } else {
            alert(res.error.message); // 예: 시간이 겹치는 수업이 있으면 TIMETABLE_OVERLAP
        }
    };

    // 파일로 저장한 뒤: 파일의 학기로 이동해 다시 불러온다
    const handleImported = (saved) => {
        const savedSemester = `${saved.year}-${Object.keys(TERMS).find((n) => TERMS[n] === saved.term)}`;
        if (SEMESTERS.includes(savedSemester)) {
            setSemester(savedSemester);
            setReloadKey((k) => k + 1);
        } else {
            alert("저장했어요. 이 학기는 목록에 없어 여기서는 볼 수 없어요.");
        }
    };

    // 수업 수정: PATCH 는 보낸 필드만 바뀐다. 온라인으로 바꾸면 서버가 요일·시간을 지운다
    const handleEdit = async (course) => {
        const res = await api(`/api/timetables/entries/${editingCourse.id}`, {
            method: "PATCH",
            body: JSON.stringify({
                courseName: course.courseName,
                dayOfWeek: course.online ? null : ALL_DAYS.indexOf(course.day) + 1,
                startTime: course.startTime ?? null,
                endTime: course.endTime ?? null,
                location: course.online ? null : course.location ?? "", // null 은 "그대로"라서 지울 땐 빈 문자열
                online: !!course.online,
            }),
        });
        if (res.success) {
            setCourses((prev) => prev.map((c) => (c.id === editingCourse.id ? toCourse(res.data) : c)));
            setEditingCourse(null);
        } else {
            alert(res.error.message); // 예: 시간이 겹치는 수업이 있으면 TIMETABLE_OVERLAP
        }
    };

    // 수업 삭제
    const handleDelete = async (course) => {
        if (!window.confirm(`'${course.courseName}' 수업을 삭제할까요?`)) return;
        const res = await api(`/api/timetables/entries/${course.id}`, { method: "DELETE" });
        if (res.success) {
            setCourses((prev) => prev.filter((c) => c.id !== course.id));
        } else {
            alert(res.error.message);
        }
    };

    // 마우스를 올리면 가운데에 뜨는 수정·삭제 버튼 (졸업 로드맵 이수 과목 표와 같은 모양)
    const renderActions = (c) => (
        <div className="tt-actions">
            <button type="button" className="tt-edit-btn" onClick={() => setEditingCourse(c)}>수정</button>
            <button type="button" className="tt-delete-btn" onClick={() => handleDelete(c)}>삭제</button>
        </div>
    );

    return (
        <div className="layout">
            <Sidebar />
            <div className="page">
                <h2 className="page-title tt-heading">{year} {term}학기 시간표</h2>

                <div className="tt-toolbar">
                    <select className="tt-semester" value={semester} onChange={(e) => setSemester(e.target.value)}>
                        {SEMESTERS.map((s) => (
                            <option key={s} value={s}>{semesterLabel(s)}</option>
                        ))}
                    </select>
                    <ImportButton kind="TIMETABLE" label="수강신청확인서 불러오기" className="tt-add-btn" onSaved={handleImported} />
                    <button className="tt-add-btn" onClick={() => setShowModal(true)}>수업 추가</button>
                </div>

                {error && <p className="text-danger">{error}</p>}

                <div className="box tt-box">
                    <div className="tt-board">
                        <div />
                        <div className="tt-days">
                            {DAYS.map((d) => <span key={d}>{d}</span>)}
                        </div>

                        <div className="tt-times" style={{ height: gridHeight }}>
                            {hours.map((h) => (
                                <span key={h} style={{ top: (h - startHour) * HOUR_H }}>
                                    {String(h).padStart(2, "0")}:00
                                </span>
                            ))}
                        </div>

                        <div className="tt-grid" style={{ height: gridHeight, "--hour-h": `${HOUR_H}px` }}>
                            {DAYS.map((day) => (
                                <div key={day} className="tt-col">
                                    {classes
                                        .filter((c) => c.day === day)
                                        .map((c) => (
                                            <div key={`${c.id}-${c.day}`} className="tt-block" style={blockStyle(c)}>
                                                <p className="tt-block-name">{c.courseName}</p>
                                                {c.location && <p className="tt-block-room">{c.location}</p>}
                                                {renderActions(c)}
                                            </div>
                                        ))}
                                </div>
                            ))}
                        </div>
                    </div>

                    {onlineCourses.length > 0 && (
                        <div className="tt-online">
                            {onlineCourses.map((c) => (
                                <div key={c.id} className="tt-online-item">
                                    {c.courseName}
                                    {renderActions(c)}
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>

            {showModal && (
                <AddCourseModal
                    courses={classes}
                    onClose={() => setShowModal(false)}
                    onSubmit={handleAdd}
                />
            )}
            {editingCourse && (
                <AddCourseModal
                    courses={classes}
                    initialCourse={editingCourse}
                    onClose={() => setEditingCourse(null)}
                    onSubmit={handleEdit}
                />
            )}
        </div>
    );
}

export default TimeTable;
