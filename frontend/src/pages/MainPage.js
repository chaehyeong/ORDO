import './MainPage.css';
import { Link } from 'react-router-dom';
import logo1 from '../assets/logo1.png';
import logo2 from '../assets/logo2.png';

function MainPage() {
    return (
        <div>
            <div className='mlogo'>
                <img className='mlogo1' src={logo1}/>
                <img className='mlogo2' src={logo2}/>
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
                <div></div>
                <div></div>
                <div></div>
                <div></div>
            </div>
        </div>
    );
}

export default MainPage;