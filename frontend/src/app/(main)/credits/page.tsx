import { DATA_SOURCES, SITE } from "@/lib/site";

export const metadata = { title: "Nguồn dữ liệu và bản quyền" };

export default function CreditsPage() {
  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="font-serif text-2xl font-semibold text-neutral-900">Nguồn dữ liệu và bản quyền</h1>
      <p className="mt-2 text-sm text-neutral-500">
        Từ điển của {SITE.name} được dựng từ các bộ dữ liệu mở dưới đây. Cảm ơn những người đã tạo và chia sẻ chúng.
      </p>

      <ul className="mt-6 flex flex-col gap-4">
        {DATA_SOURCES.map((s) => (
          <li key={s.name} className="rounded-2xl border border-black/5 bg-white p-5">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <a
                href={s.url}
                target="_blank"
                rel="noopener noreferrer"
                className="font-serif text-lg font-semibold text-neutral-900 hover:underline"
              >
                {s.name}
              </a>
              <a
                href={s.licenseUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded-full bg-neutral-100 px-3 py-1 text-xs text-neutral-600 hover:bg-neutral-200"
              >
                {s.license}
              </a>
            </div>
            <p className="mt-1 text-xs text-neutral-400">{s.owner}</p>
            <p className="mt-3 text-sm leading-relaxed text-neutral-700">{s.usage}</p>
          </li>
        ))}
      </ul>

      <div className="mt-6 rounded-2xl border border-black/5 bg-white p-5 text-sm leading-relaxed text-neutral-700">
        <p className="font-medium text-neutral-900">Đóng góp của người dùng</p>
        <p className="mt-1">
          Nghĩa, câu ví dụ và ghi chú do người dùng gửi chỉ hiện sau khi quản trị viên duyệt, kèm tên người đóng góp.
          Khi gửi đóng góp, bạn đồng ý để nội dung đó được chia sẻ theo giấy phép CC BY-SA 4.0 cùng dữ liệu từ điển.
        </p>
      </div>

      <div className="mt-6 rounded-2xl border border-black/5 bg-white p-5 text-sm leading-relaxed text-neutral-700">
        <p className="font-medium text-neutral-900">Liên hệ</p>
        <ul className="mt-1">
          {SITE.contacts.map((c) => (
            <li key={c.label}>
              {c.label}:{" "}
              <a href={c.href} target="_blank" rel="noopener noreferrer" className="underline-offset-2 hover:underline">
                {c.text}
              </a>
            </li>
          ))}
        </ul>
        <p className="mt-2 text-xs text-neutral-400">
          Nếu bạn là chủ sở hữu nội dung và muốn gỡ hoặc chỉnh sửa phần hiển thị ở đây, hãy liên hệ qua thông tin trên.
        </p>
      </div>
    </div>
  );
}
