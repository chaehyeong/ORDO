import { useRef, useState } from "react";
import { categoryColors } from "../mock/calendarMock";
import "./AddTodoModal.css";

const CATEGORIES = Object.keys(categoryColors); // 수업, 과제, 개인일정
const WEEK = ["일", "월", "화", "수", "목", "금", "토"];

// 30분 단위 시간 옵션. value는 mock과 같은 "HH:MM", label은 "오전 09:00"
const TIME_OPTIONS = Array.from({ length: 48 }, (_, i) => {
    const h = Math.floor(i / 2);
    const m = i % 2 === 0 ? "00" : "30";
    const h12 = h % 12 === 0 ? 12 : h % 12;
    return {
        value: `${String(h).padStart(2, "0")}:${m}`,
        label: `${h < 12 ? "오전" : "오후"} ${String(h12).padStart(2, "0")}:${m}`,
    };
});

const todayStr = () => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};

const formatDate = (str) => {
    if (!str) return "날짜 선택";
    const [y, m, d] = str.split("-");
    return `${y}.${m}.${d} (${WEEK[new Date(Number(y), Number(m) - 1, Number(d)).getDay()]})`;
};

function AddTodoModal({ onClose, onSubmit }) {
    const [title, setTitle] = useState("");
    const [category, setCategory] = useState(CATEGORIES[0]);
    const [date, setDate] = useState(todayStr());
    const [start, setStart] = useState("09:00");
    const [end, setEnd] = useState("10:00");
    const dateRef = useRef(null);

    // 날짜 칸 어디를 눌러도 브라우저 달력이 열리게 함
    const openDatePicker = () => {
        const input = dateRef.current;
        if (!input) return;
        if (input.showPicker) input.showPicker();
        else input.focus();
    };

    const invalidTime = end <= start;
    const canSubmit = title.trim() && date && !invalidTime;

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!canSubmit) return;
        // calendarMock의 일정 형태: { title, category, date, time, ... }
        onSubmit({ title: title.trim(), category, date, time: start, endTime: end });
    };

    return (
        <div className="todo-backdrop" onClick={onClose}>
            <form className="todo-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
                <div className="todo-head">
                    <h2>할 일 추가</h2>
                    <button type="button" className="todo-close" onClick={onClose} aria-label="닫기">×</button>
                </div>

                <label className="todo-label">할 일 제목</label>
                <input
                    className="todo-input"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="할 일을 입력하세요"
                    autoFocus
                />

                <label className="todo-label">카테고리</label>
                <div className="todo-categories">
                    {CATEGORIES.map((c) => {
                        const active = category === c;
                        return (
                            <button
                                key={c}
                                type="button"
                                className={`todo-category ${active ? "is-active" : ""}`}
                                style={active ? { borderColor: categoryColors[c], color: categoryColors[c] } : undefined}
                                onClick={() => setCategory(c)}
                            >
                                {c}
                            </button>
                        );
                    })}
                </div>

                <label className="todo-label">날짜</label>
                <div className="todo-input todo-date" onClick={openDatePicker}>
                    <span>{formatDate(date)}</span>
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                        <rect x="3" y="5" width="18" height="16" rx="4" />
                        <path d="M8 3v4M16 3v4" strokeLinecap="round" />
                    </svg>
                    {/* 화면에는 안 보이는 date input. 클릭은 위 div가 받아서 showPicker()로 연다 */}
                    <input
                        ref={dateRef}
                        type="date"
                        value={date}
                        onChange={(e) => setDate(e.target.value)}
                        tabIndex={-1}
                        aria-label="날짜 선택"
                    />
                </div>

                <div className="todo-times">
                    <div>
                        <label className="todo-label">시작 시간</label>
                        <select className="todo-input" value={start} onChange={(e) => setStart(e.target.value)}>
                            {TIME_OPTIONS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                        </select>
                    </div>
                    <div>
                        <label className="todo-label">종료 시간</label>
                        <select className="todo-input" value={end} onChange={(e) => setEnd(e.target.value)}>
                            {TIME_OPTIONS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                        </select>
                    </div>
                </div>
                {invalidTime && <p className="todo-error">종료 시간은 시작 시간 이후여야 해요.</p>}

                <button type="submit" className="todo-submit" disabled={!canSubmit}>추가하기</button>
            </form>
        </div>
    );
}

export default AddTodoModal;
