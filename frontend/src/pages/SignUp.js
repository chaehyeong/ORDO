import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import './SignUp.css'
import { api } from '../api/client';

function SignUp() {
    const navigate = useNavigate();
    const [form, setForm] = useState({
        name: '',
        phone: '',
        email: '',
        password: '',
        passwordConfirm: '',
        majorId: '',
        studentNumber: '',
        termsAgreed: false,
    });
    const [majors, setMajors] = useState([]);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    // 입력칸 하나 바뀔 때마다 form 의 그 값만 바꾼다
    const handleChange = (key) => (e) => {
        setForm({ ...form, [key]: e.target.value });
    };

    // 학번 10자리가 다 들어오면 앞 4자리(입학년도)로 학과 목록을 불러온다
    const admissionYear = form.studentNumber.length === 10 ? form.studentNumber.slice(0, 4) : null;

    useEffect(() => {
        setForm((prev) => ({ ...prev, majorId: '' }));
        setMajors([]);
        if (!admissionYear) return;

        let ignore = false;   // 학번을 빨리 바꿨을 때 늦게 온 예전 응답은 버린다
        api(`/api/catalog/majors?admissionYear=${admissionYear}`).then((res) => {
            if (ignore) return;
            if (res.success) {
                setMajors(res.data);
            } else {
                setError(res.error.message);
            }
        });
        return () => { ignore = true; };
    }, [admissionYear]);

    const handleSignUp = async () => {
        if (form.password !== form.passwordConfirm) {
            setError('비밀번호가 서로 다릅니다.');
            return;
        }
        if (!form.majorId) {
            setError('학과를 선택하세요.');
            return;
        }
        if (!form.termsAgreed) {
            setError('이용약관에 동의해 주세요.');
            return;
        }
        setError('');
        setLoading(true);

        const res = await api('/api/auth/signup', {
            method: 'POST',
            body: JSON.stringify({
                name: form.name,
                phone: form.phone || null,
                email: form.email,
                password: form.password,
                passwordConfirm: form.passwordConfirm,
                majorId: Number(form.majorId),
                studentNumber: form.studentNumber,
                termsAgreed: form.termsAgreed,
            }),
        });

        setLoading(false);
        if (res.success) {
            navigate('/signin');
        } else {
            setError(res.error.message);
        }
    };

    return (
        <div className='body'>
            <div className='signup'>
                <div className='signup-title'>회원가입</div>
                <div className='form'>
                    <Form>
                        <Form.Group className="mb-3" controlId="formBasicName">
                            <Form.Label>이름</Form.Label>
                            <Form.Control type="text" placeholder="이름을 입력하세요."
                                value={form.name} onChange={handleChange('name')} />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formBasicPhone">
                            <Form.Label>전화번호</Form.Label>
                            <Form.Control type="tel" placeholder="010 - 0000 - 0000"
                                value={form.phone} onChange={handleChange('phone')} />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formBasicEmail">
                            <Form.Label>이메일</Form.Label>
                            <Form.Control type="email" placeholder="이메일을 입력하세요."
                                value={form.email} onChange={handleChange('email')} />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formBasicPassword">
                            <Form.Label>비밀번호</Form.Label>
                            <Form.Control type="password" placeholder="8~64자, 영문+숫자 포함."
                                value={form.password} onChange={handleChange('password')} />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formBasicPasswordConfirm">
                            <Form.Label>비밀번호 확인</Form.Label>
                            <Form.Control type="password" placeholder="비밀번호를 다시 입력하세요."
                                value={form.passwordConfirm} onChange={handleChange('passwordConfirm')} />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formBasicNum">
                            <Form.Label>학번</Form.Label>
                            <Form.Control type="text" inputMode="numeric" maxLength={10} placeholder="학번을 입력하세요."
                                value={form.studentNumber} onChange={handleChange('studentNumber')} />
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formBasicMajor">
                            <Form.Label>학과</Form.Label>
                            <Form.Select aria-label="Default select example"
                                value={form.majorId} onChange={handleChange('majorId')}
                                disabled={majors.length === 0}>
                                <option value="">
                                    {admissionYear ? '학과를 선택하세요.' : '학번을 먼저 입력하세요.'}
                                </option>
                                {majors.map((major) => (
                                    <option key={major.id} value={major.id}>{major.displayName}</option>
                                ))}
                            </Form.Select>
                        </Form.Group>
                        <Form.Group className="mb-3" controlId="formBasicCheckbox">
                            <Form.Check type="checkbox" label="[필수] 개인정보 수집·이용에 동의합니다."
                                checked={form.termsAgreed}
                                onChange={(e) => setForm({ ...form, termsAgreed: e.target.checked })} />
                        </Form.Group>
                    </Form>
                </div>
                {error && <div className="text-danger small text-center">{error}</div>}
                {/* 가입 성공하면 로그인 화면으로 넘어감 */}
                <div className="d-grid gap-2">
                    <div className='char'>
                        <Button id='signup-btn' onClick={handleSignUp} disabled={loading}>
                            {loading ? '가입 중...' : '회원가입'}
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default SignUp;
