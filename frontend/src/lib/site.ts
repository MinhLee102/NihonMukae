/** Thông tin hiện ở footer và trang nguồn dữ liệu. Sửa ở đây để thêm email, Facebook... */
export const SITE = {
  name: "日本語 · Nihon Mukae",
  owner: "Minh Lê",
  contacts: [
    { label: "GitHub", href: "https://github.com/MinhLee102", text: "github.com/MinhLee102" },
    // { label: "Email", href: "mailto:you@example.com", text: "you@example.com" },
  ],
} as const;

/** Bộ dữ liệu bên thứ ba và giấy phép của chúng (hiện trên trang /credits, tóm tắt ở footer). */
export const DATA_SOURCES = [
  {
    name: "JMdict",
    owner: "Electronic Dictionary Research and Development Group (EDRDG)",
    usage: "Từ vựng, cách đọc và nghĩa tiếng Anh. Nghĩa tiếng Việt do chúng tôi dịch từ JMdict nên là bản phái sinn, cũng theo CC BY-SA 4.0.",
    license: "CC BY-SA 4.0",
    licenseUrl: "https://creativecommons.org/licenses/by-sa/4.0/",
    url: "https://www.edrdg.org/jmdict/j_jmdict.html",
  },
  {
    name: "Tatoeba (Tanaka Corpus)",
    owner: "Tatoeba và những người đóng góp câu",
    usage: "Câu ví dụ tiếng Nhật. Mỗi câu có liên kết về trang gốc trên Tatoeba; bản dịch tiếng Việt có thể do người dùng Tatoeba hoặc do chúng tôi dịch.",
    license: "CC BY 2.0 FR",
    licenseUrl: "https://creativecommons.org/licenses/by/2.0/fr/",
    url: "https://tatoeba.org",
  },
  {
    name: "Danh sách từ vựng JLPT",
    owner: "Jonathan Waller (tanos.co.uk), gắn mã JMdict bởi stephenmk/yomitan-jlpt-vocab",
    usage: "Gắn cấp độ JLPT cho từ vựng.",
    license: "CC BY / CC BY-SA 4.0",
    licenseUrl: "https://github.com/stephenmk/yomitan-jlpt-vocab",
    url: "http://www.tanos.co.uk/jlpt/",
  },
  {
    name: "KANJIDIC2",
    owner: "Electronic Dictionary Research and Development Group (EDRDG)",
    usage: "Âm on/kun, nghĩa tiếng Anh, số nét, bộ thủ và độ phổ biến của chữ Hán.",
    license: "CC BY-SA 4.0",
    licenseUrl: "https://creativecommons.org/licenses/by-sa/4.0/",
    url: "https://www.edrdg.org/wiki/index.php/KANJIDIC_Project",
  },
  {
    name: "KanjiVG",
    owner: "Ulrich Apel và những người đóng góp",
    usage: "Hình thứ tự nét viết của chữ Hán.",
    license: "CC BY-SA 3.0",
    licenseUrl: "https://creativecommons.org/licenses/by-sa/3.0/",
    url: "https://kanjivg.tagaini.net",
  },
  {
    name: "Unihan Database",
    owner: "Unicode, Inc.",
    usage: "Âm Hán Việt của chữ kanji.",
    license: "Unicode License",
    licenseUrl: "https://www.unicode.org/license.txt",
    url: "https://www.unicode.org/charts/unihan.html",
  },
] as const;

export const SOURCE_LINKS = {
  tatoebaSentence: (id: number) => `https://tatoeba.org/sentences/show/${id}`,
} as const;
