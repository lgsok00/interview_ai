import {describe, expect, it} from 'vitest'
import type {ResumeSummary, ResumeUsageStatus} from './documentsApi'
import {isResumeUsable, resumeUsageLabel, resumeUsageMessage} from './resumeUsage'

function resume(usageStatus: ResumeUsageStatus): ResumeSummary {
    return {
        id: 10,
        title: '이력서',
        originalFilename: 'resume.pdf',
        fileSize: 1024,
        extractionStatus: 'COMPLETED',
        usageStatus,
        representative: false,
        createdAt: '2026-10-02T10:00:00',
        updatedAt: '2026-10-02T10:00:00',
    }
}

describe('resumeUsage', () => {
    it('실제 본문 사용 가능 판정이 READY일 때만 AI 자료로 허용한다', () => {
        const ready = resume('READY')

        expect(isResumeUsable(ready)).toBe(true)
        expect(resumeUsageLabel(ready)).toBe('AI 사용 가능')
        expect(resumeUsageMessage(ready)).toContain('AI 초안과 면접 자료')
    })

    it.each<ResumeUsageStatus>(['PENDING', 'EMPTY_TEXT', 'EXTRACTION_FAILED'])(
        '추출 상태가 COMPLETED여도 사용 판정 %s이면 차단한다',
        (status) => {
            expect(isResumeUsable(resume(status))).toBe(false)
        },
    )

    it('추출 대기는 상태 확인과 계속 대기 시 재등록을 안내한다', () => {
        const pending = resume('PENDING')

        expect(resumeUsageLabel(pending)).toContain('추출 대기')
        expect(resumeUsageMessage(pending)).toContain('새로고침')
        expect(resumeUsageMessage(pending)).toContain('다른 PDF')
    })

    it('본문 없음은 이미지·스캔 PDF의 한계와 텍스트 PDF 재등록을 안내한다', () => {
        const empty = resume('EMPTY_TEXT')

        expect(resumeUsageLabel(empty)).toContain('텍스트 없음')
        expect(resumeUsageMessage(empty)).toContain('이미지·스캔 PDF')
        expect(resumeUsageMessage(empty)).toContain('선택하거나 복사')
        expect(resumeUsageMessage(empty)).toContain('다시 등록')
    })

    it('일반 추출 오류는 텍스트 없음과 구분해 안내한다', () => {
        const failed = resume('EXTRACTION_FAILED')

        expect(resumeUsageLabel(failed)).toContain('추출 실패')
        expect(resumeUsageMessage(failed)).toContain('오류가 발생')
        expect(resumeUsageMessage(failed)).not.toContain('이미지·스캔')
    })

    it.each([undefined, null, 'FUTURE_STATUS'])(
        '사용 상태가 없거나 알 수 없는 응답 %s는 허용하지 않는다',
        (usageStatus) => {
            // 이전 서버 및 예상하지 못한 실제 JSON 응답을 재현한다.
            const unknown = {...resume('READY'), usageStatus} as unknown as ResumeSummary

            expect(isResumeUsable(unknown)).toBe(false)
            expect(resumeUsageLabel(unknown)).toBe('사용 여부 확인 필요')
            expect(resumeUsageMessage(unknown)).toContain('새로고침')
        },
    )

    it('대표 표시만으로 사용 불가 판정을 바꾸지 않는다', () => {
        const representative = {...resume('EMPTY_TEXT'), representative: true}

        expect(isResumeUsable(representative)).toBe(false)
        expect(resumeUsageLabel(representative)).toContain('텍스트 없음')
    })
})
