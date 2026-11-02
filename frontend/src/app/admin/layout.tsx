import type { ReactNode } from "react";
import { AuthInitializer } from "@/components/auth/AuthInitializer";
import { AuthModal } from "@/components/auth/AuthModal";
import { AdminShell } from "@/components/admin/AdminShell";

/** Separate shell for the admin console; it does not use the learner sidebar. */
export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <div className="h-screen bg-[#F5F3EE]">
      <AuthInitializer />
      <AdminShell>{children}</AdminShell>
      <AuthModal />
    </div>
  );
}
