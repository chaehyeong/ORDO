import './Sidebar.css';
import { NavLink } from 'react-router-dom';
import logo1 from '../assets/logo1.png';
import logo2 from '../assets/logo2.png';
import home_icon from '../assets/home_icon.png';
import cal_icon from '../assets/cal_icon.png';
import table_icon from '../assets/table_icon.png';
import award_icon from '../assets/award_icon.png';
import user_icon from '../assets/user_icon.png';

// 필요한 페이지 : 홈, 캘린더, 시간표, 학사관리, 마이페이지

function Sidebar() {
    return (
        <div className='sidebar'>
            <div className='slogo'>
                <img className='slogo1' src={logo1} alt='logo1'/>
                <img className='slogo2' src={logo2} alt='logo2'/>
            </div>
            <div className='index'>
                <img className='image' src={home_icon} alt='home_icon'/>
                <NavLink className='char' to='/homepage'>홈</NavLink>
            </div>
            <div className='index'>
                <img className='image' src={cal_icon} alt='cal_icon'/>
                <NavLink className='char' to='/calendar'>캘린더</NavLink>
            </div>
            <div className='index'>
                <img className='image' src={table_icon} alt='table_icon'/>
                <NavLink className='char' to='/timetable'>시간표</NavLink>
            </div>
            <div className='index'>
                <img className='image' src={award_icon} alt='award_icon'/>
                <NavLink className='char' to='/degree'>학사관리</NavLink>
            </div>
            <div className='index'>
                <img className='image' src={user_icon} alt='user_icon'/>
                <NavLink className='char' to='/mypage'>마이페이지</NavLink>
            </div>
        </div>
    );
}

export default Sidebar;