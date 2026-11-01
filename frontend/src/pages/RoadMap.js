import { useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import SemesterCurve from "../components/SemesterCurve";
import CompletedCourses from "../components/CompletedCourses";
import AddCompletedCourseModal from "../components/AddCompletedCourseModal";
import ImportButton from "../components/ImportButton";
import RemainingCourses, { courseKey } from "../components/RemainingCourses";
import SemesterPlanner, { getPlanTerms, sameTerm, termLabel } from "../components/SemesterPlanner";
import AddPlanCourseModal from "../components/AddPlanCourseModal";
import { api } from "../api/client";
import frodo from "../assets/frodo4.png";
import './RoadMap.css';

// GET·PUT /api/academic/plans 응답(학기별)을 과목 한 줄씩 펼친 목록 = PUT 요청 courses 모양
const flattenPlans = (data) =>
    data.terms.flatMap((t) => t.courses.map((c) => ({ ...c, year: t.year, term: t.term })));

// 남은 과목을 못 불러왔을 때 빈 카드로 보여주기 위한 값
const EMPTY_REMAINING = { remainingCredits: 0, majorCoursesAvailable: true, courses: [] };

function RoadMap() {
    const [gpa, setGpa] = useState(null);
    const [completedCourses, setCompletedCourses] = useState([]);
    const [remaining, setRemaining] = useState(EMPTY_REMAINING);
    const [showAddCourse, setShowAddCourse] = useState(false);
    const [editingCourse, setEditingCourse] = useState(null); // 수정 창에 띄울 과목
    const [plans, setPlans] = useState([]);
    const [showAddPlan, setShowAddPlan] = useState(false);
    const [error, setError] = useState('');
    const [reloadKey, setReloadKey] = useState(0); // 1 올리면 전부 다시 불러온다

    // 박스 4개에 필요한 API 4개를 한 번에 부른다
    useEffect(() => {
        Promise.all([
            api('/api/academic/gpa'),
            api('/api/academic/completed-courses'),
            api('/api/academic/remaining-courses'),
            api('/api/academic/plans'),
        ]).then(([gpaRes, completedRes, remainingRes, plansRes]) => {
            const failed = [gpaRes, completedRes, remainingRes, plansRes].find((r) => !r.success);
            setError(failed ? failed.error.message : '');
            if (gpaRes.success) setGpa(gpaRes.data);
            if (completedRes.success) setCompletedCourses(completedRes.data);
            setRemaining(remainingRes.success ? remainingRes.data : EMPTY_REMAINING);
            if (plansRes.success) setPlans(flattenPlans(plansRes.data));
        });
    }, [reloadKey]);

    const planTerms = useMemo(() => (gpa ? getPlanTerms(gpa, plans) : []), [gpa, plans]);
    const placedKeys = useMemo(() => new Set(plans.map(courseKey)), [plans]);

    // 이수 과목을 추가하면 곡선(평점·학점)과 남은 과목도 바뀌므로 전부 다시 불러온다
    const handleAddCourse = async (course) => {
        const res = await api('/api/academic/completed-courses', {
            method: 'POST',
            body: JSON.stringify(course),
        });
        if (res.success) {
            setShowAddCourse(false);
            setReloadKey((k) => k + 1);
        } else {
            alert(res.error.message);
        }
    };

    // 수정: 보낸 필드만 바뀐다. 이것도 곡선·남은 과목에 영향이 있어 전부 다시 불러온다
    const handleEditCourse = async (course) => {
        const res = await api(`/api/academic/completed-courses/${editingCourse.id}`, {
            method: 'PATCH',
            body: JSON.stringify(course),
        });
        if (res.success) {
            setEditingCourse(null);
            setReloadKey((k) => k + 1);
        } else {
            alert(res.error.message);
        }
    };

    // 삭제: 확인 없이 바로 지운다
    const handleDeleteCourse = async (course) => {
        const res = await api(`/api/academic/completed-courses/${course.id}`, { method: 'DELETE' });
        if (res.success) {
            setReloadKey((k) => k + 1);
        } else {
            alert(res.error.message);
        }
    };

    // 바뀔 때마다 PUT /api/academic/plans 로 전체 목록을 보내고 응답으로 다시 그린다
    // 화면은 먼저 바꾸고, 저장이 실패하면 원래대로 되돌린다
    const handlePlansChange = async (next) => {
        const prev = plans;
        setPlans(next);
        const res = await api('/api/academic/plans', {
            method: 'PUT',
            body: JSON.stringify({
                courses: next.map((p) => ({
                    year: p.year,
                    term: p.term,
                    courseName: p.courseName,
                    courseCode: p.courseCode ?? null,
                    classification: p.classification ?? null,
                    credits: p.credits,
                })),
            }),
        });
        if (res.success) {
            setPlans(flattenPlans(res.data));
        } else {
            setPlans(prev);
            alert(res.error.message);
        }
    };

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

    if (!gpa) {
        return (
            <div className="layout">
                <Sidebar />
                <div className='page'>{error || '불러오는 중...'}</div>
            </div>
        );
    }

    return (
        <div className="layout">
            <Sidebar />
            <div className='page'>
                <h2 className="page-title roadmap-heading">졸업 로드맵</h2>
                <p className="roadmap-subtitle">지금까지의 나의 학교생활을 한눈에 보고 졸업 계획을 설계하세요.</p>
                {error && <p className="text-danger">{error}</p>}
                <div className="roadmap-curve-wrap">
                    <img className="roadmap-frodo" src={frodo} alt="frodo" />
                    <section className="box roadmap-box">
                        <SemesterCurve gpa={gpa} />
                    </section>
                </div>

                <section className="box completed-box">
                    <CompletedCourses
                        courses={completedCourses}
                        onAdd={() => setShowAddCourse(true)}
                        onEdit={setEditingCourse}
                        onDelete={handleDeleteCourse}
                        importButton={
                            <ImportButton
                                kind="GRADES"
                                label="성적표 불러오기"
                                className="completed-add-btn"
                                onSaved={() => setReloadKey((k) => k + 1)}
                            />
                        }
                    />
                </section>

                <section className="box remaining-box">
                    <RemainingCourses data={remaining} placedKeys={placedKeys} />
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
            {editingCourse && (
                <AddCompletedCourseModal
                    initialCourse={editingCourse}
                    onClose={() => setEditingCourse(null)}
                    onSubmit={handleEditCourse}
                />
            )}
            {showAddPlan && (
                <AddPlanCourseModal terms={planTerms} onClose={() => setShowAddPlan(false)} onSubmit={handleAddPlanCourse} />
            )}
        </div>
    );
}

export default RoadMap;
