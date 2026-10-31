import { useState } from "react";
import "./DegreeModal.css";

// onSubmit({ courseName })
function AddMicroDegreeModal({ onClose, onSubmit }) {
    const [name, setName] = useState("");
    const canSubmit = name.trim().length > 0;

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!canSubmit) return;
        onSubmit({ courseName: name.trim() });
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

                <label className="dm-label" htmlFor="dm-micro-name">수업 입력하기</label>
                <input
                    id="dm-micro-name"
                    className="dm-input"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="수업을 입력하세요."
                    autoFocus
                />

                <div className="dm-actions">
                    <button type="button" className="dm-btn dm-cancel" onClick={onClose}>취소</button>
                    <button type="submit" className="dm-btn dm-confirm" disabled={!canSubmit}>확인</button>
                </div>
            </form>
        </div>
    );
}

export default AddMicroDegreeModal;
