export type AvatarGender = 'god' | 'goddess' | string | null | undefined

export const DEFAULT_GOD_AVATAR_URL = '/assets/profile/default-avatar-god.png'
export const DEFAULT_GODDESS_AVATAR_URL = '/assets/profile/default-avatar-goddess.png'
export const DEFAULT_AVATAR_URLS = [DEFAULT_GOD_AVATAR_URL, DEFAULT_GODDESS_AVATAR_URL] as const

export function normalizedAvatarGender(gender: AvatarGender): 'god' | 'goddess' {
  return String(gender || '').trim().toLowerCase() === 'god' ? 'god' : 'goddess'
}

export function defaultAvatarUrl(gender: AvatarGender): string {
  return normalizedAvatarGender(gender) === 'god' ? DEFAULT_GOD_AVATAR_URL : DEFAULT_GODDESS_AVATAR_URL
}

export function isDefaultAvatarUrl(url: unknown): url is typeof DEFAULT_AVATAR_URLS[number] {
  return typeof url === 'string' && DEFAULT_AVATAR_URLS.includes(url as typeof DEFAULT_AVATAR_URLS[number])
}

export function avatarUrlWithGenderFallback(url: unknown, gender: AvatarGender): string {
  return typeof url === 'string' && url.trim() ? url.trim() : defaultAvatarUrl(gender)
}
