import { useState } from "react";
import { FiInfo } from "react-icons/fi";
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
                <div className="url-popup-field">
                    <input
                        type="url"
                        autoFocus
                        value={url}
                        onChange={(e) => setUrl(e.target.value)}
                        placeholder="캘린더 URL 추가하기"
                    />
                    <span className="url-popup-info" tabIndex={0} aria-label="캘린더 URL 안내">
                        <FiInfo />
                        <span className="url-popup-tooltip" role="tooltip">
                            e-Campus 캘린더로 들어가 "캘린더 피드"를 누르면 링크가 나옵니다.
                        </span>
                    </span>
                </div>
                <div className="url-popup-actions">
                    <button type="button" className="url-popup-cancel" onClick={onCancel}>취소</button>
                    <button type="submit" className="url-popup-confirm" disabled={!url.trim()}>확인</button>
                </div>
            </form>
        </>
    );
}

export default CalendarUrlPopup;
