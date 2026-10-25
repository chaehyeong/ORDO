import './Sidebar.css';
import { NavLink } from 'react-router-dom';
import ordo_logo from '../assets/ordo_logo.png';
import { FiHome } from "react-icons/fi";
import { FiCalendar } from "react-icons/fi";
import { FiTable } from "react-icons/fi";
import { FiAward } from "react-icons/fi";
import { FiMapPin } from "react-icons/fi";
import { FiUser } from "react-icons/fi";

function Sidebar() {
    return (
        <div className='sidebar'>
            <div className='side-logo'>
                <img src={ordo_logo} alt='logo1'/>
            </div>
            <div className='index'>
                <FiHome className='index-icon' />
                <NavLink className='char' to='/homepage'>홈</NavLink>
            </div>
            <div className='index'>
                <FiCalendar className='index-icon' />
                <NavLink className='char' to='/calendar'>캘린더</NavLink>
            </div>
            <div className='index'>
                <FiTable className='index-icon' />
                <NavLink className='char' to='/timetable'>시간표</NavLink>
            </div>
            <div className='index'>
                <FiAward className='index-icon' />
                <NavLink className='char' to='/degree'>학사관리</NavLink>
            </div>
            <div className='index'>
                <FiMapPin className='index-icon' />
                <NavLink className='char' to='/roadmap'>졸업 로드맵</NavLink>
            </div>
            <div className='index'>
                <FiUser className='index-icon' />
                <NavLink className='char' to='/mypage'>마이페이지</NavLink>
            </div>
        </div>
    );
}

export default Sidebar;