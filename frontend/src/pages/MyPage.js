import { useEffect, useState } from "react";
import { useNavigate } from 'react-router-dom';
import { api, getRefreshToken, clearTokens } from "../api/client";
import Sidebar from "../components/Sidebar";
import frodo from "../assets/frodo3.png"
import { FiUser, FiBell, FiLogOut } from "react-icons/fi";
import './MyPage.css';

function MyPage() {
    const navigate = useNavigate();
    const [user, setUser] = useState(null);
    const [alarm, setAlarm] = useState(true);
    const [error, setError] = useState('');

    // 처음 들어왔을 때 내 정보 불러오기
    useEffect(() => {
        api('/api/users/me').then((res) => {
            if (res.success) {
                setUser(res.data);
                setAlarm(res.data.notificationEnabled);
            } else {
                setError(res.error.message);
            }
        });
    }, []);

    // 알림 스위치: 화면 먼저 바꾸고, 서버 저장이 실패하면 되돌린다
    const handleAlarm = async () => {
        const next = !alarm;
        setAlarm(next);
        const res = await api('/api/users/me/settings', {
            method: 'PATCH',
            body: JSON.stringify({ notificationEnabled: next }),
        });
        if (!res.success) {
            setAlarm(!next);
            alert(res.error.message);
        }
    };

    const handleLogout = async () => {
        await api('/api/auth/logout', {
            method: 'POST',
            body: JSON.stringify({ refreshToken: getRefreshToken() }),
        });
        clearTokens();
        navigate('/');
    };

    if (error) return <div className="layout"><Sidebar /><div className="page">{error}</div></div>;
    if (!user) return <div className="layout"><Sidebar /><div className="page">불러오는 중...</div></div>;

    const majorName = user.major ? user.major.displayName : '-';
    const infoList = [
        ['이름', user.name],
        ['학번', user.studentNumber],
        ['학과', majorName],
        ['전화번호', user.phone || '-'],
        ['이메일', user.email],
    ];

    return (
        <div className="layout">
            <Sidebar />
            <div className="page">
                <h2 className="page-title">마이페이지</h2>

                <div className="mypage-grid">
                    <section className="box">
                        <h3 className="mp-box-title">내 정보</h3>

                        <div className="profile">
                            <FiUser className="profile-img" />
                            <div>
                                <div className="profile-name">{user.name}</div>
                                <div className="profile-major">경희대학교 {majorName}</div>
                            </div>
                        </div>

                        <button className="profile-edit-btn">프로필 사진 변경</button>

                        <dl className="info-list">
                            {infoList.map(([label, value]) => (
                                <div className="info-row" key={label}>
                                    <dt>{label}</dt>
                                    <dd>{value}</dd>
                                </div>
                            ))}
                        </dl>
                    </section>

                    <div className="side-stack">
                        <section className="box">
                            <h3 className="mp-box-title">알림 설정</h3>
                            <p className="box-desc">중요한 일정과 소식을 놓치지 않도록 설정할 수 있습니다.</p>
                            <div className="toggle-row">
                                <span className="toggle-label"><FiBell /> 알림</span>
                                <button
                                    type="button"
                                    className={`switch ${alarm ? 'on' : ''}`}
                                    aria-pressed={alarm}
                                    onClick={handleAlarm}
                                >
                                    <span className="knob" />
                                </button>
                            </div>
                        </section>

                        <section className="box logout-box">
                            <div>
                                <h3 className="mp-box-title">로그아웃</h3>
                                <p className="box-desc">현재 계정에서 로그아웃합니다.</p>
                            </div>
                            <button type="button" className="logout-btn" onClick={handleLogout}>
                                <FiLogOut /> 로그아웃
                            </button>
                        </section>
                    </div>
                </div>
                <img className="my-frodo" src={frodo} alt="frodo" />
            </div>
        </div>
    );
}

export default MyPage;
