import { useState } from "react";
import { mockUser } from "../mock/userMock";
import Sidebar from "../components/Sidebar";
import user_icon from '../assets/user_icon.png';
import Container from 'react-bootstrap/Container';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import './MyPage.css'

function MyPage() {
    const [user, setUser] = useState(mockUser);

    return (
        <div className="layout">
            <Sidebar />
            <div className='page'>
                <h2 className="title">마이페이지</h2>
                <Container>
                    <Row>
                        <Col style={{ padding: '0' }}>
                            <div className="box myinfo">
                                <h3 className="title">내 정보</h3>
                                <div className="profile">
                                    <img src={user_icon} alt="Profile"/>
                                    <div>
                                        <div className="helpme">{user.userName}</div>
                                        <div className="helpmee">경희대학교 {user.userDept}</div>
                                    </div>
                                </div>
                                <button className="profile-edit-btn">프로필 사진 변경</button>
                                <Container>
                                    <Row>
                                        <Col xs={4}>이름</Col>
                                        <Col xs={8}>{user.userName}</Col>
                                    </Row>
                                    <Row>
                                        <Col xs={4}>학번</Col>
                                        <Col xs={8}>{user.userId}</Col>
                                    </Row>
                                    <Row>
                                        <Col xs={4}>학과</Col>
                                        <Col xs={8}>{user.userDept}</Col>
                                    </Row>
                                    <Row>
                                        <Col xs={4}>전화번호</Col>
                                        <Col xs={8}>{user.userPhone}</Col>
                                    </Row>
                                    <Row>
                                        <Col xs={4}>이메일</Col>
                                        <Col xs={8}>{user.userEmail}</Col>
                                    </Row>
                                </Container>
                            </div> 
                            <h4>아 어떡해 나 이 페이지 css 못 만지겟어</h4>
                        </Col>
                        <Col>
                            <div className="box">
                                <h3 className="title">알림 설정</h3>
                            </div>
                            <div className="box">
                                <h3 className="title">로그아웃</h3>
                            </div>
                        </Col>
                    </Row>
                </Container>                
            </div>
        </div>
    );
}

export default MyPage;