import Link from "next/link";
import { SITE } from "@/lib/site";

/** Footer chung của các trang người học: liên hệ và ghi nguồn dữ liệu (JMdict, Tatoeba). */
export function Footer() {
  return (
    <footer className="mt-12 border-t border-black/5 px-2 pb-6 pt-6 text-xs leading-relaxed text-neutral-500">
      <div className="mx-auto flex max-w-5xl flex-col gap-4 md:flex-row md:justify-between">
        <div className="max-w-md">
          <p className="font-serif text-sm font-semibold text-neutral-800">{SITE.name}</p>
          <p className="mt-1">Dự án cá nhân của {SITE.owner}, học tiếng Nhật cho người Việt.</p>
          <p className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1">
            <span className="text-neutral-400">Liên hệ:</span>
            {SITE.contacts.map((c) => (
              <a
                key={c.label}
                href={c.href}
                target="_blank"
                rel="noopener noreferrer"
                className="text-neutral-700 underline-offset-2 hover:underline"
              >
                {c.label}: {c.text}
              </a>
            ))}
          </p>
        </div>

        <div className="max-w-md">
          <p className="font-medium text-neutral-700">Nguồn dữ liệu</p>
          <p className="mt-1">
            Từ điển dựa trên{" "}
            <a
              href="https://www.edrdg.org/jmdict/j_jmdict.html"
              target="_blank"
              rel="noopener noreferrer"
              className="text-neutral-700 underline-offset-2 hover:underline"
            >
              JMdict
            </a>{" "}
            © EDRDG, giấy phép{" "}
            <a
              href="https://creativecommons.org/licenses/by-sa/4.0/"
              target="_blank"
              rel="noopener noreferrer"
              className="text-neutral-700 underline-offset-2 hover:underline"
            >
              CC BY-SA 4.0
            </a>
            . Câu ví dụ từ{" "}
            <a
              href="https://tatoeba.org"
              target="_blank"
              rel="noopener noreferrer"
              className="text-neutral-700 underline-offset-2 hover:underline"
            >
              Tatoeba
            </a>{" "}
            (CC BY 2.0 FR).{" "}
            <Link href="/credits" className="text-neutral-700 underline-offset-2 hover:underline">
              Chi tiết nguồn và bản quyền
            </Link>
          </p>
        </div>
      </div>
    </footer>
  );
}
