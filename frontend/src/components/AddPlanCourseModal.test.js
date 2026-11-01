import { fireEvent, render, screen } from '@testing-library/react';
import AddPlanCourseModal from './AddPlanCourseModal';

const terms = [{ year: 2027, term: 'FIRST' }, { year: 2027, term: 'SECOND' }];

function openModal(props = {}) {
    const onSubmit = jest.fn();
    render(<AddPlanCourseModal terms={terms} onClose={jest.fn()} onSubmit={onSubmit} initialMode="distribution" {...props} />);
    return onSubmit;
}

test('과목명을 공백 없이 검색하고 선택한 학기에 원본 학수번호와 학점으로 추가한다', () => {
    const onSubmit = openModal();
    expect(screen.getByRole('button', { name: '추가하기' })).toBeDisabled();
    fireEvent.change(screen.getByRole('combobox', { name: '학기' }), { target: { value: '2027-SECOND' } });
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: '돈의 발명' } });
    expect(screen.getAllByRole('radio')).toHaveLength(1);
    fireEvent.click(screen.getByRole('radio', { name: /돈의발명:조개에서도토리까지/ }));
    fireEvent.click(screen.getByRole('button', { name: '추가하기' }));
    expect(onSubmit).toHaveBeenCalledWith({
        year: 2027, term: 'SECOND', courseName: '돈의발명:조개에서도토리까지',
        courseCode: 'GED1416', classification: 'GEN_DISTRIBUTION', credits: 3,
    });
});

test('소문자 학수번호 검색과 검색어 변경 후 선택 해제를 지원한다', () => {
    openModal();
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'ged1416' } });
    fireEvent.click(screen.getByRole('radio', { name: /GED1416/ }));
    expect(screen.getByRole('button', { name: '추가하기' })).toBeEnabled();
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: '없는과목이름' } });
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
    expect(screen.getByText(/검색 결과가 없어요/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '추가하기' })).toBeDisabled();
});

test('원본에 학수번호가 없는 실제 과목도 번호를 만들지 않고 추가한다', () => {
    const onSubmit = openModal();
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: '관계의기술' } });
    fireEvent.click(screen.getByRole('radio', { name: /관계의기술.*학수번호 미기재/ }));
    fireEvent.click(screen.getByRole('button', { name: '추가하기' }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
        courseName: '관계의기술', courseCode: null, classification: 'GEN_DISTRIBUTION', credits: 3,
    }));
});

test('중복 과목 오류를 창 안에 보여주고 다른 과목 선택 시 해제한다', () => {
    openModal({ onSubmit: () => '같은 학기에 이미 있는 과목이에요.' });
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'ged1416' } });
    fireEvent.click(screen.getByRole('radio'));
    fireEvent.click(screen.getByRole('button', { name: '추가하기' }));
    expect(screen.getByRole('alert')).toHaveTextContent('같은 학기에 이미 있는 과목이에요.');
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'ged1418' } });
    fireEvent.click(screen.getByRole('radio'));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('직접 입력한 0학점 과목도 기존 방식으로 추가할 수 있다', () => {
    const onSubmit = openModal({ initialMode: 'manual' });
    expect(screen.queryByLabelText('과목코드 (선택)')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('과목명'), { target: { value: ' 졸업논문 ' } });
    fireEvent.change(screen.getByLabelText('학점'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: '추가하기' }));
    expect(onSubmit).toHaveBeenCalledWith({
        year: 2027, term: 'FIRST', courseName: '졸업논문', courseCode: null, classification: null, credits: 0,
    });
    fireEvent.click(screen.getByRole('button', { name: '배분이수 검색' }));
    expect(screen.getByRole('searchbox')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '추가하기' })).toBeDisabled();
});

test('배치할 학기가 없으면 선택한 과목이 있어도 저장하지 않는다', () => {
    const onSubmit = openModal({ terms: [] });
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'ged1416' } });
    fireEvent.click(screen.getByRole('radio'));
    expect(screen.getByRole('button', { name: '추가하기' })).toBeDisabled();
    expect(onSubmit).not.toHaveBeenCalled();
});
