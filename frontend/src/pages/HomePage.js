import Sidebar from "../components/Sidebar";

function Home() {
    return (
        <div className="layout">
            <Sidebar />
            <div className='page'>
                홈 화면
            </div>
        </div>
    );
}

export default Home;