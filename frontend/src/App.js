import { BrowserRouter, Routes, Route } from 'react-router-dom';
import MainPage from './pages/MainPage';
import SignIn from './pages/SignIn';
import SignUp from './pages/SignUp';
import HomePage from './pages/HomePage';
import Calendar from './pages/Calendar';
import TimeTable from './pages/TimeTable';
import Degree from './pages/Degree';
import MyPage from './pages/MyPage';
import './App.css'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path='/' element={<MainPage />} />
        <Route path='/signin' element={<SignIn />} />
        <Route path='/signup' element={<SignUp />} />
        <Route path='/homepage' element={<HomePage />} />
        <Route path='/calendar' element={<Calendar />} />
        <Route path='/timetable' element={<TimeTable />} />
        <Route path='/degree' element={<Degree />} />
        <Route path='/mypage' element={<MyPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App; 