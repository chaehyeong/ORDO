export const mockDegree = {
    credits: {
        totalRequired: 130,
        categories: [
            { key: 'majorRequired', label: '전공필수', earned: 6, required: 21, color: '#079382' },
            { key: 'majorBasic',    label: '전공기초', earned: 3, required: 12, color: '#42B59E' },
            { key: 'majorElective', label: '전공선택', earned: 3, required: 30, color: '#76A2F5' },
            { key: 'generalReq',    label: '필수교양', earned: 3, required: 17, color: '#5D6CB2' },
            { key: 'distribution',  label: '배분이수', earned: 3, required: 15, color: '#C8DC5D' },
            { key: 'free',          label: '자유이수', earned: 0, required: 35, color: '#F5D92A' },
        ],
    },
    gpa: {
        average: 3.68,
        max: 4.3,
    },
    /*
    multiMajor: {
        name: '산업디자인학과',
        type: '복수전공',
        earned: 9,
        required: 36,
        breakdown: [
            { key: 'majorBasic',    label: '전공기초', earned: 3, required: 9 },
            { key: 'majorRequired', label: '전공필수', earned: 3, required: 12 },
            { key: 'majorElective', label: '전공선택', earned: 3, required: 15 },
        ],
    },
    */
    multiMajor: null,
    microDegree: {
        name: 'UX 디자인 마이크로디그리',
        type: '마이크로디그리',
        earned: 6,
        required: 12,
        period: '2026. 2학기 ~ 2027. 2학기',
    },
};
