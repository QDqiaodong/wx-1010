import type { AxiosError } from 'axios'

/**
 * 提取后端返回的业务错误信息。
 * 后端 GlobalExceptionHandler 统一返回 { error: string }。
 */
export function extractApiError(e: unknown, fallback = '操作失败'): string {
  const err = e as AxiosError<{ error?: string; message?: string }>
  const data = err?.response?.data
  if (data?.error) return data.error
  if (data?.message) return data.message
  if (err?.message && err.message !== 'Network Error') return err.message
  return fallback
}

/** 是否为 HTTP 409 冲突（如并发下“已被领用”） */
export function isConflict(e: unknown): boolean {
  return (e as AxiosError)?.response?.status === 409
}
