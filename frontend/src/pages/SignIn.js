import { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import Form from 'react-bootstrap/Form';
import Button from 'react-bootstrap/Button';
import './SignIn.css'
import logo1 from '../assets/logo1.png';
import logo2 from '../assets/logo2.png';
import { api, saveTokens } from '../api/client';

function SignIn() {
    const navigate = useNavigate();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [keepLogin, setKeepLogin] = useState(false);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const handleLogin = async () => {
        if (!email || !password) {
            setError('이메일과 비밀번호를 입력하세요.');
            return;
        }
        setError('');
        setLoading(true);

        const res = await api('/api/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password }),
        });

        setLoading(false);
        if (res.success) {
            saveTokens(res.data.accessToken, res.data.refreshToken, keepLogin);
            navigate('/homepage');
        } else {
            setError(res.error.message);
        }
    };

    // 비밀번호 칸에서 엔터 치면 로그인
    const handleKeyDown = (e) => {
        if (e.key === 'Enter') handleLogin();
    };

    return (
        <div className='body'>
            <div className='signin'>
                <div className='sign-logo'>
                    <img src={logo1} alt='logo1'/>
                    <img src={logo2} alt='logo2'/>
                </div>
                <div className='form1'>
                    <Form>
                        <Form.Group className="mb-3" controlId="formGroupEmail">
                            <Form.Label>이메일</Form.Label>
                            <Form.Control type="email" placeholder="이메일을 입력하세요."
                                value={email} onChange={(e) => setEmail(e.target.value)} />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formGroupPassword">
                            <Form.Label>비밀번호</Form.Label>
                            <Form.Control type="password" placeholder="비밀번호를 입력하세요."
                                value={password} onChange={(e) => setPassword(e.target.value)}
                                onKeyDown={handleKeyDown} />
                        </Form.Group>
                    </Form>
                    <Form.Group className="mb-3" controlId="formBasicCheckbox">
                        <Form.Check type="checkbox" label="로그인 상태 유지"
                            checked={keepLogin} onChange={(e) => setKeepLogin(e.target.checked)} />
                    </Form.Group>
                    {error && <div className="text-danger small mb-2">{error}</div>}
                    <div className="d-grid gap-2">
                        <div className='char'>
                            <Button id='login-btn' onClick={handleLogin} disabled={loading}>
                                {loading ? '로그인 중...' : '로그인'}
                            </Button>
                        </div>
                    </div>
                    <div className='option'>
                        <NavLink className='option1' to='#'>비밀번호 찾기</NavLink> {/* 안 눌러지는거 맞음. 가짜임 */}
                        <span> | </span>
                        <NavLink className='option2' to='/SignUp'>회원가입</NavLink>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default SignIn;
