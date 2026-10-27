import { useState } from "react";
import { Link } from "react-router-dom";
import { mockUser } from "../mock/userMock";
import { mockCalendarEvents, categoryColors } from "../mock/calendarMock";
import { mockTimetable } from "../mock/timetableMock";
import { mockDegree } from "../mock/degreeMock";
import frodo from "../assets/frodo2.png";
import { FiChevronRight, FiBell, FiBook } from "react-icons/fi";
import Sidebar from "../components/Sidebar";
import "./HomePage.css";

const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일'];
const MAX_DOTS = 3;
const toDateKey = (date) =>
    `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const toMinutes = (time) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
const DDAY_CATEGORIES = ['과제', '개인일정'];
const DDAY_LIMIT = 2;

function Home() {
    const [user] = useState(mockUser);
    const [degree] = useState(mockDegree);
    const [events] = useState(mockCalendarEvents);
    const [timetable] = useState(mockTimetable);

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

    // 오늘 일정

    const todayLabel = WEEKDAYS[(today.getDay() + 6) % 7];
    const todayItems = [
        ...timetable
            .filter((t) => t.day === todayLabel)
            .map((t) => ({ key: `class-${t.id}`, time: t.startTime, endTime: t.endTime, title: t.courseName, location: t.location, category: '수업' })),
        ...events
            .filter((e) => e.date === todayKey && e.category !== '과제')
            .map((e) => ({ key: `event-${e.id}`, time: e.time, endTime: e.time, title: e.title, location: e.location, category: e.category })),
    ].sort((a, b) => {
        if (!a.time) return -1;
        if (!b.time) return 1;
        return toMinutes(a.time) - toMinutes(b.time);
    });
    const nowMinutes = today.getHours() * 60 + today.getMinutes();
    const activeKey = todayItems.find((item) => item.endTime && toMinutes(item.endTime) >= nowMinutes)?.key;

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

    // D-Day 알림
    const todayMidnight = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    const ddayItems = events
        .filter((e) => DDAY_CATEGORIES.includes(e.category))
        .map((e) => {
            const [y, m, d] = e.date.split('-').map(Number);
            return { ...e, days: Math.round((new Date(y, m - 1, d) - todayMidnight) / 86400000) };
        })
        .filter((e) => e.days > 0 || (e.days === 0 && (!e.time || toMinutes(e.time) >= nowMinutes)))
        .sort((a, b) => a.days - b.days || toMinutes(a.time ?? '00:00') - toMinutes(b.time ?? '00:00'))
        .slice(0, DDAY_LIMIT);
    const ddayLabel = (e) => (e.days === 0 ? (e.time ? `오늘 ${e.time}` : 'D-Day') : `D-${e.days}`);

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

                    <section className="box today-box">
                        <div className="today-header">
                            <h5 className="today-title">오늘 일정</h5>
                            <Link to="/calendar" className="today-btn">일정 수정하기 <FiChevronRight /></Link>
                        </div>
                        {todayItems.length === 0 ? (
                            <p className="today-empty">오늘은 일정이 없어요.</p>
                        ) : (
                            <ul className="today-list">
                                {todayItems.map((item, i) => (
                                    <li key={item.key} className="today-row">
                                        <div className="today-time-col">
                                            <span className={`today-time ${item.key === activeKey ? 'is-active' : ''}`}>
                                                {item.time ?? '종일'}
                                            </span>
                                            {i < todayItems.length - 1 && <span className="today-line" />}
                                        </div>
                                        <div className="today-card">
                                            <div>
                                                <p className="today-name">{item.title}</p>
                                                {item.location && <p className="today-location">{item.location}</p>}
                                            </div>
                                            <span className="today-category">
                                                <span className="today-category-dot" style={{ backgroundColor: categoryColors[item.category] }} />
                                                {item.category}
                                            </span>
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </section>

                    <section className="box dday-box">
                        <h5 className="dday-title"><FiBell className="dday-bell" /> D-Day 알림</h5>
                        {ddayItems.length === 0 ? (
                            <p className="dday-empty">다가오는 일정이 없어요.</p>
                        ) : (
                            <ul className="dday-list">
                                {ddayItems.map((e) => (
                                    <li key={e.id} className="dday-card">
                                        <span className="dday-icon"><FiBook size={22} /></span>
                                        <div>
                                            <p className="dday-name">
                                                {e.title}
                                                <span className="dday-count">{ddayLabel(e)}</span>
                                            </p>
                                            <p className="dday-meta">{e.subject ? `${e.subject} | ${e.category}` : e.category}</p>
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </section>
                </div>
                <img className="home-frodo" src={frodo} alt="frodo" />
            </div>
        </div>
    );
}

export default Home;
