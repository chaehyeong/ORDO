import { useEffect, useMemo, useState } from "react";
import { api } from "../api/client";
import { categoryColors } from "../constants/categoryColors";
import Sidebar from "../components/Sidebar";
import CalendarUrlPopup from "../components/CalendarUrlPopup";
import AddTodoModal from "../components/AddTodoModal";
import "./Calendar.css";

const WEEKDAYS = ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"];
const MAX_CHIPS = 3; // 한 칸에 보여줄 일정 개수 (넘으면 +N개)

const toDateKey = (date) =>
    `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;

// 서버 카테고리(영어) ↔ 화면 카테고리(한글)
const CATEGORY_LABELS = { LECTURE: "수업", ASSIGNMENT: "과제", PERSONAL: "개인일정" };
const CATEGORY_CODES = { 수업: "LECTURE", 과제: "ASSIGNMENT", 개인일정: "PERSONAL" };

// 서버 응답 → 화면에서 쓰던 일정 모양 { id, title, category, date, time }
// scheduleId·endTime 은 수정·삭제할 때 쓴다 (e캠퍼스 일정에는 없음)
const fromSchedule = (s) => ({
    id: `schedule-${s.id}`,
    scheduleId: s.id,
    title: s.title,
    category: CATEGORY_LABELS[s.category],
    date: s.date,
    time: s.startTime,
    endTime: s.endTime,
});
// e캠퍼스 과제 마감은 '과제'로 보여주고, 누르면 e캠퍼스 화면으로 이동
const fromEcampus = (e) => ({
    id: `ecampus-${e.id}`,
    title: e.title,
    category: "과제",
    date: e.date,
    time: e.time,
    url: e.url,
});

function Calendar() {
    const today = new Date();
    const todayKey = toDateKey(today);

    const [year, setYear] = useState(today.getFullYear());
    const [month, setMonth] = useState(today.getMonth() + 1); // 1~12
    const [events, setEvents] = useState([]);
    const [reloadKey, setReloadKey] = useState(0); // 1 올리면 일정을 다시 불러온다

    const [syncOn, setSyncOn] = useState(false);
    const [notifyOn, setNotifyOn] = useState(true);
    const [showUrlPopup, setShowUrlPopup] = useState(false);
    const [showAddModal, setShowAddModal] = useState(false);
    const [editingEvent, setEditingEvent] = useState(null); // 수정 중인 일정 (있으면 수정 창)

    // 달력 칸 42개(6주). 이전/다음 달 날짜도 채워서 보여줌
    const cells = useMemo(() => {
        const first = new Date(year, month - 1, 1);
        const start = new Date(year, month - 1, 1 - first.getDay());
        return Array.from({ length: 42 }, (_, i) => new Date(start.getFullYear(), start.getMonth(), start.getDate() + i));
    }, [year, month]);
    const from = toDateKey(cells[0]);
    const to = toDateKey(cells[cells.length - 1]);

    // 토글 처음 상태: e캠퍼스 연결 여부, 알림 설정
    useEffect(() => {
        api("/api/users/me").then((res) => {
            if (res.success) {
                setSyncOn(res.data.ecampusConnected);
                setNotifyOn(res.data.notificationEnabled);
            }
        });
    }, []);

    // 달력에 보이는 42칸 기간의 일정 + e캠퍼스 과제를 함께 불러온다
    useEffect(() => {
        let ignore = false; // 달을 빨리 넘겼을 때 늦게 온 예전 응답은 버린다
        Promise.all([
            api(`/api/schedules?from=${from}&to=${to}`),
            api(`/api/ecampus/events?from=${from}&to=${to}`),
        ]).then(([schedules, ecampus]) => {
            if (ignore) return;
            const list = schedules.success ? schedules.data.map(fromSchedule) : [];
            // e캠퍼스가 실패해도 내 일정은 보여준다
            const ecampusList = ecampus.success ? ecampus.data.events.map(fromEcampus) : [];
            setEvents([...list, ...ecampusList]);
        });
        return () => { ignore = true; };
    }, [from, to, reloadKey]);

    // 날짜별 일정 묶기 (시간 순, 종일은 맨 위)
    const eventsByDate = useMemo(() => {
        const map = {};
        events.forEach((e) => {
            (map[e.date] ||= []).push(e);
        });
        Object.values(map).forEach((list) =>
            list.sort((a, b) => (a.time ?? "").localeCompare(b.time ?? ""))
        );
        return map;
    }, [events]);

    // 캘린더 연동(e캠퍼스): 켜면 URL 팝업, 끄면 서버에서 연결 해제
    const handleSyncToggle = async () => {
        if (syncOn) {
            const res = await api("/api/ecampus/feed", { method: "DELETE" });
            if (res.success) {
                setSyncOn(false);
                setReloadKey((k) => k + 1);
            } else {
                alert(res.error.message);
            }
        } else {
            setShowUrlPopup(true);
        }
    };

    // 피드 주소는 비밀번호 같은 값이라 화면 state 에 남기지 않고 서버에만 보낸다
    const handleUrlConfirm = async (url) => {
        const res = await api("/api/ecampus/feed", {
            method: "PUT",
            body: JSON.stringify({ feedUrl: url }),
        });
        if (res.success) {
            setSyncOn(true);
            setShowUrlPopup(false);
            setReloadKey((k) => k + 1);
        } else {
            alert(res.error.message);
        }
    };

    const handleUrlCancel = () => {
        setShowUrlPopup(false);
        setSyncOn(false);
    };

    // 알림 받기: 화면 먼저 바꾸고, 서버 저장이 실패하면 되돌린다
    const handleNotifyToggle = async () => {
        const next = !notifyOn;
        setNotifyOn(next);
        const res = await api("/api/users/me/settings", {
            method: "PATCH",
            body: JSON.stringify({ notificationEnabled: next }),
        });
        if (!res.success) {
            setNotifyOn(!next);
            alert(res.error.message);
        }
    };

    const handleAddTodo = async (todo) => {
        const res = await api("/api/schedules", {
            method: "POST",
            body: JSON.stringify({
                title: todo.title,
                category: CATEGORY_CODES[todo.category],
                date: todo.date,
                startTime: todo.time,
                endTime: todo.endTime,
            }),
        });
        if (res.success) {
            setEvents((prev) => [...prev, fromSchedule(res.data)]);
            setShowAddModal(false);
        } else {
            alert(res.error.message);
        }
    };

    // 일정 수정: PATCH 는 보낸 필드만 바뀐다
    const handleEditTodo = async (todo) => {
        const res = await api(`/api/schedules/${editingEvent.scheduleId}`, {
            method: "PATCH",
            body: JSON.stringify({
                title: todo.title,
                category: CATEGORY_CODES[todo.category],
                date: todo.date,
                startTime: todo.time,
                endTime: todo.endTime,
            }),
        });
        if (res.success) {
            setEvents((prev) => prev.map((e) => (e.id === editingEvent.id ? fromSchedule(res.data) : e)));
            setEditingEvent(null);
        } else {
            alert(res.error.message);
        }
    };

    // 일정 삭제 (칩에 마우스를 올리면 뜨는 ×)
    const handleDeleteTodo = async (event) => {
        if (!window.confirm(`'${event.title}' 일정을 삭제할까요?`)) return;
        const res = await api(`/api/schedules/${event.scheduleId}`, { method: "DELETE" });
        if (res.success) {
            setEvents((prev) => prev.filter((e) => e.id !== event.id));
        } else {
            alert(res.error.message);
        }
    };

    const goToday = () => {
        setYear(today.getFullYear());
        setMonth(today.getMonth() + 1);
    };

    return (
        <div className="layout">
            <Sidebar />
            <div className="page">
                <div className="cal-header">
                    <h2 className="page-title cal-heading">{year}년 {month}월</h2>
                </div>

                <div className="cal-selects">
                    <select value={year} onChange={(e) => setYear(Number(e.target.value))}>
                        {Array.from({ length: 11 }, (_, i) => today.getFullYear() - 5 + i).map((y) => (
                            <option key={y} value={y}>{y}년</option>
                        ))}
                    </select>
                    <select value={month} onChange={(e) => setMonth(Number(e.target.value))}>
                        {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
                            <option key={m} value={m}>{m}월</option>
                        ))}
                    </select>
                    {/* 시간표 페이지처럼 연/월 선택 옆에 나란히: 연도 / 월 / 오늘 / 할 일 추가 */}
                    <button className="cal-today-btn" onClick={goToday}>오늘</button>
                    {/* 카테고리 색 안내 (오늘 버튼 옆) */}
                    <div className="cal-legend">
                        {Object.entries(categoryColors).map(([name, color]) => (
                            <span key={name} className="cal-legend-item">
                                <i style={{ backgroundColor: color }} />
                                {name}
                            </span>
                        ))}
                    </div>
                    <button className="cal-add-btn" onClick={() => setShowAddModal(true)}>+ 할 일 추가</button>
                </div>

                <div className="cal-weekdays">
                    {WEEKDAYS.map((d) => <span key={d}>{d}</span>)}
                </div>

                <div className="box cal-grid">
                    {cells.map((date) => {
                        const key = toDateKey(date);
                        const list = eventsByDate[key] ?? [];
                        const classes = [
                            "cal-cell",
                            date.getDay() === 0 ? "is-sunday" : "",
                            date.getMonth() !== month - 1 ? "is-other-month" : "",
                        ].join(" ");
                        return (
                            <div key={key} className={classes}>
                                <span className={`cal-day ${key === todayKey ? "is-today" : ""}`}>{date.getDate()}</span>
                                {list.slice(0, MAX_CHIPS).map((e) => (
                                    <div
                                        key={e.id}
                                        className={`cal-chip ${e.url ? "" : "is-editable"}`}
                                        style={{ backgroundColor: `${categoryColors[e.category]}33`, color: categoryColors[e.category] }}
                                        title={`${e.time ?? "종일"} ${e.title}`}
                                        // e캠퍼스 일정은 e캠퍼스 화면으로, 내 일정은 수정 창
                                        onClick={e.url ? () => window.open(e.url, "_blank") : () => setEditingEvent(e)}
                                    >
                                        {e.title}
                                        {!e.url && (
                                            <button
                                                type="button"
                                                className="cal-chip-remove"
                                                onClick={(ev) => { ev.stopPropagation(); handleDeleteTodo(e); }}
                                                aria-label="삭제"
                                            >
                                                ×
                                            </button>
                                        )}
                                    </div>
                                ))}
                                {list.length > MAX_CHIPS && <span className="cal-more">+{list.length - MAX_CHIPS}개</span>}
                            </div>
                        );
                    })}
                </div>

                <div className="cal-footer">
                    <div className="box cal-settings">
                        <div className="cal-setting-row">
                            <span>캘린더 연동</span>
                            <Toggle checked={syncOn} onChange={handleSyncToggle} />
                        </div>
                        <div className="cal-setting-row">
                            <span>알림 받기</span>
                            <Toggle checked={notifyOn} onChange={handleNotifyToggle} />
                        </div>

                        {showUrlPopup && (
                            <CalendarUrlPopup onConfirm={handleUrlConfirm} onCancel={handleUrlCancel} />
                        )}
                    </div>
                </div>
            </div>

            {showAddModal && (
                <AddTodoModal onClose={() => setShowAddModal(false)} onSubmit={handleAddTodo} />
            )}
            {editingEvent && (
                <AddTodoModal initialTodo={editingEvent} onClose={() => setEditingEvent(null)} onSubmit={handleEditTodo} />
            )}
        </div>
    );
}

function Toggle({ checked, onChange }) {
    return (
        <button
            type="button"
            role="switch"
            aria-checked={checked}
            className={`cal-toggle ${checked ? "is-on" : ""}`}
            onClick={onChange}
        >
            <span className="cal-toggle-knob" />
        </button>
    );
}

export default Calendar;
