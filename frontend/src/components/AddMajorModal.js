import { useEffect, useState } from "react";
import { FiChevronDown } from "react-icons/fi";
import { api } from "../api/client";
import "./DegreeModal.css";

// 학과 목록은 서버에서 받는다
// - 다전공: GET /api/catalog/majors?includeConvergence=true (융합전공 포함)
// - 부전공: GET /api/catalog/minors (부전공 요건이 있는 학과만) → 목록에 없는 학과면 부전공 버튼을 막는다
// admissionYear = 내 입학년도, excludeIds = 이미 주전공·다전공·부전공인 학과 id
// onSubmit({ majorId, type })  type: "다전공" | "부전공"
function AddMajorModal({ admissionYear, excludeIds = [], onClose, onSubmit }) {
    const [department, setDepartment] = useState("");
    const [majors, setMajors] = useState([]);
    const [minorIds, setMinorIds] = useState([]);

    useEffect(() => {
        if (!admissionYear) return;
        Promise.all([
            api(`/api/catalog/majors?admissionYear=${admissionYear}&includeConvergence=true`),
            api(`/api/catalog/minors?admissionYear=${admissionYear}`),
        ]).then(([majorRes, minorRes]) => {
            if (majorRes.success) setMajors(majorRes.data.filter((m) => !excludeIds.includes(m.id)));
            if (minorRes.success) setMinorIds(minorRes.data.map((m) => m.id));
        });
        // excludeIds 는 창이 열려 있는 동안 바뀌지 않는다
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [admissionYear]);

    const canMinor = department && minorIds.includes(Number(department));

    const submit = (type) => {
        if (!department) return;
        onSubmit({ majorId: Number(department), type });
    };

    return (
        <div className="dm-backdrop" onClick={onClose}>
            <div className="dm-modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
                <h2 className="dm-title">다전공 추가하기</h2>

                <label className="dm-label" htmlFor="dm-department">학과 선택하기</label>
                <div className="dm-select-wrap">
                    <select
                        id="dm-department"
                        className="dm-input"
                        value={department}
                        onChange={(e) => setDepartment(e.target.value)}
                    >
                        <option value="" disabled hidden></option>
                        {majors.map((m) => (
                            <option key={m.id} value={m.id}>{m.displayName}</option>
                        ))}
                    </select>
                    <span className="dm-select-arrow"><FiChevronDown size={16} /></span>
                </div>

                <div className="dm-actions">
                    <button type="button" className="dm-btn dm-cancel" onClick={onClose}>취소</button>
                    <button type="button" className="dm-btn dm-confirm" disabled={!department} onClick={() => submit("다전공")}>다전공</button>
                    <button type="button" className="dm-btn dm-confirm" disabled={!canMinor} onClick={() => submit("부전공")}>부전공</button>
                </div>
            </div>
        </div>
    );
}

export default AddMajorModal;
