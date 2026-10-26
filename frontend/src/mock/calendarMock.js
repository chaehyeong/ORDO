const day = (offset) => {
    const d = new Date();
    d.setDate(d.getDate() + offset);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

export const categoryColors = {
    수업: '#39B19D',
    과제: '#0091BC',
    개인일정: '#C03CD5',
};

// time이 null이면 종일 일정
export const mockCalendarEvents = [
    { id: 1, title: '세시 팀플 모임', category: '개인일정', date: day(0),  time: '12:00', subject: '세계와 시민',  location: null },
    { id: 2, title: '과동아리 회의', category: '개인일정', date: day(0),  time: '18:00', subject: null,           location: null },
    { id: 3, title: '에세이 제출', category: '과제',     date: day(1),  time: '23:59', subject: '인간의 가치탐색', location: null },
    { id: 4, title: '과제 마감', category: '과제',     date: day(2),  time: '23:59', subject: '타이포그래피 I', location: null },
    { id: 5, title: '중간 발표 준비', category: '개인일정', date: day(2),  time: '19:00', subject: '세계와 시민',  location: null },
];
