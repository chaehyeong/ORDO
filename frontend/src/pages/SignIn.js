import React, { useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import './SignIn.css'
import logo1 from '../assets/logo1.png';
import logo2 from '../assets/logo2.png';

function SignIn() {
    useEffect(() => {
        document.body.style.backgroundColor = '#F9FDF9';
        return () => {
            document.body.style.backgroundColor = null;
        };
    }, []);

    return (
        <div>
            <div className='signin'>
                <div className='sign-logo'>
                    <img src={logo1} alt='logo1'/>
                    <img src={logo2} alt='logo2'/>
                </div>
                <div className='form'>
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
                    <NavLink className='fake-btn' to='#'>비밀번호 찾기</NavLink>
                </div>
                <div className='line'>
                    <div></div>
                    <span className='text'>또는</span>
                    <div></div>
                </div>
                {/*
                지금은 걍 로그인 누르면 바로 홈화면 넘어가는 상태니까 
                백엔드쪽에서 알아서 잘 하시리라 믿고 나머지는 그냥 갑자기 의욕 떨어져서 못하겟고 자야겟다
                */}
            </div>
        </div>
    );
}

export default SignIn;