'use client';
 
import { useState, type FormEvent } from 'react';
import { User, Mail, Lock, Eye, EyeOff } from 'lucide-react';
import { registerRequest } from '@/lib/api/auth';
import { getAuthErrorMessage } from '@/lib/api/errors';
import { useAuthModalStore } from '@/store/authModalStore';
import { GoogleIcon } from './GoogleIcon';
 
const MIN_PASSWORD_LENGTH = 8;
 
export function RegisterForm() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
 
  const [submitted, setSubmitted] = useState(false);

  const closeModal = useAuthModalStore((s) => s.close);
  const setMode = useAuthModalStore((s) => s.setMode);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
 
    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`Mật khẩu phải có ít nhất ${MIN_PASSWORD_LENGTH} ký tự.`);
      return;
    }
    if (password !== confirmPassword) {
      setError('Mật khẩu xác nhận không khớp.');
      return;
    }
 
    setIsSubmitting(true);
    try {
      // Tài khoản mới chưa có token: chờ admin duyệt rồi mới đăng nhập được
      await registerRequest({ name, email, password });
      setSubmitted(true);
    } catch (err) {
      setError(getAuthErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  }
 
  if (submitted) {
    return (
      <div className="space-y-4 text-center">
        <h2 className="text-2xl font-semibold text-neutral-900">Đã gửi đăng ký</h2>
        <p className="text-sm leading-relaxed text-neutral-600">
          Tài khoản <span className="font-medium text-neutral-900">{email}</span> đang chờ quản trị viên duyệt. Ứng dụng
          chỉ mở cho người được mời để tránh vi phạm bản quyền đề thi. Bạn sẽ đăng nhập được sau khi được duyệt.
        </p>
        <div className="flex justify-center gap-2 pt-2">
          <button
            type="button"
            onClick={() => setMode('login')}
            className="rounded-lg border border-neutral-200 px-5 py-2 text-sm text-neutral-600 hover:bg-neutral-50"
          >
            Về đăng nhập
          </button>
          <button
            type="button"
            onClick={closeModal}
            className="rounded-lg bg-neutral-900 px-5 py-2 text-sm font-medium text-white"
          >
            Đóng
          </button>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      <div>
        <h2 className="text-2xl font-semibold text-neutral-900">Tạo tài khoản</h2>
        <p className="mt-1 text-sm text-neutral-500">
          Miễn phí. Tài khoản mới cần được quản trị viên duyệt trước khi đăng nhập.
        </p>
      </div>
 
      <button
        type="button"
        disabled
        title="Đăng ký với Google — sắp ra mắt"
        className="flex w-full cursor-not-allowed items-center justify-center gap-2 rounded-lg border border-neutral-200 bg-neutral-50 py-2.5 text-sm font-medium text-neutral-400"
      >
        <GoogleIcon />
        Tiếp tục với Google
      </button>
 
      <div className="flex items-center gap-3">
        <div className="h-px flex-1 bg-neutral-200" />
        <span className="text-xs uppercase tracking-wide text-neutral-400">Hoặc</span>
        <div className="h-px flex-1 bg-neutral-200" />
      </div>
 
      {error && (
        <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-600">
          {error}
        </p>
      )}
 
      <div className="relative">
        <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
        <input
          type="text"
          required
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Họ và tên"
          autoComplete="name"
          className="w-full rounded-lg border border-neutral-200 bg-neutral-50 py-2.5 pl-10 pr-3 text-sm text-neutral-900 outline-none focus:border-neutral-400 focus:bg-white"
        />
      </div>
 
      <div className="relative">
        <Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
        <input
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="Email"
          autoComplete="email"
          className="w-full rounded-lg border border-neutral-200 bg-neutral-50 py-2.5 pl-10 pr-3 text-sm text-neutral-900 outline-none focus:border-neutral-400 focus:bg-white"
        />
      </div>
 
      <div className="relative">
        <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
        <input
          type={showPassword ? 'text' : 'password'}
          required
          minLength={MIN_PASSWORD_LENGTH}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Mật khẩu"
          autoComplete="new-password"
          className="w-full rounded-lg border border-neutral-200 bg-neutral-50 py-2.5 pl-10 pr-10 text-sm text-neutral-900 outline-none focus:border-neutral-400 focus:bg-white"
        />
        <button
          type="button"
          onClick={() => setShowPassword((v) => !v)}
          aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-neutral-600"
        >
          {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
      </div>
 
      <div className="relative">
        <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
        <input
          type={showConfirmPassword ? 'text' : 'password'}
          required
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          placeholder="Xác nhận mật khẩu"
          autoComplete="new-password"
          className="w-full rounded-lg border border-neutral-200 bg-neutral-50 py-2.5 pl-10 pr-10 text-sm text-neutral-900 outline-none focus:border-neutral-400 focus:bg-white"
        />
        <button
          type="button"
          onClick={() => setShowConfirmPassword((v) => !v)}
          aria-label={showConfirmPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-neutral-600"
        >
          {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
      </div>
 
      <button
        type="submit"
        disabled={isSubmitting}
        className="w-full rounded-lg bg-neutral-900 py-2.5 text-sm font-medium text-white transition hover:bg-neutral-800 disabled:opacity-60"
      >
        {isSubmitting ? 'Đang tạo tài khoản...' : 'Tạo tài khoản'}
      </button>
 
      <p className="text-center text-sm text-neutral-500">
        Đã có tài khoản?{' '}
        <button
          type="button"
          onClick={() => setMode('login')}
          className="font-medium text-neutral-900 hover:underline"
        >
          Đăng nhập
        </button>
      </p>
    </form>
  );
}