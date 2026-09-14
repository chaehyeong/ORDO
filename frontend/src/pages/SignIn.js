import React, { useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import './SignIn.css'
import logo1 from '../assets/logo1.png';
import logo3 from '../assets/logo3.png';

function SignIn() {
    useEffect(() => {
        document.body.style.backgroundColor = '#F9FDF9';
        return () => {
            document.body.style.backgroundColor = null;
        };
    }, []);

    return (
        <div className='body'>
            <div className='signin'>
                <div className='sign-logo'>
                    <img src={logo1} alt='logo1'/>
                    <img src={logo3} alt='logo2'/>
                </div>
                <div className='form1'>
                    <Form>
                        <Form.Group className="mb-3" controlId="formGroupEmail">
                            <Form.Label>Email address</Form.Label>
                            <Form.Control type="email" placeholder="이메일을 입력하세요." />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formGroupPassword">
                            <Form.Label>Password</Form.Label>
                            <Form.Control type="password" placeholder="비밀번호를 입력하세요." />
                        </Form.Group>
                    </Form>
                    <div className="d-grid gap-2">
                        <Button id='login-btn'><NavLink className='char' to='/HomePage'>로그인</NavLink></Button>
                    </div>
                    <div className='option'>
                        <NavLink className='option1' to='#'>비밀번호 찾기</NavLink>
                        <span> | </span>
                        <NavLink className='option2' to='/SignUp'>회원가입</NavLink>
                    </div>
                </div>
                <div className='line'>
                    <div></div>
                    <span className='text'>또는</span>
                    <div></div>
                </div>
                <div className='form2'>
                    <div className="d-grid gap-2">
                        <Button id='info21-btn'><a href='https://info21.khu.ac.kr/com/LoginCtr/login.do?sso=ok'>경희대학교 통합 로그인</a></Button>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default SignIn;