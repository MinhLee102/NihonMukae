import type { ReactNode } from "react";
import { Sidebar } from "../../components/Sidebar";
import { Header } from "../../components/Header";
import { AuthModal } from '@/components/auth/AuthModal';
import { AuthInitializer } from '@/components/auth/AuthInitializer';
import { FeedbackDialog } from '@/components/FeedbackDialog';
import { WelcomeDialog } from '@/components/WelcomeDialog';
import { Footer } from '@/components/Footer';

/**
 * Shared shell for every user-facing page (Dictionary, Flashcard, My Shelf,
 * Reading, Listening, JLPT, Shadowing). 
 */
export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-screen bg-[#F5F3EE]">
      <AuthInitializer />
      <Sidebar />
      <div className="relative flex min-w-0 flex-1 flex-col">
        <Header />
        <main className="flex-1 overflow-y-auto px-8 py-8">
          {children}
          <Footer />
        </main>
        {/* Dialogs sit inside the content column so the sidebar stays usable while one is open */}
        <FeedbackDialog />
        <WelcomeDialog />
      </div>
      <AuthModal />
    </div>
  );
}