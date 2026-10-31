import { FiLogOut } from "react-icons/fi";

// 학점 막대 (전체 막대 / 세부 막대 공용)
function ProgressBar({ earned, required, small }) {
    const percent = required > 0 ? Math.min((earned / required) * 100, 100) : 0;
    return (
        <div className={`program-bar ${small ? 'small' : ''}`}>
            <div className="program-bar-fill" style={{ width: `${percent}%` }} />
        </div>
    );
}

// 세부 막대(breakdown)와 미충족 요건(unmet)은 Degree.js에서 children으로 넘기던 것을 data 값으로 그리도록 변경
function Progress({ icon, data, onViewCourses }) {
    return (
        <div className="program-progress">
            <div className="program-info">
                <div className="program-icon">{icon}</div>
                <div>
                    <p className="program-name">{data.name}</p>
                    <p className="program-type">{data.type}</p>
                </div>
            </div>
            <p className="program-credit">{data.earned} / {data.required}학점</p>
            <ProgressBar earned={data.earned} required={data.required} />
            {/* 세부 막대가 있으면(다전공) 남는 세로 공간의 가운데에 둔다 */}
            <div className={`program-detail ${data.breakdown ? 'centered' : ''}`}>
                {/* [수정] data에 breakdown이 있으면 세부 막대 (다전공) */}
                {data.breakdown && data.breakdown.map((b) => (
                    <div key={b.key} className="program-breakdown">
                        <span>{b.label}</span>
                        <ProgressBar earned={b.earned} required={b.required} small />
                    </div>
                ))}
                {/* data에 unmet이 있으면 미충족 요건 + 들어야 할 과목 (마이크로디그리). 비어 있으면 모두 충족 */}
                {data.unmet && (
                    <>
                        <p className="program-period-label">미충족 요건</p>
                        {data.unmet.length > 0 ? (
                            data.unmet.map((u) => (
                                <div key={u.text} className="program-unmet">
                                    <p className="program-period">{u.text}</p>
                                    {u.courses.length > 0 && (
                                        <p className="program-unmet-courses">{u.courses.join(', ')}</p>
                                    )}
                                </div>
                            ))
                        ) : (
                            <p className="program-period">모든 요건을 충족했어요</p>
                        )}
                    </>
                )}
                {/* data에 note가 있으면 안내 문구 (부전공: 학과별 지정 과목 확인 필요) */}
                {data.note && <p className="program-period-label">{data.note}</p>}
            </div>
            <button type="button" className="btn-ordo program-view" onClick={onViewCourses}>
                이수 과목 보기 <FiLogOut size={20} />
            </button>
        </div>
    );
}

export default Progress;
