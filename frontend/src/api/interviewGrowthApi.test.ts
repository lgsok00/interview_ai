import {afterEach, describe, expect, it, vi} from 'vitest';
import {getInterviewGrowthAnalysis} from './interviewGrowthApi';

const {apiRequestMock} = vi.hoisted(() => ({apiRequestMock: vi.fn()}));

vi.mock('./client', () => ({apiRequest: apiRequestMock}));

describe('interviewGrowthApi', () => {
    afterEach(() => apiRequestMock.mockReset());

    it('기간만 지정해 인증된 성장 분석 요청을 보내고 응답을 반환한다', async () => {
        const response = {
            period: {from: '2026-07-02', to: '2026-09-29'},
            summary: {
                sessionCount: 2,
                evaluatedAnswerCount: 5,
                excludedSessionCount: 1,
                averageScore: 82.4,
                starScore: 80,
                logicScore: 84,
                jobFitScore: 83,
            },
            trends: [],
            strengths: [],
            weaknesses: [],
            learningRoadmap: [],
            insufficientData: false,
            minimumEvaluatedAnswerCount: 3,
        };
        apiRequestMock.mockResolvedValue(response);

        await expect(getInterviewGrowthAnalysis({
            from: '2026-07-02',
            to: '2026-09-29',
        })).resolves.toBe(response);

        expect(apiRequestMock).toHaveBeenCalledWith(
            '/api/interview-growth-analysis?from=2026-07-02&to=2026-09-29',
            {authenticated: true},
        );
    });

    it('선택한 필터를 인코딩해 query parameter로 전달한다', async () => {
        apiRequestMock.mockResolvedValue({});

        await getInterviewGrowthAnalysis({
            from: '2026-01-01',
            to: '2026-03-31',
            jobRole: '백엔드 개발',
            companyId: 12,
            jobPostingId: 34,
        });

        expect(apiRequestMock).toHaveBeenCalledWith(
            '/api/interview-growth-analysis?from=2026-01-01&to=2026-03-31&jobRole=%EB%B0%B1%EC%97%94%EB%93%9C+%EA%B0%9C%EB%B0%9C&companyId=12&jobPostingId=34',
            {authenticated: true},
        );
    });

    it('빈 직무와 설정되지 않은 ID 필터는 요청에서 제외한다', async () => {
        apiRequestMock.mockResolvedValue({});

        await getInterviewGrowthAnalysis({
            from: '2026-01-01',
            to: '2026-01-31',
            jobRole: '   ',
        });

        expect(apiRequestMock).toHaveBeenCalledWith(
            '/api/interview-growth-analysis?from=2026-01-01&to=2026-01-31',
            {authenticated: true},
        );
    });

    it('API 오류를 호출자에게 전달한다', async () => {
        const error = new Error('INTERVIEW_ANALYSIS_INVALID_DATE_RANGE');
        apiRequestMock.mockRejectedValue(error);

        await expect(getInterviewGrowthAnalysis({
            from: '2026-09-30',
            to: '2026-09-01',
        })).rejects.toBe(error);
    });
});
