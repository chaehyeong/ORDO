import "./SemesterCurve.css";

// 곡선 위 점 순서: 입학 → 1-1 … 4-2 → 졸업
const STOPS = ['입학', '1-1', '1-2', '2-1', '2-2', '3-1', '3-2', '4-1', '4-2', '졸업'];
const YEARS = [1, 2, 3, 4];

// 점 위치 (곡선 영역 기준 %). x는 10% 간격, y는 오르내리며 올라가는 물결 모양
const POINT_X = STOPS.map((_, i) => 5 + i * 10);
const POINT_Y = [90, 62, 72, 46, 56, 30, 40, 18, 28, 4];

// 두 점 사이를 부드러운 곡선(Catmull-Rom → 베지어)으로 잇는 path
function segmentPath(i) {
    const p = (k) => {
        const idx = Math.min(Math.max(k, 0), STOPS.length - 1);
        return { x: POINT_X[idx], y: POINT_Y[idx] };
    };
    const p0 = p(i - 1), p1 = p(i), p2 = p(i + 1), p3 = p(i + 2);
    const c1 = { x: p1.x + (p2.x - p0.x) / 6, y: p1.y + (p2.y - p0.y) / 6 };
    const c2 = { x: p2.x - (p3.x - p1.x) / 6, y: p2.y - (p3.y - p1.y) / 6 };
    return `M ${p1.x} ${p1.y} C ${c1.x} ${c1.y}, ${c2.x} ${c2.y}, ${p2.x} ${p2.y}`;
}

// 학기별 곡선. gpa = GET /api/academic/gpa 응답 data
function SemesterCurve({ gpa }) {
    // 현재 학기 위치. 4-2를 넘긴 경우(목록에 없음)는 졸업 지점에 둔다
    const found = STOPS.indexOf(gpa.currentSemesterLabel);
    const currentIdx = found === -1 ? STOPS.length - 1 : found;

    // semesterLabel이 있는 정규 학기만 사용 (계절학기 제외)
    const termByLabel = {};
    gpa.terms.forEach((t) => {
        if (t.semesterLabel && !termByLabel[t.semesterLabel]) termByLabel[t.semesterLabel] = t;
    });

    const segments = STOPS.slice(0, -1).map((_, i) => ({ i, d: segmentPath(i), done: i < currentIdx }));

    return (
        <div className="roadmap-chart">
            {/* 학년 구간 띠 */}
            {YEARS.map((year) => (
                <div
                    key={year}
                    className={`year-band ${year % 2 === 1 ? 'shaded' : ''}`}
                    style={{ left: `${POINT_X[year * 2 - 1] - 5}%`, width: '20%' }}
                >
                    <span className="year-label">{year}학년</span>
                </div>
            ))}

            <div className="curve-area">
                <svg className="curve-svg" viewBox="0 0 100 100" preserveAspectRatio="none">
                    {segments.map((s) => (
                        <path key={`band-${s.i}`} d={s.d} className={s.done ? 'curve-band done' : 'curve-band'} vectorEffect="non-scaling-stroke" />
                    ))}
                    {segments.filter((s) => s.done).map((s) => (
                        <path key={`line-${s.i}`} d={s.d} className="curve-line" vectorEffect="non-scaling-stroke" />
                    ))}
                </svg>

                {STOPS.map((label, i) => {
                    const status = i < currentIdx ? 'done' : i === currentIdx ? 'current' : 'future';
                    const term = termByLabel[label];
                    const showScore = status === 'done' && term;
                    const isGraduation = label === '졸업';

                    return (
                        <div
                            key={label}
                            className={`curve-point ${status} ${isGraduation ? 'graduation' : ''}`}
                            style={{ left: `${POINT_X[i]}%`, top: `${POINT_Y[i]}%` }}
                        >
                            {status === 'current' && <span className="here-pill">지금 여기</span>}
                            {isGraduation && status !== 'current' && (
                                <svg className="grad-flag" viewBox="0 0 24 28">
                                    <line x1="3" y1="2" x2="3" y2="27" />
                                    <path d="M4 3 L22 9 L4 15 Z" />
                                </svg>
                            )}
                            <span className="point-dot" />
                            <div className="point-label">
                                <strong>{label}</strong>
                                {showScore && (
                                    <>
                                        <span>{term.gpa !== null ? term.gpa.toFixed(2) : '-'}</span>
                                        <span>{term.earnedCredits}학점</span>
                                    </>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

export default SemesterCurve;
