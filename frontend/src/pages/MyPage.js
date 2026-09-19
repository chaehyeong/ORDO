import Sidebar from "../components/Sidebar";
import Container from 'react-bootstrap/Container';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import './MyPage.css'

function MyPage() {
    return (
        <div className="layout">
            <Sidebar />
            <div className='page'>
                <h2 className="title">마이페이지</h2>
                <Container>
                    <Row>
                        <Col xs={7}>
                            <div className="box myinfo">
                                <h3 className="title">내 정보</h3>
                                <Container>
                                    <Row>
                                        <Col xs={5}>이름</Col>
                                        <Col xs={7}>여기</Col>
                                    </Row>
                                    <Row>
                                        <Col xs={5}>학번</Col>
                                        <Col xs={7}>데이터</Col>
                                    </Row>
                                    <Row>
                                        <Col xs={5}>학과</Col>
                                        <Col xs={7}>넣어야</Col>
                                    </Row>
                                    <Row>
                                        <Col xs={5}>전화번호</Col>
                                        <Col xs={7}>되는데ㅔ</Col>
                                    </Row>
                                    <Row>
                                        <Col xs={5}>이메일</Col>
                                        <Col xs={7}>아ㅏㅏㅏㄱ</Col>
                                    </Row>
                                </Container>
                            </div> 
                        </Col>
                        <Col xs={5}>
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