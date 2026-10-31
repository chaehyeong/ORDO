import './MainPage.css';
import { Link } from 'react-router-dom';
import ordo_logo from '../assets/ordo_logo.png';
import frodo from '../assets/frodo1.png';
import { FiCheckSquare } from "react-icons/fi";
import { FiBook } from "react-icons/fi";
import { FiBell } from "react-icons/fi";
import { FiCalendar } from "react-icons/fi";

import { useEffect } from 'react';
import { api } from '../api/client';

const services = [
    {
        key: 'scheduler',
        color: 'pink',
        title: '스케쥴러',
        desc: ['강의, 과제, 시험까지', '한눈에 관리해요.'],
        icon: (
            <FiCheckSquare />
        ),
    },
    {
        key: 'academic',
        color: 'yellow',
        title: '학사 정보 통합',
        desc: ['수강신청, 종강일정, 졸업 요건을', '쉽게 확인할 수 있어요.'],
        icon: (
            <FiBook />
        ),
    },
    {
        key: 'ai',
        color: 'green',
        title: 'AI 추천',
        desc: ['AI 가 당신의 일정과 선호를 분석해', '맞춤형 일정을 추천해줘요.'],
        icon: (
            <FiBell />
        ),
    },
    {
        key: 'calendar',
        color: 'blue',
        title: '나만의 캘린더',
        desc: ['개인 일정과 학업 계획을', '캘린더로 깔끔하게 정리해요.'],
        icon: (
            <FiCalendar />
        ),
    },
];

function MainPage() {
    useEffect(() => {
        api('/api/health').then((res) => console.log(res));
    }, []);
    
    // 카드 세트를 두 번 깔아두고 트랙을 -50%까지 밀어야 이음매가 안 보임
    const loop = [...services, ...services];

    return (
        <div className='main-body'>
            <div className='hero'>
                <div className='hero-text'>
                    <div className='main-logo'>
                        <img src={ordo_logo} alt='ordo_logo'/>
                    </div>
                    <div>
                        <div className='slogan'>ORDO와 함께하는<br/>대학생활의 모든 순간</div>
                        <div className='intro'>일정 관리부터 학사 정보까지, <br/>ORDO가 당신의 캠퍼스 라이프를 더 스마트하게 만들어줍니다.</div>
                    </div>
                    <div className='sign'>
                        <Link className='in' to='/signin'>로그인 &nbsp; &rarr;</Link>
                        <Link className='up' to='/signup'>회원가입 &nbsp; &rarr;</Link>
                    </div>
                </div>
                <img className='hero-frodo' src={frodo} alt='frodo' />  
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
