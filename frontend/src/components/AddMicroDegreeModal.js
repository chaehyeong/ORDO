import { useEffect, useState } from "react";
import { FiChevronDown } from "react-icons/fi";
import { api } from "../api/client";
import "./DegreeModal.css";

// 마이크로디그리 목록은 GET /api/catalog/micro-degrees 에서 받아 고르게 한다 (서버는 id 로 등록)
// excludeIds = 이미 등록한 마이크로디그리 id
// onSubmit({ microDegreeId })
function AddMicroDegreeModal({ excludeIds = [], onClose, onSubmit }) {
    const [microDegreeId, setMicroDegreeId] = useState("");
    const [options, setOptions] = useState([]);
    const canSubmit = microDegreeId !== "";

    useEffect(() => {
        api('/api/catalog/micro-degrees').then((res) => {
            if (res.success) setOptions(res.data.filter((md) => !excludeIds.includes(md.id)));
        });
        // excludeIds 는 창이 열려 있는 동안 바뀌지 않는다
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!canSubmit) return;
        onSubmit({ microDegreeId: Number(microDegreeId) });
    };

    return (
        <div className="dm-backdrop" onClick={onClose}>
            <form
                className="dm-modal"
                role="dialog"
                aria-modal="true"
                onClick={(e) => e.stopPropagation()}
                onSubmit={handleSubmit}
            >
                <h2 className="dm-title">마이크로디그리 추가하기</h2>

                <label className="dm-label" htmlFor="dm-micro-name">마이크로디그리 선택하기</label>
                <div className="dm-select-wrap">
                    <select
                        id="dm-micro-name"
                        className="dm-input"
                        value={microDegreeId}
                        onChange={(e) => setMicroDegreeId(e.target.value)}
                    >
                        <option value="" disabled hidden></option>
                        {options.map((md) => (
                            <option key={md.id} value={md.id}>{md.name} ({md.hostDepartment})</option>
                        ))}
                    </select>
                    <span className="dm-select-arrow"><FiChevronDown size={16} /></span>
                </div>

                <div className="dm-actions">
                    <button type="button" className="dm-btn dm-cancel" onClick={onClose}>취소</button>
                    <button type="submit" className="dm-btn dm-confirm" disabled={!canSubmit}>확인</button>
                </div>
            </form>
        </div>
    );
}

export default AddMicroDegreeModal;
