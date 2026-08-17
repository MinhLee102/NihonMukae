'use client';
 
import { useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { Mail, Lock, Eye, EyeOff } from 'lucide-react';
import { loginRequest } from '@/lib/api/auth';
import { getAuthErrorMessage } from '@/lib/api/errors';
import { useAuthStore } from '@/store/authStore';
import { useAuthModalStore } from '@/store/authModalStore';
import { GoogleIcon } from './GoogleIcon';
 
export function LoginForm() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
 
  const setAccessToken = useAuthStore((s) => s.setAccessToken);
  const closeModal = useAuthModalStore((s) => s.close);
  const setMode = useAuthModalStore((s) => s.setMode);
  const router = useRouter();
 
  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      const { accessToken } = await loginRequest({ email, password });
      setAccessToken(accessToken);
      closeModal();
      router.push('/');
    } catch (err) {
      setError(getAuthErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  }
 
  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      <div>
        <h2 className="text-2xl font-semibold text-neutral-900">Chào mừng trở lại</h2>
        <p className="mt-1 text-sm text-neutral-500">
          Đăng nhập để tiếp tục hành trình học tiếng Nhật.
        </p>
      </div>
 
      <button
        type="button"
        disabled
        title="Đăng nhập Google — sắp ra mắt"
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
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Mật khẩu"
          autoComplete="current-password"
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
 
      <div className="flex items-center justify-between text-sm">
        <label className="flex items-center gap-2 text-neutral-600">
          <input
            type="checkbox"
            checked={remember}
            onChange={(e) => setRemember(e.target.checked)}
            className="h-4 w-4 rounded border-neutral-300"
          />
          Ghi nhớ đăng nhập
        </label>
        <button type="button" className="text-neutral-500 hover:text-neutral-900">
          Quên mật khẩu?
        </button>
      </div>
 
      <button
        type="submit"
        disabled={isSubmitting}
        className="w-full rounded-lg bg-neutral-900 py-2.5 text-sm font-medium text-white transition hover:bg-neutral-800 disabled:opacity-60"
      >
        {isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
      </button>
 
      <p className="text-center text-sm text-neutral-500">
        Chưa có tài khoản?{' '}
        <button
          type="button"
          onClick={() => setMode('register')}
          className="font-medium text-neutral-900 hover:underline"
        >
          Đăng ký ngay
        </button>
      </p>
    </form>
  );
}