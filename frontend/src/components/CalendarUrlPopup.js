import { useState } from "react";
import "./CalendarUrlPopup.css";

function CalendarUrlPopup({ onConfirm, onCancel }) {
    const [url, setUrl] = useState("");

    const handleSubmit = (e) => {
        e.preventDefault();
        const trimmed = url.trim();
        if (!trimmed) return;
        onConfirm(trimmed);
    };

    return (
        <>
            <div className="url-popup-backdrop" onClick={onCancel} />
            <form className="url-popup" onSubmit={handleSubmit}>
                <input
                    type="url"
                    autoFocus
                    value={url}
                    onChange={(e) => setUrl(e.target.value)}
                    placeholder="캘린더 URL 추가하기"
                />
                <div className="url-popup-actions">
                    <button type="button" className="url-popup-cancel" onClick={onCancel}>취소</button>
                    <button type="submit" className="url-popup-confirm" disabled={!url.trim()}>확인</button>
                </div>
            </form>
        </>
    );
}

export default CalendarUrlPopup;
