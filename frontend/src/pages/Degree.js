import Sidebar from "../components/Sidebar";
import Progress from "../components/Progress";
import EmptyState from "../components/EmptyState";
import { mockDegree } from "../mock/degreeMock";
import { FiBook, FiBookOpen } from "react-icons/fi";
import './Degree.css';

// 원형 차트 (SVG stroke-dasharray로 카테고리별 구간 그리기)
function CreditDonut({ categories, totalRequired }) {
    const size = 150;
    const stroke = 10;
    const r = (size - stroke) / 2;
    const c = size / 2;
    const circumference = 2 * Math.PI * r;

    const totalEarned = categories.reduce((sum, c) => sum + c.earned, 0);
    const percent = totalRequired > 0 ? (totalEarned / totalRequired) * 100 : 0;

    const segments = [
        ...categories.filter((c) => c.earned > 0).map((c) => ({ key: c.key, value: c.earned, color: c.color })),
        { key: 'remaining', value: Math.max(totalRequired - totalEarned, 0), color: '#D3D6E0' },
    ].filter((s) => s.value > 0);

    let offset = 0;
    const arcs = segments.map((s) => {
        const len = (s.value / totalRequired) * circumference;
        const arc = { ...s, start: offset, len };
        offset += len;
        return arc;
    });

    const endPoint = (arc) => {
        const angle = (arc.start + arc.len) / r;
        return { x: c + r * Math.cos(angle), y: c + r * Math.sin(angle) };
    };

    return (
        <div className="donut" style={{ width: size, height: size, '--donut-size': `${size}px` }}>
            <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
                <g transform={`rotate(-90 ${size / 2} ${size / 2})`}>
                    {arcs.map((arc) => (
                        <circle
                            key={arc.key}
                            cx={c} cy={c} r={r}
                            fill="none" stroke={arc.color} strokeWidth={stroke}
                            strokeDasharray={`${arc.len} ${circumference - arc.len}`}
                            strokeDashoffset={-arc.start}
                            className="donut-segment"
                        />
                    ))}
                    {arcs.length > 1 && arcs.map((arc) => {
                        const { x, y } = endPoint(arc);
                        return <circle key={`${arc.key}-end`} cx={x} cy={y} r={stroke / 2} fill={arc.color} />;
                    })}
                </g>
            </svg>
            <div className="donut-center">
                <span className="donut-label">총 취득 학점</span>
                <span className="donut-value">{totalEarned}/{totalRequired}</span>
                <span className="donut-percent">{percent.toFixed(1)}%</span>
            </div>
        </div>
    );
}

function CreditLegend({ categories }) {
    return (
        <ul className="credit-legend">
            {categories.map((c) => (
                <li key={c.key}>
                    <span className="legend-dot" style={{ backgroundColor: c.color }} />
                    <span className="legend-label">{c.label}</span>
                    <span className="legend-value">{c.earned}/{c.required}</span>
                </li>
            ))}
        </ul>
    );
}

function GpaBar({ average, max }) {
    const percent = Math.min((average / max) * 100, 100);

    return (
        <div className="gpa">
            <div className="gpa-bar">
                <div className="gpa-bar-fill" style={{ width: `${percent}%` }} />
            </div>
            <p className="gpa-text">
                <span className="gpa-value">{average.toFixed(2)}</span>
                <span className="gpa-max"> / {max}</span>
            </p>
        </div>
    );
}

function Degree() {
    const { credits, gpa, multiMajor, microDegree } = mockDegree;

    return (
        <div className="layout">
            <Sidebar />
            <div className='page'>
                <h2 className="page-title">학사관리</h2>

                <div className="degree-grid">
                    <section className="box status-box">
                        <h4>학점 취득 현황</h4>
                        <div className="credit-content">
                            <CreditDonut
                                categories={credits.categories}
                                totalRequired={credits.totalRequired}
                            />
                            <CreditLegend categories={credits.categories} />
                        </div>
                    </section>
                    <section className="box status-box">
                        <h4>평균 학점 현황</h4>
                        <GpaBar average={gpa.average} max={gpa.max} />
                    </section>
                    <section className="box program-box">
                        <div className="program-header">
                            <h5>다전공</h5>
                            <button type="button" className="btn-outline-ordo">+ 다전공 추가하기</button>
                        </div>
                        <div className="program-panel">
                            {multiMajor ? (
                                <Progress icon={<FiBookOpen size={28} />} data={multiMajor} />
                            ) : (
                                <EmptyState
                                    icon={<FiBookOpen size={28} />}
                                    title="추가한 다전공이 없습니다."
                                    description={<>관심 있는 전공을 추가하여<br />이수 현황을 관리해보세요.</>}
                                    actionLabel="다전공 알아보기"
                                    onAction={() => window.open('https://www.khu.ac.kr/kor/user/contents/view.do?menuNo=200105', '_blank', 'noopener,noreferrer')}
                                />
                            )}
                        </div>
                    </section>
                    <section className="box program-box">
                        <div className="program-header">
                            <h5>마이크로디그리</h5>
                            <button type="button" className="btn-outline-ordo">+ 마이크로디그리 추가하기</button>
                        </div>
                        <div className="program-panel">
                            {microDegree ? (
                                <Progress icon={<FiBook size={28} />} data={microDegree} /> 
                            ) : (
                                <EmptyState
                                    icon={<FiBook size={28} />}
                                    title="이수 중인 마이크로디그리가 없습니다."
                                    description={<>관심 분야의 마이크로디그리를<br />확인해보세요.</>}
                                    actionLabel="마이크로디그리 알아보기"
                                />
                            )}
                        </div>
                    </section>
                </div>
            </div>
        </div>
    );
}

export default Degree;
