import { useMemo, useState } from "react";
import { mockTimetable } from "../mock/timetableMock";
import Sidebar from "../components/Sidebar";
import AddCourseModal from "../components/AddCourseModal";
import "./TimeTable.css";

const DAYS = ["월", "화", "수", "목", "금"];
const HOUR_H = 66; // 1시간당 높이(px)
const DEFAULT_START = 9; // 기본 표시 범위 09:00 ~ 18:00 (이 범위 밖 수업이 있으면 자동 확장)
const DEFAULT_END = 18;

const CURRENT_SEMESTER = "2026-2";
const SEMESTERS = ["2025-1", "2025-2", "2026-1", "2026-2"];

// 수업별 블록 색 (수업 이름 순서대로 돌아가며 배정)
const COLORS = ["#86C0CB", "#C3EDE5", "#F2F3D9", "#F4F8F9", "#F6DCDC", "#E3DCF5"];

const toMinutes = (time) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
const semesterLabel = (s) => {
    const [y, n] = s.split("-");
    return `${y}년 ${n}학기`;
};

function TimeTable() {
    const [courses, setCourses] = useState(mockTimetable);
    const [semester, setSemester] = useState(CURRENT_SEMESTER);
    const [showModal, setShowModal] = useState(false);

    // semester 필드가 없는 기존 데이터는 현재 학기로 취급
    const semesterCourses = useMemo(
        () => courses.filter((c) => (c.semester ?? CURRENT_SEMESTER) === semester),
        [courses, semester]
    );
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
    const gridHeight = (endHour - startHour) * HOUR_H;

    const blockStyle = (c) => ({
        top: ((toMinutes(c.startTime) - startHour * 60) / 60) * HOUR_H,
        height: ((toMinutes(c.endTime) - toMinutes(c.startTime)) / 60) * HOUR_H,
        backgroundColor: colorOf[c.courseName],
    });

    const handleAdd = (course) => {
        setCourses((prev) => [...prev, { id: Date.now(), semester, ...course }]);
        setShowModal(false);
    };

    const [year, term] = semester.split("-");

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
                    <button className="tt-add-btn" onClick={() => setShowModal(true)}>수업 추가</button>
                </div>

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
                                            </div>
                                        ))}
                                </div>
                            ))}
                        </div>
                    </div>

                    {onlineCourses.length > 0 && (
                        <div className="tt-online">
                            {onlineCourses.map((c) => (
                                <div key={c.id} className="tt-online-item">{c.courseName}</div>
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
        </div>
    );
}

export default TimeTable;
