import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { categoryColors } from "../constants/categoryColors";
import frodo from "../assets/frodo2.png";
import { FiChevronRight, FiBell, FiBook } from "react-icons/fi";
import Sidebar from "../components/Sidebar";
import "./HomePage.css";

const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일'];
const MAX_DOTS = 3;
const toDateKey = (date) =>
    `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const toMinutes = (time) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
const DDAY_LIMIT = 2;
// 서버는 카테고리를 영어로 준다 → 화면에는 한글로
const CATEGORY_LABELS = { LECTURE: '수업', ASSIGNMENT: '과제', PERSONAL: '개인일정' };
const TIMELINE_SOURCES = ['TIMETABLE', 'SCHEDULE', 'ECAMPUS'];

function Home() {
    const [home, setHome] = useState(null);
    const [error, setError] = useState('');

    // 홈 화면 데이터는 /api/home 한 번으로 다 받는다
    useEffect(() => {
        api('/api/home').then((res) => {
            if (res.success) {
                setHome(res.data);
            } else {
                setError(res.error.message);
            }
        });
    }, []);

    if (error) return <div className="layout"><Sidebar /><div className="page">{error}</div></div>;
    if (!home) return <div className="layout"><Sidebar /><div className="page">불러오는 중...</div></div>;

    // 주간 일정

    const today = new Date();
    const todayKey = toDateKey(today);
    const weekDays = home.week.map((d) => ({
        key: d.date,
        label: WEEKDAYS[d.dayOfWeek - 1],
        date: Number(d.date.slice(8, 10)),
        isToday: d.today,
        isUpcoming: d.date > todayKey,
        categories: d.categories.map((cat) => CATEGORY_LABELS[cat]),
    }));

    // 오늘 일정 (과제는 빼고, 서버가 이미 시간순으로 정렬해서 준다)

    const todayItems = home.timeline
        .filter((t) => TIMELINE_SOURCES.includes(t.source) && t.category !== 'ASSIGNMENT')
        .map((t) => ({
            key: `${t.source}-${t.id ?? t.title}`,
            time: t.startTime,
            endTime: t.endTime ?? t.startTime,
            title: t.title,
            location: t.location,
            category: CATEGORY_LABELS[t.category],
        }));
    const nowMinutes = today.getHours() * 60 + today.getMinutes();
    const activeKey = todayItems.find((item) => item.endTime && toMinutes(item.endTime) >= nowMinutes)?.key;

    // 졸업 진행률 (학과·입학년도가 없으면 graduation 이 null)

    const graduation = home.graduation;
    const totalEarned = graduation ? graduation.earned : 0;
    const totalRequired = graduation ? graduation.required : 0;
    const remaining = graduation ? graduation.remaining : 0;
    const percent = graduation ? graduation.percent : 0;

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

    // D-Day 알림 (서버가 지난 일정은 빼고 가까운 순으로 정렬해서 준다)
    const ddayItems = home.deadlines.slice(0, DDAY_LIMIT).map((e) => ({
        key: `${e.source}-${e.id ?? e.title}-${e.date}`,
        title: e.title,
        days: e.dday,
        time: e.time,
        subject: e.courseName,
        category: CATEGORY_LABELS[e.category],
    }));
    const ddayLabel = (e) => (e.days === 0 ? (e.time ? `오늘 ${e.time}` : 'D-Day') : `D-${e.days}`);

    return (
        <div className="layout">
            <Sidebar />
            <div className="page">
                <h2 className="page-title home-heading">안녕하세요 {home.greetingName}님!</h2>
                <p className="home-subtitle">오늘도 오르도와 함께해요.</p>
                <div className="homepage-grid">
                    <Link to="/calendar" className="box week-box">
                        <h5 className="week-title">주간 일정</h5>
                        <div className="week-grid">
                            {weekDays.map((d) => (
                                <span key={`label-${d.key}`} className="week-label">{d.label}</span>
                            ))}
                            {weekDays.map((d) => (
                                <div key={d.key} className="week-day">
                                    <span className={`week-date ${d.isToday ? 'is-today' : ''} ${d.isUpcoming && d.categories.length ? 'is-upcoming' : ''}`}>
                                        {d.date}
                                    </span>
                                    <div className="week-dots">
                                        {d.categories.slice(0, MAX_DOTS).map((category) => (
                                            <span
                                                key={category}
                                                className="week-dot"
                                                style={d.isToday ? { backgroundColor: categoryColors[category] } : undefined}
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
                                    {!graduation
                                        ? '마이페이지에서 학과와 입학년도를 입력해 주세요.'
                                        : remaining > 0 ? `졸업 학점까지 ${remaining}학점 남았어요!` : '졸업 학점을 모두 채웠어요!'}
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
                                    <li key={e.key} className="dday-card">
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
