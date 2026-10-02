import type {ResumeSummary} from "./documentsApi";

export function isResumeUsable(resume: ResumeSummary): boolean {
    return resume.usageStatus === 'READY';
}

export function resumeUsageLabel(resume: ResumeSummary): string {
    switch (resume.usageStatus) {
        case 'READY':
            return 'AI 사용 가능'
        case 'PENDING':
            return '사용 불가 · 추출 대기'
        case 'EMPTY_TEXT':
            return '사용 불가 · 텍스트 없음'
        case 'EXTRACTION_FAILED':
            return '사용 불가 · 추출 실패'
        default:
            return '사용 여부 확인 필요'
    }
}

export function resumeUsageMessage(resume: ResumeSummary): string {
    switch (resume.usageStatus) {
        case 'READY':
            return '추출된 본문을 AI 초안과 면접 자료로 사용할 수 있습니다.'
        case 'PENDING':
            return '텍스트 추출이 완료되지 않았습니다. 새로고침으로 상태를 확인하고, 계속 대기 상태이면 다른 PDF를 등록해 주세요.'
        case 'EMPTY_TEXT':
            return 'PDF에서 텍스트를 찾지 못했습니다. 이미지·스캔 PDF는 텍스트를 추출할 수 없으므로, 글자를 선택하거나 복사할 수 있는 PDF를 다시 등록해 주세요.'
        case 'EXTRACTION_FAILED':
            return 'PDF 텍스트 추출 중 오류가 발생했습니다. 다른 PDF로 다시 등록해 주세요.'
        default:
            return '사용 가능 여부를 확인하지 못했습니다. 새로고침 후 다시 확인해 주세요.'
    }
}