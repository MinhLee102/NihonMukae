import { AxiosError } from 'axios';
import type { ApiErrorBody } from '@/types/auth';
 
const ERROR_MESSAGES: Record<string, string> = {
  invalid_credentials: 'Email hoặc mật khẩu không đúng.',
  invalid_refresh_token: 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.',
  no_refresh_token: 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.',
  account_locked: 'Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.',
  email_already_exists: 'Email này đã được đăng ký.',
};
 
export function getAuthErrorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const body = error.response?.data as ApiErrorBody | undefined;
    if (body?.error && ERROR_MESSAGES[body.error]) {
      return ERROR_MESSAGES[body.error];
    }
    if (error.response?.status === 409) {
      return 'Email này đã được đăng ký.';
    }
    if (error.response?.status === 429) {
      return 'Bạn thử quá nhiều lần. Vui lòng đợi vài phút rồi thử lại.';
    }
    if (!error.response) {
      return 'Không thể kết nối đến máy chủ. Vui lòng thử lại.';
    }
  }
  return 'Có lỗi xảy ra, vui lòng thử lại.';
}
 