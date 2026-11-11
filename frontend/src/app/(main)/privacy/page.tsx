import { SITE } from "@/lib/site";

export const metadata = { title: "Chính sách quyền riêng tư" };

const UPDATED = "11/11/2026";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mt-6 rounded-2xl border border-black/5 bg-white p-5">
      <h2 className="font-serif text-lg font-semibold text-neutral-900">{title}</h2>
      <div className="mt-2 space-y-2 text-sm leading-relaxed text-neutral-700">{children}</div>
    </section>
  );
}

export default function PrivacyPage() {
  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="font-serif text-2xl font-semibold text-neutral-900">Chính sách quyền riêng tư</h1>
      <p className="mt-2 text-sm text-neutral-500">
        {SITE.name} là dự án cá nhân của {SITE.owner}, phục vụ việc học tiếng Nhật. Trang này nói rõ chúng tôi lưu gì về bạn, vì
        sao và bạn kiểm soát nó thế nào. Cập nhật lần cuối: {UPDATED}.
      </p>

      <Section title="Chúng tôi lưu gì">
        <ul className="list-disc space-y-1.5 pl-5">
          <li>Tài khoản: email, tên hiển thị, mật khẩu (chỉ lưu dạng băm, không đọc lại được).</li>
          <li>Dữ liệu học của bạn: flashcard và tiến độ ôn, bài trong My Shelf, lịch sử tra từ, kết quả luyện thi JLPT.</li>
          <li>Shadowing: link YouTube, phụ đề bạn dán và các câu bạn đánh dấu. Không lưu video, âm thanh hay bản thu của bạn.</li>
          <li>Kaiwa: chỉ lưu tóm tắt mỗi buổi (chủ đề, nhận xét, số lượt, thời gian). Không lưu nội dung từng câu nói.</li>
          <li>Đóng góp từ điển của bạn (kèm tên hiển thị khi được duyệt) và bản ghi bạn đã đồng ý các điều khoản dùng Kaiwa.</li>
        </ul>
        <p>Chúng tôi dùng dữ liệu này để chạy các tính năng trên, không bán và không dùng để quảng cáo.</p>
      </Section>

      <Section title="Kaiwa và Google Gemini">
        <p>
          Khi bạn trò chuyện trong Kaiwa, nội dung bạn gõ hoặc nói (đã đổi thành chữ) được gửi từ trình duyệt của bạn thẳng tới Google
          Gemini bằng key của chính bạn. Máy chủ của chúng tôi không nhận nội dung này và không lưu key; key chỉ nằm trong trình duyệt
          của bạn. Với key miễn phí, Google có thể dùng nội dung để cải thiện sản phẩm và người của Google có thể xem lại, nên đừng nói
          hay gõ thông tin cá nhân. Dữ liệu được xử lý ở máy chủ nước ngoài.
        </p>
        <p>
          Nếu bạn dùng micro, Chrome hoặc Edge gửi âm thanh tới dịch vụ nhận dạng giọng nói của Google hoặc Microsoft để đổi thành chữ.
          Chúng tôi không nhận âm thanh đó. Bạn có thể chỉ gõ chữ.
        </p>
        <p>Chúng tôi chỉ cho dùng Kaiwa sau khi bạn tích đồng ý, và bạn rút lại được bất cứ lúc nào ngay trên trang Kaiwa.</p>
      </Section>

      <Section title="Bên thứ ba khác">
        <ul className="list-disc space-y-1.5 pl-5">
          <li>
            YouTube: trang Shadowing nhúng trình phát của YouTube. Khi xem, YouTube có thể đặt cookie và thu thập dữ liệu theo chính
            sách của Google.
          </li>
          <li>Hạ tầng: giao diện web chạy trên Vercel, máy chủ và cơ sở dữ liệu chạy trên máy ảo Azure.</li>
          <li>Từ điển dựng từ dữ liệu mở (xem trang Nguồn dữ liệu và bản quyền); các bộ dữ liệu này không nhận dữ liệu của bạn.</li>
        </ul>
      </Section>

      <Section title="Quyền của bạn">
        <p>
          Bạn có quyền được biết, xem, sửa, xoá dữ liệu của mình, rút lại sự đồng ý và phản đối việc xử lý, theo Nghị định
          13/2023/NĐ-CP về bảo vệ dữ liệu cá nhân. Bạn tự xoá được từng buổi Kaiwa, bài Shadowing, thẻ và bài My Shelf trong ứng dụng.
          Để xoá tài khoản hoặc xem toàn bộ dữ liệu của mình, hãy liên hệ chúng tôi:
        </p>
        <p>
          {SITE.contacts.map((c) => (
            <a key={c.label} href={c.href} target="_blank" rel="noopener noreferrer" className="underline">
              {c.label}: {c.text}
            </a>
          ))}
        </p>
      </Section>

      <Section title="Trẻ em">
        <p>Tính năng Kaiwa dành cho người từ đủ 16 tuổi, hoặc người dưới 16 tuổi có cha mẹ hoặc người giám hộ đồng ý.</p>
      </Section>
    </div>
  );
}
