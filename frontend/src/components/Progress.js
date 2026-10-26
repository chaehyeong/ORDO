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

// 세부 막대(breakdown)와 이수 기간(period)은 Degree.js에서 children으로 넘기던 것을 data 값으로 그리도록 변경
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
            <div className="program-detail">
                {/* [수정] data에 breakdown이 있으면 세부 막대 (다전공) */}
                {data.breakdown && data.breakdown.map((b) => (
                    <div key={b.key} className="program-breakdown">
                        <span>{b.label}</span>
                        <ProgressBar earned={b.earned} required={b.required} small />
                    </div>
                ))}
                {/* [수정] data에 period가 있으면 이수 기간 (마이크로디그리) */}
                {data.period && (
                    <>
                        <p className="program-period-label">이수 기간</p>
                        <p className="program-period">{data.period}</p>
                    </>
                )}
            </div>
            <button type="button" className="btn-ordo program-view" onClick={onViewCourses}>
                이수 과목 보기 <FiLogOut size={20} />
            </button>
        </div>
    );
}

export default Progress;
