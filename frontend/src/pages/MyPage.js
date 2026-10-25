import { useState } from "react";
import { NavLink } from 'react-router-dom';
import { mockUser } from "../mock/userMock";
import Sidebar from "../components/Sidebar";
import frodo from "../assets/frodo3.png"
import { FiUser, FiBell, FiLogOut } from "react-icons/fi";
import './MyPage.css';

function MyPage() {
    const [user] = useState(mockUser);
    const [alarm, setAlarm] = useState(true);

    const infoList = [
        ['이름', user.userName],
        ['학번', user.userId],
        ['학과', user.userMajor],
        ['전화번호', user.userPhone],
        ['이메일', user.userEmail],
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
                                <div className="profile-name">{user.userName}</div>
                                <div className="profile-major">경희대학교 {user.userMajor}</div>
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
                                    onClick={() => setAlarm(!alarm)}
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
                            <NavLink to='/'><button type="button" className="logout-btn">
                                <FiLogOut /> 로그아웃
                            </button></NavLink>
                        </section>
                    </div>
                </div>
                <img className="my-frodo" src={frodo} alt="frodo" />
            </div>
        </div>
    );
}

export default MyPage;