'use client';
 
import { useEffect } from 'react';
import { X } from 'lucide-react';
import { useAuthModalStore } from '@/store/authModalStore';
import { LoginForm } from './LoginForm';
import { RegisterForm } from './RegisterForm';
export function AuthModal() {
  const isOpen = useAuthModalStore((s) => s.isOpen);
  const mode = useAuthModalStore((s) => s.mode);
  const close = useAuthModalStore((s) => s.close);
  const setMode = useAuthModalStore((s) => s.setMode);
 
  useEffect(() => {
    if (!isOpen) return;
 
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') close();
    }
    document.addEventListener('keydown', onKeyDown);
 
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
 
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [isOpen, close]);
 
  if (!isOpen) return null;
 
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={mode === 'login' ? 'Đăng nhập' : 'Đăng ký'}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4"
      onClick={close}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="grid w-full max-w-3xl overflow-hidden rounded-2xl bg-white shadow-2xl md:grid-cols-2"
      >
        {/* Left brand panel — hidden on small screens */}
        <div className="relative hidden flex-col justify-between bg-neutral-900 p-8 text-white md:flex">
          <div>
            <p className="text-2xl font-semibold tracking-wide">日本語</p>
            <p className="mt-1 text-xs uppercase tracking-[0.2em] text-neutral-400">
              Japanese Learning
            </p>
          </div>
          <div>
            <p className="text-lg font-medium">「継続は力なり」</p>
            <p className="mt-2 text-sm text-neutral-400">
              Kiên trì chính là sức mạnh.
              <br />
              Bắt đầu hành trình của bạn hôm nay.
            </p>
          </div>
        </div>
 
        {/* Right form panel */}
        <div className="relative p-8">
          <button
            onClick={close}
            aria-label="Đóng"
            className="absolute right-4 top-4 text-neutral-400 hover:text-neutral-700"
          >
            <X className="h-5 w-5" />
          </button>
 
          <div className="mb-6 flex rounded-full bg-neutral-100 p-1 text-sm font-medium">
            <button
              type="button"
              onClick={() => setMode('login')}
              className={`flex-1 rounded-full py-2 transition ${
                mode === 'login'
                  ? 'bg-white text-neutral-900 shadow-sm'
                  : 'text-neutral-500'
              }`}
            >
              Đăng nhập
            </button>
            <button
              type="button"
              onClick={() => setMode('register')}
              className={`flex-1 rounded-full py-2 transition ${
                mode === 'register'
                  ? 'bg-white text-neutral-900 shadow-sm'
                  : 'text-neutral-500'
              }`}
            >
              Đăng ký
            </button>
          </div>
 
          {mode === 'login' ? <LoginForm /> : <RegisterForm />}
        </div>
      </div>
    </div>
  );
}