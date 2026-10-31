import { useMemo, useState } from "react";
import { CLASSIFICATION_LABELS, FilterSelect } from "./CompletedCourses";
import "./RemainingCourses.css";

// 이수구분별 카드 색 (배경 / 태그 글자). 목록에 없는 값(직접 추가한 수업 등)은 DEFAULT_COLOR
export const CLASSIFICATION_COLORS = {
    GEN_REQUIRED: { bg: '#F9D3D3', text: '#D95B5B' },
    MAJOR_BASIC: { bg: '#D3DEFA', text: '#4F74D1' },
    MAJOR_REQUIRED: { bg: '#E2D3F7', text: '#8752D1' },
    MAJOR_ELECTIVE: { bg: '#FBF0B8', text: '#B89400' },
};
export const DEFAULT_COLOR = { bg: '#D7EFEA', text: '#2F9D8A' };

// 같은 과목인지 판단하는 키: 학수번호, 없으면 공백 뺀 과목명
export const courseKey = (c) => c.courseCode || c.courseName.replace(/\s/g, '');

// 남은 수강 과목 박스
// data = GET /api/academic/remaining-courses 응답 data, placedKeys = 학기별 배치에 놓인 과목 키 (회색 표시)
function RemainingCourses({ data, placedKeys }) {
    const [classification, setClassification] = useState('');

    const classificationOptions = useMemo(
        () => Object.keys(CLASSIFICATION_LABELS)
            .filter((k) => data.courses.some((c) => c.classification === k))
            .map((k) => ({ value: k, label: CLASSIFICATION_LABELS[k] })),
        [data.courses]
    );

    const filtered = data.courses.filter((c) => !classification || c.classification === classification);
    // 왼쪽→오른쪽으로 읽히도록 앞 절반은 윗줄, 나머지는 아랫줄에 놓는다
    const half = Math.ceil(filtered.length / 2);

    // 드래그 시작: 학기별 배치 박스가 받을 과목 정보를 담는다
    const handleDragStart = (e, course) => {
        e.dataTransfer.setData('application/json', JSON.stringify({ source: 'remaining', course }));
        e.dataTransfer.effectAllowed = 'copy';
    };

    return (
        <div className="remaining-courses">
            <div className="remaining-title-wrap">
                <h4 className="remaining-title">남은 수강 과목</h4>
                <span className="remaining-total">총 {data.remainingCredits}학점</span>
            </div>

            <div className="remaining-filters">
                <FilterSelect value={classification} onChange={setClassification} allLabel="전체 이수구분" options={classificationOptions} />
            </div>

            {!data.majorCoursesAvailable && (
                <p className="remaining-notice">전공 과목 정보가 없어요. 필수교양만 보여드려요.</p>
            )}

            {filtered.length === 0 ? (
                <p className="remaining-empty">남은 과목이 없습니다.</p>
            ) : (
                <div className="remaining-grid">
                    {filtered.map((c, i) => {
                        const placed = placedKeys.has(courseKey(c));
                        const color = CLASSIFICATION_COLORS[c.classification] ?? DEFAULT_COLOR;
                        return (
                            <div
                                key={courseKey(c)}
                                className={`remaining-card ${placed ? 'placed' : ''}`}
                                style={{
                                    gridRow: i < half ? 1 : 2,
                                    gridColumn: (i < half ? i : i - half) + 1,
                                    ...(placed ? {} : { backgroundColor: color.bg }),
                                }}
                                draggable
                                onDragStart={(e) => handleDragStart(e, c)}
                                title={placed ? '이미 학기에 배치한 과목이에요' : '드래그해서 학기에 배치하세요'}
                            >
                                <span className="remaining-tag" style={placed ? undefined : { color: color.text }}>
                                    {CLASSIFICATION_LABELS[c.classification] ?? c.classification}
                                </span>
                                <div className="remaining-card-body">
                                    <span className="remaining-name">{c.courseName}</span>
                                    <span className="remaining-credits">{c.credits}</span>
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

export default RemainingCourses;
