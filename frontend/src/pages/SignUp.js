import React, { useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import './SignUp.css'

function SignUp() {
    useEffect(() => {
        document.body.style.backgroundColor = '#F9FDF9';
        return () => {
            document.body.style.backgroundColor = null;
        };
    }, []);

    return (
        <div className='body'>
            <div className='signup'>
                <div className='form'>
                    <Form>
                        <Form.Group className="mb-3" controlId="formBasicName">
                            <Form.Label>이름</Form.Label>
                            <Form.Control type="text" placeholder="이름을 입력하세요." />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formBasicPhone">
                            <Form.Label>전화번호</Form.Label>
                            <Form.Control type="tel" placeholder="010 - 0000 - 0000" />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formBasicEmail">
                            <Form.Label>이메일</Form.Label>
                            <Form.Control type="email" placeholder="이메일을 입력하세요." />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formBasicPassword">
                            <Form.Label>비밀번호</Form.Label>
                            <Form.Control type="password" placeholder="비밀번호를 입력하세요." />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formBasicPassword">
                            <Form.Label>비밀번호 확인</Form.Label>
                            <Form.Control type="password" placeholder="비밀번호를 다시 입력하세요." />
                        </Form.Group>
                        <div className="d-grid gap-2">
                            <Button id='login-btn'><NavLink className='char' to='/SignIn'>회원가입</NavLink></Button>
                        </div>
                    </Form>
                </div>
            </div>
        </div>
    );
}

export default SignUp;

/*백엔드 연결 안 하고 화면만 띄울거임*/