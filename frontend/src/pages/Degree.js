import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import Progress from "../components/Progress";
import EmptyState from "../components/EmptyState";
import AddMajorModal from "../components/AddMajorModal";
import AddMicroDegreeModal from "../components/AddMicroDegreeModal";
import { api } from "../api/client";
import { FiBook, FiBookOpen } from "react-icons/fi";
import './Degree.css';

// 학점 취득 현황 영역 (progress.areas 의 key → 화면 이름·색). 이 순서로 그린다
const AREA_STYLES = [
    { key: 'MAJOR_REQUIRED',   label: '전공필수', color: '#079382' },
    { key: 'MAJOR_BASIC',      label: '전공기초', color: '#42B59E' },
    { key: 'MAJOR_ELECTIVE',   label: '전공선택', color: '#76A2F5' },
    { key: 'GEN_REQUIRED',     label: '필수교양', color: '#5D6CB2' },
    { key: 'GEN_DISTRIBUTION', label: '배분이수', color: '#C8DC5D' },
    { key: 'GEN_FREE',         label: '자유이수', color: '#F5D92A' },
    { key: 'OTHER',            label: '기타',     color: '#B0B0B0' }, // 요구 학점 없음. 들은 게 있을 때만 보인다
];
const GPA_MAX = 4.3;

// GET /api/academic/progress → 도넛·범례가 쓰던 { totalRequired, categories } 모양
const toCredits = (progress) => ({
    totalRequired: progress.total.required,
    categories: AREA_STYLES
        .map((style) => {
            const area = progress.areas.find((a) => a.key === style.key);
            return area && { ...style, earned: area.earned, required: area.required };
        })
        .filter((c) => c && (c.key !== 'OTHER' || c.earned > 0)),
});

// GET /api/academic/double-major → Progress 카드 모양
const fromDoubleMajor = (d) => ({
    name: d.major.displayName,
    type: '다전공',
    earned: d.total.earned,
    required: d.total.required,
    breakdown: d.groups.map((g) => ({ key: g.key, label: g.label, earned: g.earned, required: g.required })),
});

// GET /api/academic/minor → Progress 카드 모양. 후보 학점은 공식 인정 학점이 아니라서 안내 문구를 꼭 붙인다
const fromMinor = (m) => ({
    name: m.major.displayName,
    type: '부전공',
    earned: m.candidateCredits,
    required: m.requirements.totalCredits,
    note: '학과별 부전공 지정 과목 확인 필요',
});

// GET /api/academic/micro-degrees 배열의 한 개 → Progress 카드 모양
const fromMicroDegree = (md) => ({
    id: md.id,
    name: md.name,
    type: '마이크로디그리',
    earned: md.earned,
    required: md.required,
    period: md.period,
});

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
                    <span className="legend-value">{c.earned}/{c.required ?? '-'}</span>
                </li>
            ))}
        </ul>
    );
}

// average 가 null 이면(성적 있는 과목 없음) '-' 로 표시
function GpaBar({ average, max }) {
    const percent = average == null ? 0 : Math.min((average / max) * 100, 100);

    return (
        <div className="gpa">
            <div className="gpa-bar">
                <div className="gpa-bar-fill" style={{ width: `${percent}%` }} />
            </div>
            <p className="gpa-text">
                <span className="gpa-value">{average == null ? '-' : average.toFixed(2)}</span>
                <span className="gpa-max"> / {max}</span>
            </p>
        </div>
    );
}

function Degree() {
    const [progress, setProgress] = useState(null);
    const [gpa, setGpa] = useState(null);
    const [doubleMajor, setDoubleMajor] = useState(null);
    const [minor, setMinor] = useState(null);
    const [microDegrees, setMicroDegrees] = useState([]);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const [reloadKey, setReloadKey] = useState(0); // 1 올리면 전부 다시 불러온다

    // 추가하기 모달 열림 상태
    const [showMajorModal, setShowMajorModal] = useState(false);
    const [showMicroModal, setShowMicroModal] = useState(false);

    // 박스 4개에 필요한 API 5개를 한 번에 부른다
    useEffect(() => {
        Promise.all([
            api('/api/academic/progress'),
            api('/api/academic/gpa'),
            api('/api/academic/double-major'),
            api('/api/academic/minor'),
            api('/api/academic/micro-degrees'),
        ]).then(([progressRes, gpaRes, doubleRes, minorRes, microRes]) => {
            // 입학년도·전공이 없으면 progress 가 PROFILE_INCOMPLETE 로 실패한다
            const failed = [progressRes, gpaRes, doubleRes, minorRes, microRes].find((r) => !r.success);
            setError(failed ? failed.error.message : '');
            setProgress(progressRes.success ? progressRes.data : null);
            if (gpaRes.success) setGpa(gpaRes.data);
            setDoubleMajor(doubleRes.success ? doubleRes.data : null);
            setMinor(minorRes.success ? minorRes.data : null);
            setMicroDegrees(microRes.success ? microRes.data : []);
            setLoading(false);
        });
    }, [reloadKey]);

    // 다전공/부전공 추가: { majorId, type: "다전공" | "부전공" }
    // 등록하면 주전공 요건·이수현황도 바뀌므로 전부 다시 불러온다
    const handleAddMajor = async ({ majorId, type }) => {
        const path = type === '다전공' ? '/api/academic/double-major' : '/api/academic/minor';
        const res = await api(path, { method: 'PUT', body: JSON.stringify({ majorId }) });
        if (res.success) {
            setShowMajorModal(false);
            setReloadKey((k) => k + 1);
        } else {
            alert(res.error.message);
        }
    };

    // 마이크로디그리 추가: { microDegreeId }. 응답이 내 목록 전체라 그대로 넣는다
    const handleAddMicro = async ({ microDegreeId }) => {
        const res = await api('/api/academic/micro-degrees', {
            method: 'POST',
            body: JSON.stringify({ microDegreeId }),
        });
        if (res.success) {
            setMicroDegrees(res.data);
            setShowMicroModal(false);
        } else {
            alert(res.error.message);
        }
    };

    if (loading) {
        return (
            <div className="layout">
                <Sidebar />
                <div className='page'>불러오는 중...</div>
            </div>
        );
    }

    const credits = progress ? toCredits(progress) : { totalRequired: 0, categories: [] };
    // 다전공이 있으면 다전공, 없으면 부전공을 보여준다
    const multiMajor = doubleMajor ? fromDoubleMajor(doubleMajor) : minor ? fromMinor(minor) : null;
    // 이미 다전공·부전공·주전공인 학과는 추가 목록에서 뺀다
    const excludeMajorIds = [progress?.major?.id, doubleMajor?.major.id, minor?.major.id].filter(Boolean);

    return (
        <div className="layout">
            <Sidebar />
            <div className='page'>
                <h2 className="page-title">학사관리</h2>
                {error && <p className="text-danger">{error}</p>}

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
                        <GpaBar average={gpa ? gpa.gpa : null} max={GPA_MAX} />
                    </section>
                    <section className="box program-box">
                        <div className="program-header">
                            <h5>다전공/부전공</h5>
                            <button type="button" className="btn-outline-ordo" onClick={() => setShowMajorModal(true)}>+ 다전공/부전공 추가하기</button>
                        </div>
                        <div className="program-panel">
                            {multiMajor ? (
                                <Progress icon={<FiBookOpen size={28} />} data={multiMajor} />
                            ) : (
                                <EmptyState
                                    icon={<FiBookOpen size={28} />}
                                    title="추가한 다전공/부전공이 없습니다."
                                    description={<>관심 있는 전공을 추가하여<br />이수 현황을 관리해보세요.</>}
                                    actionLabel="다전공/부전공 알아보기"
                                    onAction={() => window.open('https://www.khu.ac.kr/kor/user/contents/view.do?menuNo=200105', '_blank', 'noopener,noreferrer')}
                                />
                            )}
                        </div>
                    </section>
                    <section className="box program-box">
                        <div className="program-header">
                            <h5>마이크로디그리</h5>
                            <button type="button" className="btn-outline-ordo" onClick={() => setShowMicroModal(true)}>+ 마이크로디그리 추가하기</button>
                        </div>
                        <div className="program-panel">
                            {microDegrees.length > 0 ? (
                                // 최대 3개까지 등록할 수 있어 하나씩 카드로 그린다
                                microDegrees.map((md) => (
                                    <Progress key={md.id} icon={<FiBook size={28} />} data={fromMicroDegree(md)} />
                                ))
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

            {showMajorModal && (
                <AddMajorModal
                    admissionYear={progress?.admissionYear}
                    excludeIds={excludeMajorIds}
                    onClose={() => setShowMajorModal(false)}
                    onSubmit={handleAddMajor}
                />
            )}
            {showMicroModal && (
                <AddMicroDegreeModal
                    excludeIds={microDegrees.map((md) => md.id)}
                    onClose={() => setShowMicroModal(false)}
                    onSubmit={handleAddMicro}
                />
            )}
        </div>
    );
}

export default Degree;
