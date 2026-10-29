import { useState } from "react";
import "./AddCourseModal.css";

const DAYS = ["월", "화", "수", "목", "금"];

// 15분 단위, 07:00 ~ 22:00. value는 mock과 같은 "HH:MM", label은 "오전 09:00"
const TIME_OPTIONS = Array.from({ length: (22 - 7) * 4 + 1 }, (_, i) => {
    const total = 7 * 60 + i * 15;
    const h = Math.floor(total / 60);
    const m = total % 60;
    const h12 = h % 12 === 0 ? 12 : h % 12;
    return {
        value: `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`,
        label: `${h < 12 ? "오전" : "오후"} ${String(h12).padStart(2, "0")}:${String(m).padStart(2, "0")}`,
    };
});

// courses: 현재 학기의 (온라인이 아닌) 수업들. 시간 겹침 검사에 사용
function AddCourseModal({ courses, onClose, onSubmit }) {
    const [name, setName] = useState("");
    const [location, setLocation] = useState("");
    const [start, setStart] = useState("09:00");
    const [end, setEnd] = useState("10:15");
    const [day, setDay] = useState("");
    const [online, setOnline] = useState(false);

    const invalidTime = !online && end <= start;
    const overlapped =
        !online &&
        !invalidTime &&
        day &&
        courses.find((c) => c.day === day && start < c.endTime && end > c.startTime);

    const canSubmit = name.trim() && (online || (day && !invalidTime && !overlapped));

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!canSubmit) return;
        if (online) {
            onSubmit({ courseName: name.trim(), online: true });
        } else {
            onSubmit({
                courseName: name.trim(),
                day,
                startTime: start,
                endTime: end,
                location: location.trim() || null,
            });
        }
    };

    return (
        <div className="course-backdrop" onClick={onClose}>
            <form className="course-modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
                <h2 className="course-title">수업 추가</h2>

                <label className="course-label">수업 이름</label>
                <input
                    className="course-input"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="수업을 입력하세요."
                    autoFocus
                />

                <label className="course-label">강의실</label>
                <input
                    className="course-input"
                    value={location}
                    onChange={(e) => setLocation(e.target.value)}
                    placeholder="강의실을 입력하세요."
                    disabled={online}
                />

                <div className="course-row">
                    <div>
                        <label className="course-label">시작 시간</label>
                        <select
                            className="course-input"
                            value={start}
                            onChange={(e) => setStart(e.target.value)}
                            disabled={online}
                        >
                            {TIME_OPTIONS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                        </select>
                    </div>
                    <div>
                        <label className="course-label">종료 시간</label>
                        <select
                            className="course-input"
                            value={end}
                            onChange={(e) => setEnd(e.target.value)}
                            disabled={online}
                        >
                            {TIME_OPTIONS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                        </select>
                    </div>
                </div>

                <div className="course-row">
                    <div>
                        <label className="course-label">요일</label>
                        <select
                            className="course-input"
                            value={day}
                            onChange={(e) => setDay(e.target.value)}
                            disabled={online}
                        >
                            <option value="" disabled>선택</option>
                            {DAYS.map((d) => <option key={d} value={d}>{d}</option>)}
                        </select>
                    </div>
                </div>

                {invalidTime && <p className="course-error">종료 시간은 시작 시간 이후여야 해요.</p>}
                {overlapped && (
                    <p className="course-error">
                        {overlapped.courseName} 수업({overlapped.startTime}~{overlapped.endTime})과 시간이 겹쳐요.
                    </p>
                )}

                <label className="course-online">
                    <span>온라인 강의</span>
                    <input type="checkbox" checked={online} onChange={(e) => setOnline(e.target.checked)} />
                </label>

                <div className="course-actions">
                    <button type="button" className="course-cancel" onClick={onClose}>취소</button>
                    <button type="submit" className="course-confirm" disabled={!canSubmit}>확인</button>
                </div>
            </form>
        </div>
    );
}

export default AddCourseModal;
