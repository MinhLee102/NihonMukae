import Link from "next/link";
import { SITE } from "@/lib/site";

const link = "text-neutral-600 underline-offset-2 hover:underline";

/** Footer gọn một dòng: liên hệ và ghi nguồn dữ liệu. Nằm dưới cùng vùng cuộn nên chỉ thấy khi kéo hết trang. */
export function Footer() {
  return (
    <footer className="mt-8 border-t border-black/5 py-3 text-[11px] leading-relaxed text-neutral-400">
      <p className="mx-auto flex max-w-5xl flex-wrap items-center justify-center gap-x-3 gap-y-1 text-center">
        <span>
          {SITE.name} · dự án cá nhân của {SITE.owner}
        </span>
        {SITE.contacts.map((c) => (
          <a key={c.label} href={c.href} target="_blank" rel="noopener noreferrer" className={link}>
            {c.label}: {c.text}
          </a>
        ))}
        <span>
          <a href="https://www.edrdg.org/jmdict/j_jmdict.html" target="_blank" rel="noopener noreferrer" className={link}>
            JMdict
          </a>{" "}
          (CC BY-SA 4.0) ·{" "}
          <a href="https://tatoeba.org" target="_blank" rel="noopener noreferrer" className={link}>
            Tatoeba
          </a>{" "}
          (CC BY 2.0 FR)
        </span>
        <Link href="/credits" className={link}>
          Nguồn dữ liệu và bản quyền
        </Link>
      </p>
    </footer>
  );
}
