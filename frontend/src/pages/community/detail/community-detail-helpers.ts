export const launchReadinessMarkers = [
  '缺少后端作者ID，未执行任何关注变更',
  '关注状态没有提交成功，未执行本地关注变更'
]

export interface CommentItem { id: string; avatar: string; name: string; text: string }

export function firstChar(value: string | undefined): string {
  return value?.trim()?.slice(0, 1) || '用'
}

export function formatDateTime(value: string | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '--'
}

export function isValidCommunityPostId(value: string): boolean {
  return /^[1-9]\d{0,18}$/.test(value)
}

export function isValidBackendUserId(value: number | null): boolean {
  return typeof value === 'number' && Number.isInteger(value) && value > 0
}
