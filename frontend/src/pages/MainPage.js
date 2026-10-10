import './MainPage.css';
import { Link } from 'react-router-dom';
import logo1 from '../assets/logo1.png';
import logo2 from '../assets/logo2.png';

const services = [
    {
        key: 'scheduler',
        color: 'pink',
        title: '스케쥴러',
        desc: ['강의, 과제, 시험까지', '한눈에 관리해요.'],
        icon: (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="3" width="18" height="18" rx="3"/>
                <path d="M7.5 12.3l3 3 6-6.8"/>
            </svg>
        ),
    },
    {
        key: 'academic',
        color: 'yellow',
        title: '학사 정보 통합',
        desc: ['수강신청, 종강일정, 졸업 요건을', '쉽게 확인할 수 있어요.'],
        icon: (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <path d="M6 3h11a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"/>
                <path d="M4 17h14"/>
            </svg>
        ),
    },
    {
        key: 'ai',
        color: 'green',
        title: 'AI 추천',
        desc: ['AI 가 당신의 일정과 선호를 분석해', '맞춤형 일정을 추천해줘요.'],
        icon: (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <path d="M11 3l1.9 5.1L18 10l-5.1 1.9L11 17l-1.9-5.1L4 10l5.1-1.9z"/>
                <path d="M17.5 14.5l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z"/>
            </svg>
        ),
    },
    {
        key: 'calendar',
        color: 'blue',
        title: '나만의 캘린더',
        desc: ['개인 일정과 학업 계획을', '캘린더로 깔끔하게 정리해요.'],
        icon: (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="5" width="18" height="16" rx="3"/>
                <path d="M3 10h18M8 3v4M16 3v4"/>
            </svg>
        ),
    },
];

function MainPage() {
    // 카드 세트를 두 번 깔아두고 트랙을 -50%까지 밀어야 이음매가 안 보임
    const loop = [...services, ...services];

    return (
        <div>
            <div className='main-logo'>
                <img className='main-logo1' src={logo1}/>
                <img className='main-logo2' src={logo2}/>
            </div>
            <div>
                <div className='slogan'>ORDO와 함께하는<br/>대학생활의 모든 순간</div>
                <div className='intro'>일정 관리부터 학사 정보, AI 추천까지 ORDO가 스마트한 캠퍼스 라이프를 지원합니다.</div>
            </div>
            <div className='sign'>
                <Link className='in' to='/signin'>로그인 &nbsp; &rarr;</Link>
                <Link className='up' to='/signup'>회원가입 &nbsp; &rarr;</Link>
            </div>

            <div className='service'>
                <div className='service-track'>
                    {loop.map((item, index) => (
                        <div
                            className={`service-card service-card-${item.color}`}
                            key={`${item.key}-${index}`}
                            aria-hidden={index >= services.length}
                        >
                            <div className='card-icon'>{item.icon}</div>
                            <div className='card-title'>{item.title}</div>
                            <div className='card-desc'>
                                {item.desc.map((line, i) => (
                                    <span key={i}>
                                        {line}
                                        {i < item.desc.length - 1 && <br/>}
                                    </span>
                                ))}
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}

export default MainPage;
