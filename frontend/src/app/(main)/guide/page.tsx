import Link from "next/link";
import type { ReactNode } from "react";

const SECTIONS: { id: string; title: string; body: ReactNode }[] = [
  {
    id: "start",
    title: "Bắt đầu",
    body: (
      <>
        <p>
          Bạn có thể <b>tra từ điển ngay mà không cần đăng nhập</b>. Muốn dùng Flashcard, My Shelf và luyện đề JLPT thì bấm
          Đăng nhập ở góc dưới bên trái để tạo tài khoản (chỉ cần email và mật khẩu từ 8 ký tự).
        </p>
        <p>Các mục ở thanh bên trái: Từ điển, Flashcard, My Shelf, JLPT. Nút Góp ý nằm ngay phía trên tên của bạn.</p>
      </>
    ),
  },
  {
    id: "dictionary",
    title: "Từ điển",
    body: (
      <ul>
        <li>Gõ chữ Nhật, hiragana hoặc nghĩa tiếng Việt vào ô tìm kiếm.</li>
        <li>Bấm vào một kết quả để xem nghĩa, ví dụ và cách đọc.</li>
        <li>Bấm nút thêm vào Flashcard để đưa từ vào bộ thẻ của bạn.</li>
        <li>Lịch sử tìm kiếm nằm ngay trang chủ, có thể xóa từng mục hoặc xóa hết.</li>
      </ul>
    ),
  },
  {
    id: "flashcard",
    title: "Flashcard",
    body: (
      <ul>
        <li>Tạo một bộ thẻ, rồi thêm thẻ bằng tay hoặc từ kết quả tra từ.</li>
        <li>
          Khi học, sau mỗi thẻ hãy chọn <b>Khó</b>, <b>Vừa</b> hoặc <b>Dễ</b>. Hệ thống dùng thuật toán lặp lại ngắt quãng (SM-2) để
          hẹn lại thẻ: nhớ tốt thì lâu mới gặp lại, quên thì gặp lại ngay hôm sau.
        </li>
        <li>Mỗi ngày chỉ cần học các thẻ đến hạn. Học đều đặn sẽ giữ chuỗi ngày học liên tiếp.</li>
      </ul>
    ),
  },
  {
    id: "shelf",
    title: "My Shelf",
    body: (
      <ul>
        <li>Dán một bài đọc tiếng Nhật vào, hệ thống tự gắn furigana cho chữ Hán.</li>
        <li>Bấm vào một từ để tra nghĩa và thêm vào Flashcard ngay trong bài.</li>
        <li>
          Từ trang kết quả luyện đề, bạn có thể <b>lưu câu sai hoặc cả bài vào My Shelf</b> để đọc lại có furigana. Phần giải
          thích tiếng Việt không được lưu, hãy xem ở trang kết quả.
        </li>
      </ul>
    ),
  },
  {
    id: "jlpt",
    title: "Luyện đề JLPT",
    body: (
      <>
        <p>Vào mục JLPT, chọn level (N5 đến N1) rồi chọn cách luyện:</p>
        <ul>
          <li>
            <b>Luyện theo dạng bài:</b> chọn một hoặc nhiều dạng (đọc Hán tự, ngữ pháp, đọc hiểu...), số câu, có tính giờ hay
            không, và hiện đáp án ngay sau mỗi câu hay chỉ khi nộp bài. Phù hợp để ôn phần mình yếu.
          </li>
          <li>
            <b>Thi thử:</b> làm một đề thật theo năm, hoặc đề ngẫu nhiên, có bấm giờ từng khối như kỳ thi. Hết giờ khối nào thì
            khối đó bị khóa, không quay lại được.
          </li>
          <li>
            <b>Nghe cả bộ đề:</b> làm trọn phần nghe của một đề cùng file audio, có thể tua và nghe lại.
          </li>
        </ul>
        <p>
          Khi làm bài: phím <kbd>1</kbd> đến <kbd>4</kbd> chọn đáp án, mũi tên trái và phải chuyển câu, nút Đánh dấu để quay lại câu
          còn phân vân. Bảng bên phải cho thấy câu nào đã làm.
        </p>
      </>
    ),
  },
  {
    id: "listening",
    title: "Phần nghe",
    body: (
      <ul>
        <li>
          Khi <b>thi thử</b>, audio phát tự động <b>một lần</b>, không tạm dừng hay tua, giống kỳ thi thật. Nếu tải lại trang,
          audio tiếp tục từ chỗ đang nghe.
        </li>
        <li>Khi luyện, bạn được nghe lại và tua tự do.</li>
        <li>Sau khi nộp bài, ở màn xem lại từng câu có nút <b>Xem transcript</b> (lời thoại) nếu câu đó đã có.</li>
        <li>
          Câu chưa có transcript thì có nút <b>Đóng góp transcript</b>. Bạn gõ lại lời thoại, quản trị viên kiểm tra rồi mới hiển thị
          cho mọi người.
        </li>
      </ul>
    ),
  },
  {
    id: "result",
    title: "Xem kết quả",
    body: (
      <ul>
        <li>Tab <b>Tổng kết</b>: số câu đúng, sai, bỏ trống, điểm ước tính (với thi thử) và đạt hay chưa đạt.</li>
        <li>Biểu đồ cho thấy bạn <b>hổng phần nào nhất</b>, theo phần thi và theo từng dạng bài. Bấm Xem câu sai để nhảy tới đúng câu.</li>
        <li>Tab <b>Xem lại từng câu</b>: đáp án đúng, đáp án bạn chọn và giải thích.</li>
        <li>Điểm chỉ là ước tính, kỳ thi thật dùng điểm quy đổi nên có thể khác một chút.</li>
        <li>Bấm Làm lại để luyện lại cùng cấu hình.</li>
      </ul>
    ),
  },
  {
    id: "feedback",
    title: "Báo lỗi và góp ý",
    body: (
      <ul>
        <li>Thấy đáp án sai, nội dung lỗi hay muốn đề xuất gì, bấm <b>Góp ý</b> ở thanh bên trái.</li>
        <li>Khi đang làm bài hoặc xem lại, nút <b>Báo lỗi</b> trên mỗi câu sẽ tự gửi kèm câu hỏi đó nên rất nhanh và chính xác.</li>
        <li>Bạn cần đăng nhập để gửi góp ý.</li>
      </ul>
    ),
  },
  {
    id: "soon",
    title: "Sắp có",
    body: (
      <p>
        Các mục Reading, Listening riêng, Shadowing đang được phát triển và hiện hiển thị &ldquo;Sắp ra mắt&rdquo;. Hiện tại bạn luyện đọc hiểu
        và nghe hiểu ngay trong mục JLPT.
      </p>
    ),
  },
];

export default function GuidePage() {
  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="font-serif text-2xl font-semibold text-neutral-900">Hướng dẫn sử dụng</h1>
      <p className="mt-2 text-sm text-neutral-500">
        Trang web giúp bạn tra từ, học từ vựng bằng flashcard, đọc bài tiếng Nhật có furigana và luyện đề JLPT. Chọn một mục để xem nhanh.
      </p>

      <nav className="mt-5 flex flex-wrap gap-2" aria-label="Mục lục">
        {SECTIONS.map((s) => (
          <a
            key={s.id}
            href={`#${s.id}`}
            className="rounded-full border border-black/10 bg-white px-3.5 py-1.5 text-xs text-neutral-600 transition hover:border-black/30"
          >
            {s.title}
          </a>
        ))}
      </nav>

      <div className="mt-8 space-y-5">
        {SECTIONS.map((s) => (
          <section key={s.id} id={s.id} className="scroll-mt-4 rounded-2xl border border-black/5 bg-white p-6">
            <h2 className="font-serif text-lg font-semibold text-neutral-900">{s.title}</h2>
            <div className="mt-3 space-y-3 text-sm leading-7 text-neutral-700 [&_kbd]:rounded [&_kbd]:border [&_kbd]:border-black/15 [&_kbd]:bg-[#F5F3EE] [&_kbd]:px-1.5 [&_kbd]:text-xs [&_li]:ml-5 [&_li]:list-disc [&_li]:pl-1">
              {s.body}
            </div>
          </section>
        ))}
      </div>

      <p className="mt-8 text-center text-sm text-neutral-400">
        Còn thắc mắc? Dùng nút Góp ý ở thanh bên trái để nhắn cho chúng mình, hoặc{" "}
        <Link href="/" className="underline">
          quay lại tra từ
        </Link>
        .
      </p>
    </div>
  );
}
