import Sidebar from "../components/Sidebar";

function MyPage() {
    return (
        <div className="layout">
            <Sidebar />
            <div className='page'>
                마이페이지
            </div>
        </div>
    );
}

export default MyPage;