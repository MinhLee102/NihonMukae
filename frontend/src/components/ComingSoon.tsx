import { Construction } from "lucide-react";

/** Placeholder for sections that are linked in the sidebar but not built yet. */
export function ComingSoon({ title }: { title: string }) {
  return (
    <div className="mx-auto mt-16 max-w-md rounded-2xl border border-black/5 bg-white p-10 text-center">
      <Construction size={32} strokeWidth={1.5} className="mx-auto text-neutral-400" />
      <h1 className="mt-4 font-serif text-xl font-semibold text-neutral-900">{title}</h1>
      <p className="mt-2 text-sm text-neutral-500">Chức năng này đang được phát triển, hẹn bạn ở bản sau.</p>
    </div>
  );
}
