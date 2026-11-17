"use client";

import { useState } from "react";
import Link from "next/link";
import { KeyRound, ShieldCheck } from "lucide-react";
import { acceptConsent } from "@/lib/api/consent";
import { getApiErrorMessage } from "@/lib/api/flashcard";

function Check({ checked, onChange, children }: { checked: boolean; onChange: (v: boolean) => void; children: React.ReactNode }) {
  return (
    <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-black/10 p-3 text-sm leading-5 text-neutral-700 has-[:checked]:border-neutral-900 has-[:checked]:bg-[#F5F3EE]">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="mt-0.5 h-4 w-4 shrink-0 accent-neutral-900"
      />
      <span>{children}</span>
    </label>
  );
}

/** Màn đồng ý hiện trước khi dùng Kaiwa lần đầu (và khi nội dung đồng ý đổi phiên bản). */
export function KaiwaConsent({ version, onAccepted }: { version: number; onAccepted: () => void }) {
  const [dataOk, setDataOk] = useState(false);
  const [ageOk, setAgeOk] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const accept = async () => {
    setSaving(true);
    setError(null);
    try {
      await acceptConsent("kaiwa", version);
      onAccepted();
    } catch (err) {
      setError(getApiErrorMessage(err));
      setSaving(false);
    }
  };

  return (
    <div className="rounded-2xl border border-black/5 bg-white p-6">
      <p className="flex items-center gap-2 text-sm font-semibold text-neutral-900">
        <ShieldCheck size={16} /> Trước khi dùng Kaiwa
      </p>
      <p className="mt-2 text-sm leading-6 text-neutral-600">
        Kaiwa cho bạn trò chuyện với giáo viên AI (Google Gemini). Để làm được việc đó, nội dung bạn nói hoặc gõ phải được gửi
        tới Google. Hãy đọc kỹ điều này đi đâu:
      </p>

      <ul className="mt-4 list-disc space-y-2 pl-5 text-sm leading-6 text-neutral-700">
        <li>
          <strong>Câu bạn gõ hoặc nói</strong> (đã chuyển thành chữ) và câu trả lời của AI được gửi{" "}
          <strong>từ trình duyệt của bạn thẳng tới Google Gemini</strong>, bằng key của bạn. Máy chủ Nihon Mukae không nhận nội
          dung này.
        </li>
        <li>
          <strong>Nếu dùng micro</strong>, Chrome hoặc Edge gửi âm thanh của bạn tới dịch vụ nhận dạng giọng nói của Google hoặc
          Microsoft để đổi thành chữ. Nihon Mukae không nhận âm thanh. Bạn có thể không dùng micro mà chỉ gõ.
        </li>
        <li>
          <strong>Chúng tôi chỉ lưu tóm tắt</strong> mỗi buổi (chủ đề, nhận xét, số lượt, thời gian) gắn với tài khoản của bạn.
          Bạn xoá được từng buổi bất cứ lúc nào.
        </li>
        <li>
          <strong>Key Gemini</strong> chỉ lưu trong trình duyệt của bạn, chúng tôi không nhận và không lưu.
        </li>
      </ul>

      <div className="mt-5 rounded-xl border border-black/10 p-4">
        <p className="flex items-center gap-2 text-sm font-semibold text-neutral-900">
          <KeyRound size={15} /> Chúng tôi bảo vệ key của bạn thế nào
        </p>
        <ul className="mt-2 list-disc space-y-1.5 pl-5 text-sm leading-6 text-neutral-700">
          <li>Key chỉ nằm trong trình duyệt của bạn. Nó không bao giờ được gửi tới máy chủ Nihon Mukae, nên chúng tôi không thể đọc, lưu hay làm lộ key.</li>
          <li>Key chỉ được gửi tới Google, qua kết nối mã hoá (HTTPS) và nằm trong phần đầu yêu cầu chứ không nằm trong địa chỉ trang, nên không lọt vào lịch sử hay nhật ký.</li>
          <li>Ô nhập key che ký tự, và chúng tôi chỉ hiện 4 ký tự cuối. Bạn xoá key khỏi trình duyệt bất cứ lúc nào bằng nút &ldquo;Xoá key&rdquo; hoặc &ldquo;Rút lại đồng ý&rdquo;.</li>
        </ul>
        <p className="mt-2 text-xs leading-5 text-neutral-500">
          Giới hạn thật thà: dữ liệu trong trình duyệt vẫn có thể bị đọc bởi tiện ích cài thêm hoặc phần mềm độc hại trên máy bạn. Vì vậy
          đừng nhập key trên máy dùng chung.
        </p>
      </div>

      <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-900">
        <p className="font-semibold">Hãy dùng key miễn phí, đừng dùng key trả phí theo lượng dùng</p>
        <p className="mt-1 text-xs leading-5">
          Tạo key trong một dự án Google AI Studio <strong>chưa bật thanh toán</strong>. Key miễn phí có hạn mức cố định nên dù bị lộ hay bị
          dùng quá tay cũng không thể phát sinh tiền, chỉ bị chặn tạm thời. Key trả phí tính tiền theo từng token, nếu lộ có thể mất tiền
          thật. Nếu nghi ngờ lộ key, vào aistudio.google.com/apikey và xoá key đó ngay.
        </p>
      </div>

      <p className="mt-4 rounded-xl bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
        Với key Gemini miễn phí, Google có thể dùng nội dung để cải thiện sản phẩm và người của Google có thể xem lại. Dữ liệu được
        xử lý ở máy chủ nước ngoài. Vì vậy <strong>đừng nói hoặc gõ thông tin cá nhân</strong> như họ tên đầy đủ, địa chỉ, số điện
        thoại, giấy tờ, tài khoản ngân hàng hay tình trạng sức khoẻ.
      </p>

      <div className="mt-5 space-y-2.5">
        <Check checked={dataOk} onChange={setDataOk}>
          Tôi đã đọc và đồng ý để nội dung hội thoại (chữ, và âm thanh nếu tôi dùng micro) được gửi tới Google như trên, và tôi hiểu
          nên dùng key miễn phí.
        </Check>
        <Check checked={ageOk} onChange={setAgeOk}>
          Tôi từ đủ 16 tuổi trở lên, hoặc đã được cha mẹ hoặc người giám hộ đồng ý cho dùng tính năng này.
        </Check>
      </div>

      <p className="mt-3 text-[11px] leading-4 text-neutral-400">
        Bạn rút lại đồng ý được bất cứ lúc nào ngay trên trang Kaiwa. Chi tiết xem{" "}
        <Link href="/privacy" className="underline">
          Chính sách quyền riêng tư
        </Link>
        .
      </p>

      {error && <p className="mt-3 text-xs text-red-600">{error}</p>}

      <button
        type="button"
        onClick={accept}
        disabled={!dataOk || !ageOk || saving}
        className="mt-5 w-full rounded-full bg-neutral-900 py-3 text-sm font-medium text-white transition disabled:opacity-40"
      >
        {saving ? "Đang lưu..." : "Đồng ý và tiếp tục"}
      </button>
    </div>
  );
}
