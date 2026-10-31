import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
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

// 마이크로디그리 미충족 요건 코드 → 화면 문구 (백엔드 가이드 문구)
const UNMET_LABELS = {
    REQUIRED_COURSE: '필수 과목을 아직 안 들었어요',
    COURSE_COUNT: '과목이 더 필요해요',
    GROUP_MINIMUM: '선택 묶음의 과목이 더 필요해요',
    DEPARTMENT_COUNT: '다른 참여학과 과목을 1개 이상 들어야 해요',
};

const noSpace = (s) => (s ?? '').replace(/\s/g, '');

// 미충족 요건마다 { text, courses(들어야 할 과목 이름) } 를 만든다
// md: 내 마이크로디그리(GET /api/academic/micro-degrees), catalog: 같은 id 의 카탈로그(GET /api/catalog/micro-degrees)
// 서버 판정과 같은 기준: 인정된(counted) 과목을 학수번호 또는 띄어쓰기 뺀 과목명으로 맞춘다
const describeUnmet = (md, catalog) => {
    const rows = catalog?.courses ?? [];
    const counted = md.courses.filter((c) => c.counted);
    const isTaken = (row) => counted.some((c) =>
        (row.courseCode && c.courseCode === row.courseCode) || noSpace(c.courseName) === noSpace(row.courseName));
    const taken = rows.filter(isTaken);
    const left = rows.filter((row) => !isTaken(row));
    const names = (list) => list.map((row) => row.courseName);

    return md.unmet.flatMap((code) => {
        if (!catalog) return [{ text: UNMET_LABELS[code] ?? code, courses: [] }];
        switch (code) {
            case 'REQUIRED_COURSE':
                return [{ text: UNMET_LABELS[code], courses: names(left.filter((row) => row.mandatory)) }];
            case 'COURSE_COUNT':
                return [{ text: `${md.requiredCourses - md.earnedCourses}과목이 더 필요해요`, courses: names(left) }];
            case 'GROUP_MINIMUM': {
                // 최소 과목 수를 못 채운 묶음마다 한 줄
                const groupKeys = [...new Set(rows.filter((row) => row.groupMin != null).map((row) => row.groupKey))];
                return groupKeys.flatMap((key) => {
                    const min = rows.find((row) => row.groupKey === key).groupMin;
                    const need = min - taken.filter((row) => row.groupKey === key).length;
                    if (need <= 0) return [];
                    return [{
                        text: `선택 묶음 ${key}에서 ${need}과목이 더 필요해요`,
                        courses: names(left.filter((row) => row.groupKey === key)),
                    }];
                });
            }
            case 'DEPARTMENT_COUNT': {
                // 아직 과목을 안 들은 참여학과의 과목
                const departments = new Set(taken.map((row) => row.departmentName));
                return [{
                    text: `참여학과 ${catalog.minDepartments}곳 이상의 과목을 들어야 해요 (지금 ${departments.size}곳)`,
                    courses: names(left.filter((row) => !departments.has(row.departmentName))),
                }];
            }
            default:
                return [{ text: UNMET_LABELS[code] ?? code, courses: [] }];
        }
    });
};

// GET /api/academic/micro-degrees 배열의 한 개 → Progress 카드 모양
const fromMicroDegree = (md, catalog) => ({
    id: md.id,
    name: md.name,
    type: '마이크로디그리',
    earned: md.earned,
    required: md.required,
    unmet: describeUnmet(md, catalog),
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
    const navigate = useNavigate();
    const goRoadMap = () => navigate('/roadmap'); // 학점 취득 현황 박스, 이수 과목 보기 → 졸업 로드맵
    const [progress, setProgress] = useState(null);
    const [gpa, setGpa] = useState(null);
    const [doubleMajor, setDoubleMajor] = useState(null);
    const [minor, setMinor] = useState(null);
    const [microDegrees, setMicroDegrees] = useState([]);
    const [microCatalog, setMicroCatalog] = useState([]); // 마이크로디그리별 들어야 할 과목 (미충족 요건 표시용)
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
            api('/api/catalog/micro-degrees'),
        ]).then(([progressRes, gpaRes, doubleRes, minorRes, microRes, catalogRes]) => {
            // 입학년도·전공이 없으면 progress 가 PROFILE_INCOMPLETE 로 실패한다
            const failed = [progressRes, gpaRes, doubleRes, minorRes, microRes].find((r) => !r.success);
            setError(failed ? failed.error.message : '');
            setProgress(progressRes.success ? progressRes.data : null);
            if (gpaRes.success) setGpa(gpaRes.data);
            setDoubleMajor(doubleRes.success ? doubleRes.data : null);
            setMinor(minorRes.success ? minorRes.data : null);
            setMicroDegrees(microRes.success ? microRes.data : []);
            // 카탈로그는 실패해도 오류로 띄우지 않는다 (미충족 요건 문구만 나온다)
            setMicroCatalog(catalogRes.success ? catalogRes.data : []);
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

    // 다전공/부전공 취소: 지금 카드에 보이는 쪽(다전공 우선)을 해제한다
    const handleRemoveMajor = async () => {
        const type = doubleMajor ? '다전공' : '부전공';
        const name = (doubleMajor ?? minor).major.displayName;
        if (!window.confirm(`${type} '${name}'을(를) 취소할까요?`)) return;
        const path = doubleMajor ? '/api/academic/double-major' : '/api/academic/minor';
        const res = await api(path, { method: 'DELETE' });
        if (res.success) {
            setReloadKey((k) => k + 1); // 주전공 요건·이수현황도 바뀌므로 전부 다시 불러온다
        } else {
            alert(res.error.message);
        }
    };

    // 마이크로디그리 취소: 등록된 것을 모두 해제한 뒤 목록을 다시 불러온다
    const handleRemoveMicro = async () => {
        const names = microDegrees.map((md) => md.name).join(', ');
        if (!window.confirm(`마이크로디그리 '${names}'을(를) 취소할까요?`)) return;
        for (const md of microDegrees) {
            const res = await api(`/api/academic/micro-degrees/${md.id}`, { method: 'DELETE' });
            if (!res.success) {
                alert(res.error.message);
                break;
            }
        }
        setReloadKey((k) => k + 1);
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
                    <section className="box status-box credit-box" onClick={goRoadMap}>
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
                            {multiMajor ? (
                                <button type="button" className="btn-outline-ordo" onClick={handleRemoveMajor}>{multiMajor.type} 취소하기</button>
                            ) : (
                                <button type="button" className="btn-outline-ordo" onClick={() => setShowMajorModal(true)}>+ 다전공/부전공 추가하기</button>
                            )}
                        </div>
                        <div className="program-panel">
                            {multiMajor ? (
                                <Progress icon={<FiBookOpen size={28} />} data={multiMajor} onViewCourses={goRoadMap} />
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
                            {microDegrees.length > 0 ? (
                                <button type="button" className="btn-outline-ordo" onClick={handleRemoveMicro}>마이크로디그리 취소하기</button>
                            ) : (
                                <button type="button" className="btn-outline-ordo" onClick={() => setShowMicroModal(true)}>+ 마이크로디그리 추가하기</button>
                            )}
                        </div>
                        <div className="program-panel">
                            {microDegrees.length > 0 ? (
                                // 최대 3개까지 등록할 수 있어 하나씩 카드로 그린다
                                microDegrees.map((md) => (
                                    <Progress key={md.id} icon={<FiBook size={28} />} data={fromMicroDegree(md, microCatalog.find((c) => c.id === md.id))} onViewCourses={goRoadMap} />
                                ))
                            ) : (
                                <EmptyState
                                    icon={<FiBook size={28} />}
                                    title="이수 중인 마이크로디그리가 없습니다."
                                    description={<>관심 분야의 마이크로디그리를<br />확인해보세요.</>}
                                    actionLabel="마이크로디그리 알아보기"
                                    onAction={() => window.open('https://sdl.khu.ac.kr/bbs/board.php?bo_table=faq_global', '_blank', 'noopener,noreferrer')}
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
