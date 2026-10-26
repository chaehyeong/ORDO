import { useState } from "react";
import { Link } from "react-router-dom";
import { mockUser } from "../mock/userMock";
import { mockCalendarEvents, categoryColors } from "../mock/calendarMock";
import { mockDegree } from "../mock/degreeMock";
import Sidebar from "../components/Sidebar";
import "./HomePage.css";

const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일'];
const MAX_DOTS = 3;
const toDateKey = (date) =>
    `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

function Home() {
    const [user] = useState(mockUser);
    const [degree] = useState(mockDegree);
    const [events] = useState(mockCalendarEvents);

    // 주간 일정

    const today = new Date();
    const todayKey = toDateKey(today);
    const monday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - ((today.getDay() + 6) % 7));
    const weekDays = WEEKDAYS.map((label, i) => {
        const date = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + i);
        const key = toDateKey(date);
        return {
            key,
            label,
            date: date.getDate(),
            isToday: key === todayKey,
            isUpcoming: key > todayKey,
            events: events.filter((e) => e.date === key),
        };
    });

    // 졸업 진행률

    const { totalRequired, categories } = degree.credits;
    const totalEarned = categories.reduce((sum, c) => sum + c.earned, 0);
    const remaining = Math.max(totalRequired - totalEarned, 0);
    const percent = totalRequired > 0 ? Math.min((totalEarned / totalRequired) * 100, 100) : 0;

    const size = 110;
    const stroke = 12;
    const r = (size - stroke) / 2;
    const c = size / 2;
    const circumference = 2 * Math.PI * r;
    const arcs = [
        { key: 'earned', value: totalEarned, color: '#39B19D' },
        { key: 'remaining', value: remaining, color: '#ABABAB' },
    ]
        .filter((s) => s.value > 0)
        .reduce((list, s) => {
            const start = list.length ? list[list.length - 1].start + list[list.length - 1].len : 0;
            return [...list, { ...s, start, len: (s.value / totalRequired) * circumference }];
        }, []);
    const endPoint = (arc) => {
        const angle = (arc.start + arc.len) / r;
        return { x: c + r * Math.cos(angle), y: c + r * Math.sin(angle) };
    };

    return (
        <div className="layout">
            <Sidebar />
            <div className="page">
                <h2 className="page-title">안녕하세요 {user.userName}님!</h2>
                <div className="homepage-grid">
                    <Link to="/calendar" className="box week-box">
                        <h5 className="week-title">주간 일정</h5>
                        <div className="week-grid">
                            {weekDays.map((d) => (
                                <span key={`label-${d.key}`} className="week-label">{d.label}</span>
                            ))}
                            {weekDays.map((d) => (
                                <div key={d.key} className="week-day">
                                    <span className={`week-date ${d.isToday ? 'is-today' : ''} ${d.isUpcoming && d.events.length ? 'is-upcoming' : ''}`}>
                                        {d.date}
                                    </span>
                                    <div className="week-dots">
                                        {d.events.slice(0, MAX_DOTS).map((e) => (
                                            <span
                                                key={e.id}
                                                className="week-dot"
                                                style={d.isToday ? { backgroundColor: categoryColors[e.category] } : undefined}
                                            />
                                        ))}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </Link>
                    <Link to="/degree" className="box grad-box">
                        <div className="grad-header">
                            <h5 className="grad-title">졸업 진행률</h5>
                        </div>
                        <div className="grad-content">
                            <div className="grad-donut" style={{ width: size, height: size, '--donut-size': `${size}px` }}>
                                <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
                                    <g transform={`rotate(-90 ${c} ${c})`}>
                                        {arcs.map((arc) => (
                                            <circle
                                                key={arc.key}
                                                cx={c} cy={c} r={r}
                                                fill="none" stroke={arc.color} strokeWidth={stroke}
                                                strokeDasharray={`${arc.len} ${circumference - arc.len}`}
                                                strokeDashoffset={-arc.start}
                                                className="grad-donut-segment"
                                            />
                                        ))}
                                        {arcs.length > 1 && arcs.map((arc) => {
                                            const { x, y } = endPoint(arc);
                                            return <circle key={`${arc.key}-end`} cx={x} cy={y} r={stroke / 2} fill={arc.color} />;
                                        })}
                                    </g>
                                </svg>
                                <span className="grad-percent">{Math.round(percent)}%</span>
                            </div>
                            <div className="grad-info">
                                <p className="grad-credit">
                                    <span>{totalEarned}</span>
                                    <span>/</span>
                                    <span>{totalRequired}</span>
                                </p>
                                <p className="grad-remaining">
                                    {remaining > 0 ? `졸업 학점까지 ${remaining}학점 남았어요!` : '졸업 학점을 모두 채웠어요!'}
                                </p>
                            </div>
                        </div>
                    </Link>
                    <section className="box">
                        <h5>오늘 일정</h5>
                    </section>
                    <section className="box">
                        <h5>D-Day 알림</h5>
                    </section>
                </div>
            </div>
        </div>
    );
}

export default Home;
