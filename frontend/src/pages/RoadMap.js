import { useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import SemesterCurve from "../components/SemesterCurve";
import CompletedCourses from "../components/CompletedCourses";
import AddCompletedCourseModal from "../components/AddCompletedCourseModal";
import RemainingCourses, { courseKey } from "../components/RemainingCourses";
import SemesterPlanner, { getPlanTerms, sameTerm, termLabel } from "../components/SemesterPlanner";
import AddPlanCourseModal from "../components/AddPlanCourseModal";
import { mockRoadmap } from "../mock/roadmapMock";
import frodo from "../assets/frodo4.png";
import './RoadMap.css';

// 이수내역 정렬 순서 (학년도 → 1학기·여름·2학기·겨울)
const TERM_ORDER = { FIRST: 0, SUMMER: 1, SECOND: 2, WINTER: 3 };

function RoadMap() {
    const [completedCourses, setCompletedCourses] = useState(mockRoadmap.completedCourses);
    const [showAddCourse, setShowAddCourse] = useState(false);
    // 학기별 배치: GET /api/academic/plans 응답(학기별)을 과목 한 줄씩 펼친 목록 = PUT 요청 courses 모양
    const [plans, setPlans] = useState(() =>
        mockRoadmap.plans.terms.flatMap((t) => t.courses.map((c) => ({ ...c, year: t.year, term: t.term })))
    );
    const [showAddPlan, setShowAddPlan] = useState(false);

    const planTerms = useMemo(() => getPlanTerms(mockRoadmap.gpa, plans), [plans]);
    const placedKeys = useMemo(() => new Set(plans.map(courseKey)), [plans]);

    // 백엔드 연동 시 POST /api/academic/completed-courses 후 응답 data를 넣으면 된다
    const handleAddCourse = (course) => {
        setCompletedCourses((prev) =>
            [...prev, { ...course, id: Date.now() }].sort(
                (a, b) => a.year - b.year || TERM_ORDER[a.term] - TERM_ORDER[b.term]
            )
        );
        setShowAddCourse(false);
    };

    // 백엔드 연동 시 바뀔 때마다 PUT /api/academic/plans 로 전체 목록을 보내고 응답으로 다시 그린다
    const handlePlansChange = (next) => setPlans(next);

    const handleResetPlans = () => {
        if (window.confirm('배치한 과목을 모두 지울까요?')) handlePlansChange([]);
    };

    // 수업 추가: 같은 학기에 같은 과목이 있으면 오류 문구를 돌려준다
    const handleAddPlanCourse = (course) => {
        if (plans.some((p) => sameTerm(p, course) && courseKey(p) === courseKey(course))) {
            return `${termLabel(course)}에 이미 있는 과목이에요.`;
        }
        handlePlansChange([...plans, { ...course, id: Date.now() }]);
        setShowAddPlan(false);
        return null;
    };

    return (
        <div className="layout">
            <Sidebar />
            <div className='page'>
                <h2 className="page-title roadmap-heading">졸업 로드맵</h2>
                <p className="roadmap-subtitle">지금까지의 나의 학교생활을 한눈에 보고 졸업 계획을 설계하세요.</p>
                <div className="roadmap-curve-wrap">
                    <img className="roadmap-frodo" src={frodo} alt="frodo" />
                    <section className="box roadmap-box">
                        <SemesterCurve gpa={mockRoadmap.gpa} />
                    </section>
                </div>

                <section className="box completed-box">
                    <CompletedCourses courses={completedCourses} onAdd={() => setShowAddCourse(true)} />
                </section>

                <section className="box remaining-box">
                    <RemainingCourses data={mockRoadmap.remainingCourses} placedKeys={placedKeys} />
                </section>

                <section className="box planner-box">
                    <SemesterPlanner
                        terms={planTerms}
                        plans={plans}
                        onChange={handlePlansChange}
                        onAddClick={() => setShowAddPlan(true)}
                        onReset={handleResetPlans}
                    />
                </section>
            </div>

            {showAddCourse && (
                <AddCompletedCourseModal onClose={() => setShowAddCourse(false)} onSubmit={handleAddCourse} />
            )}
            {showAddPlan && (
                <AddPlanCourseModal terms={planTerms} onClose={() => setShowAddPlan(false)} onSubmit={handleAddPlanCourse} />
            )}
        </div>
    );
}

export default RoadMap;
