import { useState } from "react";
import { FiChevronDown } from "react-icons/fi";
import "./DegreeModal.css";

const DEPARTMENTS = [];

// onSubmit({ department, type })  type: "다전공" | "부전공"
function AddMajorModal({ onClose, onSubmit }) {
    const [department, setDepartment] = useState("");

    const submit = (type) => {
        if (!department) return;
        onSubmit({ department, type });
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
                        {DEPARTMENTS.map((d) => (
                            <option key={d} value={d}>{d}</option>
                        ))}
                    </select>
                    <span className="dm-select-arrow"><FiChevronDown size={16} /></span>
                </div>

                <div className="dm-actions">
                    <button type="button" className="dm-btn dm-cancel" onClick={onClose}>취소</button>
                    <button type="button" className="dm-btn dm-confirm" disabled={!department} onClick={() => submit("다전공")}>다전공</button>
                    <button type="button" className="dm-btn dm-confirm" disabled={!department} onClick={() => submit("부전공")}>부전공</button>
                </div>
            </div>
        </div>
    );
}

export default AddMajorModal;
