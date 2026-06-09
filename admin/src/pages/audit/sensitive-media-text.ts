const sensitiveUploadPathPattern = /\/uploads\/(?:chat-voice|chat-image|video-identity|report-evidence|evidence\/after-sales)\/[A-Za-z0-9._~/%-]+/g
const sensitiveMediaPlaceholder = '敏感媒体已隐藏，请使用授权查看'

export function maskSensitiveMediaText(value?: string | null): string {
  if (!value) return ''
  return value.replace(sensitiveUploadPathPattern, sensitiveMediaPlaceholder)
}
