import { useMemo, useState } from "react";
import { mockCalendarEvents, categoryColors } from "../mock/calendarMock";
import Sidebar from "../components/Sidebar";
import CalendarUrlPopup from "../components/CalendarUrlPopup";
import AddTodoModal from "../components/AddTodoModal";
import "./Calendar.css";

const WEEKDAYS = ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"];
const MAX_CHIPS = 3; // 한 칸에 보여줄 일정 개수 (넘으면 +N개)

const toDateKey = (date) =>
    `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;

function Calendar() {
    const today = new Date();
    const todayKey = toDateKey(today);

    const [year, setYear] = useState(today.getFullYear());
    const [month, setMonth] = useState(today.getMonth() + 1); // 1~12
    const [events, setEvents] = useState(mockCalendarEvents);

    const [syncOn, setSyncOn] = useState(false);
    const [notifyOn, setNotifyOn] = useState(true);
    const [calendarUrl, setCalendarUrl] = useState("");
    const [showUrlPopup, setShowUrlPopup] = useState(false);
    const [showAddModal, setShowAddModal] = useState(false);

    // 달력 칸 42개(6주). 이전/다음 달 날짜도 채워서 보여줌
    const cells = useMemo(() => {
        const first = new Date(year, month - 1, 1);
        const start = new Date(year, month - 1, 1 - first.getDay());
        return Array.from({ length: 42 }, (_, i) => new Date(start.getFullYear(), start.getMonth(), start.getDate() + i));
    }, [year, month]);

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

    // 캘린더 연동: 켜면 URL 팝업, 끄면 URL 삭제
    const handleSyncToggle = () => {
        if (syncOn) {
            setSyncOn(false);
            setCalendarUrl("");
        } else {
            setShowUrlPopup(true);
        }
    };

    const handleUrlConfirm = (url) => {
        setCalendarUrl(url);
        setSyncOn(true);
        setShowUrlPopup(false);
        // TODO: calendarUrl(.ics 등)에서 일정 불러와서 setEvents에 합치기
    };

    const handleUrlCancel = () => {
        setShowUrlPopup(false);
        setSyncOn(false);
    };

    const handleAddTodo = (todo) => {
        setEvents((prev) => [...prev, { id: Date.now(), subject: null, location: null, ...todo }]);
        setShowAddModal(false);
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
                    <button className="cal-today-btn" onClick={goToday}>오늘</button>
                    <button className="cal-add-btn" onClick={() => setShowAddModal(true)}>+ 할 일 추가</button>
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
                                        className="cal-chip"
                                        style={{ backgroundColor: `${categoryColors[e.category]}33`, color: categoryColors[e.category] }}
                                        title={`${e.time ?? "종일"} ${e.title}`}
                                    >
                                        {e.title}
                                    </div>
                                ))}
                                {list.length > MAX_CHIPS && <span className="cal-more">+{list.length - MAX_CHIPS}개</span>}
                            </div>
                        );
                    })}
                </div>

                <div className="cal-footer">
                    <div className="box cal-legend">
                        {Object.entries(categoryColors).map(([name, color]) => (
                            <span key={name} className="cal-legend-item">
                                <i style={{ backgroundColor: color }} />
                                {name}
                            </span>
                        ))}
                    </div>

                    <div className="box cal-settings">
                        <div className="cal-setting-row">
                            <span>캘린더 연동</span>
                            <Toggle checked={syncOn} onChange={handleSyncToggle} />
                        </div>
                        <div className="cal-setting-row">
                            <span>알림 받기</span>
                            <Toggle checked={notifyOn} onChange={() => setNotifyOn((v) => !v)} />
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
