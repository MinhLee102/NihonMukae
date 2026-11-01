"use client";

import { useState, useEffect, useCallback } from "react";
import { Clock, BookOpen, Volume2, CheckCircle2 } from "lucide-react";
import { QuoteBanner } from "../../components/QuoteBanner";
import { SearchBar } from "../../components/SearchBar";
import { RecentWordCard, type RecentWord } from "../../components/RecentWordCard";
import { AddToFlashcardButton } from "@/components/flashcard/AddToFlashcardButton";
import { useAuthStore } from "@/store/authStore";
import {
  searchDictionary,
  getSearchHistory,
  type WordDto,
} from "@/lib/api/dictionary";

export default function DictionaryPage() {
  const accessToken = useAuthStore((state) => state.accessToken);

  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  
  const [searchResults, setSearchResults] = useState<WordDto[]>([]);
  const [selectedWord, setSelectedWord] = useState<WordDto | null>(null);
  
  const [isSearching, setIsSearching] = useState(false);
  const [fuzzyFallback, setFuzzyFallback] = useState(false);

  const [recentWords, setRecentWords] = useState<RecentWord[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  // Debounce query input 300ms
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedQuery(query.trim());
    }, 300);
    return () => clearTimeout(handler);
  }, [query]);

  // Load Lịch sử
  const loadHistory = useCallback(async () => {
    if (!accessToken) return;
    try {
      setLoadingHistory(true);
      const res = await getSearchHistory(0, 10);
      if (res.data?.content) {
        const mapped: RecentWord[] = res.data.content
          .filter((item) => item.word)
          .map((item) => {
            const w = item.word!;
            return {
              id: item.id.toString(),
              avatarChar: w.kanji ? w.kanji.charAt(0) : w.kana.charAt(0),
              word: w.kanji || w.kana,
              reading: w.kana,
              meaning: w.meanings?.[0]?.meaning || "Chưa có nghĩa",
            };
          });
        setRecentWords(mapped);
      }
    } catch (err) {
      console.error("Lỗi khi tải lịch sử:", err);
    } finally {
      setLoadingHistory(false);
    }
  }, [accessToken]);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  // Search API Call
  useEffect(() => {
    if (!debouncedQuery) {
      setSearchResults([]);
      setSelectedWord(null);
      setFuzzyFallback(false);
      return;
    }

    let isMounted = true;
    setIsSearching(true);

    searchDictionary(debouncedQuery, "vi", 0, 20)
      .then((res) => {
        if (!isMounted) return;
        const words = res.data?.results?.content || [];
        setSearchResults(words);
        setSelectedWord(words[0] || null); // Mặc định từ đầu tiên là từ chính
        setFuzzyFallback(res.data?.fuzzyFallback || false);
        if (accessToken) loadHistory();
      })
      .catch((err) => {
        console.error("Lỗi tìm kiếm:", err);
        if (isMounted) {
          setSearchResults([]);
          setSelectedWord(null);
        }
      })
      .finally(() => {
        if (isMounted) setIsSearching(false);
      });

    return () => {
      isMounted = false;
    };
  }, [debouncedQuery, accessToken, loadHistory]);

  const handleSpeak = (text: string) => {
    if (!("speechSynthesis" in window)) return;

    // Hủy các câu đọc đang dở trước đó nếu bấm liên tục
    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "ja-JP";
    utterance.rate = 0.85; // Giảm nhẹ tốc độ để nghe rõ phát âm từng âm tiết
    utterance.pitch = 1.0; // Cao độ tự nhiên
    utterance.volume = 1.0;

    // Lấy danh sách giọng đọc tiếng Nhật có trên máy người dùng
    const voices = window.speechSynthesis.getVoices();
    
    // Ưu tiên chọn giọng tiếng Nhật chất lượng cao (Google, Microsoft, Kyoko, Otoya...)
    const japaneseVoice = voices.find(
      (v) => v.lang === "ja-JP" || v.lang.startsWith("ja")
    );

    if (japaneseVoice) {
      utterance.voice = japaneseVoice;
    }

    window.speechSynthesis.speak(utterance);
  };

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6 p-4">
      <QuoteBanner
        imageUrl="/images/kyoto-dusk.jpg"
        japanese="千里の道も一歩から"
        translation="Hành trình vạn dặm bắt đầu từ một bước chân."
      />

      <SearchBar
        value={query}
        onChange={setQuery}
        isLoading={isSearching}
      />

      {/* Khu vực hiển thị kết quả */}
      {debouncedQuery ? (
        <section className="flex flex-col gap-3">
          {fuzzyFallback && (
            <div className="rounded-lg bg-amber-500/10 px-4 py-2 text-xs text-amber-600">
              Không có kết quả khớp chính xác 100%. Hiển thị các kết quả tương tự:
            </div>
          )}

          {searchResults.length > 0 && selectedWord ? (
            <div className="grid grid-cols-1 items-start gap-5 md:grid-cols-3">
              {/* Box kết quả chính (Bên trái - chiếm 2/3) */}
              <div className="flex flex-col gap-5 rounded-2xl border border-black/5 bg-white p-6 shadow-sm md:col-span-2">
                {/* Header từ vựng */}
                <div className="flex items-start justify-between">
                  <div className="flex flex-col gap-3">
                    <div className="flex items-center gap-2">
                      <h1 className="text-4xl font-bold tracking-tight text-neutral-900">
                        {selectedWord.kanji || selectedWord.kana}
                      </h1>
                      <CheckCircle2 size={18} className="text-sky-500" />
                    </div>

                    {/* Tag phiên âm / Hán việt / Loại từ */}
                    <div className="flex flex-wrap items-center gap-2 text-xs">
                      {selectedWord.kana && (
                        <span className="rounded-full bg-neutral-100 px-3 py-1 text-neutral-700 font-medium">
                          {selectedWord.kana}
                        </span>
                      )}
                      {selectedWord.hanViet && (
                        <span className="rounded-full bg-sky-50 px-3 py-1 font-bold text-sky-600">
                          {selectedWord.hanViet}
                        </span>
                      )}
                      {selectedWord.pos && (
                        <span className="rounded-full bg-neutral-50 px-3 py-1 text-neutral-500 border border-neutral-200">
                          {selectedWord.pos}
                        </span>
                      )}
                      {selectedWord.jlptLevel && (
                        <span className="rounded-full bg-emerald-50 px-3 py-1 font-semibold text-emerald-600">
                          N{selectedWord.jlptLevel}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-1">
                    <AddToFlashcardButton wordId={selectedWord.id} />
                    <button
                      type="button"
                      onClick={() => handleSpeak(selectedWord.kanji || selectedWord.kana)}
                      className="rounded-full p-2 text-neutral-400 transition hover:bg-neutral-100 hover:text-neutral-700"
                      title="Phát âm"
                    >
                      <Volume2 size={22} strokeWidth={1.75} />
                    </button>
                  </div>
                </div>

                <div className="h-px bg-neutral-100" />

                {/* Danh sách nghĩa và ví dụ */}
                <div className="flex flex-col gap-6">
                  {selectedWord.meanings.map((m, idx) => (
                    <div key={idx} className="flex flex-col gap-2">
                      <div className="flex items-center gap-2">
                        <span className="h-1.5 w-1.5 rounded-full bg-sky-500" />
                        <span className="text-base font-bold text-neutral-800">
                          {m.meaning.replace(/^◆\s*/, "")}
                        </span>
                        {m.pos && (
                          <span className="rounded bg-neutral-100 px-2 py-0.5 text-[11px] text-neutral-500">
                            {m.pos}
                          </span>
                        )}
                      </div>

                      {/* Câu ví dụ đi kèm */}
                      {selectedWord.examples && selectedWord.examples.length > 0 && (
                        <div className="ml-4 flex flex-col gap-3 border-l-2 border-neutral-200 pl-4">
                          {selectedWord.examples.map((ex, exIdx) => (
                            <div key={exIdx} className="flex flex-col text-sm">
                              <p className="text-neutral-800">{ex.sentence}</p>
                              <p className="text-xs text-neutral-500">{ex.translation}</p>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              {/* Cột danh sách từ liên quan (Bên phải - chiếm 1/3) */}
              <div className="flex flex-col rounded-2xl border border-black/5 bg-white p-4 shadow-sm">
                <h3 className="mb-3 text-xs font-bold tracking-wider text-neutral-400 uppercase">
                  Từ liên quan ({searchResults.length})
                </h3>

                <div className="flex max-h-[550px] flex-col gap-2 overflow-y-auto pr-1">
                  {searchResults.map((item) => {
                    const isSelected = item.id === selectedWord.id;
                    const primaryMeaning = item.meanings?.[0]?.meaning?.replace(/^◆\s*/, "") || "";

                    return (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => setSelectedWord(item)}
                        className={`flex flex-col items-start gap-1 rounded-xl p-3 text-left transition-all ${
                          isSelected
                            ? "bg-sky-50/70 border border-sky-200"
                            : "hover:bg-neutral-50 border border-transparent"
                        }`}
                      >
                        <div className="flex items-baseline gap-2">
                          <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-sky-500" />
                          <span className="font-bold text-neutral-900">
                            {item.kanji || item.kana}
                          </span>
                          <span className="line-clamp-1 text-xs text-neutral-500">
                            {primaryMeaning}
                          </span>
                        </div>

                        {item.kana && (
                          <span className="ml-3.5 rounded-full bg-neutral-100 px-2 py-0.5 text-[11px] text-neutral-600">
                            {item.kana}
                          </span>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
          ) : (
            !isSearching && (
              <div className="flex flex-col items-center justify-center gap-2 py-16 text-neutral-400">
                <BookOpen size={36} strokeWidth={1.5} />
                <p className="text-sm">Không tìm thấy từ nào phù hợp</p>
              </div>
            )
          )}
        </section>
      ) : (
        /* Lịch sử tra cứu gần đây */
        <section>
          <div className="mb-3 flex items-center gap-2 text-sm text-neutral-500">
            <Clock size={14} strokeWidth={1.75} />
            Đã tra gần đây
          </div>

          {loadingHistory ? (
            <div className="py-2 text-xs text-neutral-400">Đang tải lịch sử...</div>
          ) : recentWords.length > 0 ? (
            <div className="grid grid-cols-2 gap-3">
              {recentWords.map((word) => (
                <RecentWordCard key={word.id} word={word} />
              ))}
            </div>
          ) : (
            <div className="py-2 text-xs text-neutral-400">Chưa có lịch sử tra cứu gần đây.</div>
          )}
        </section>
      )}
    </div>
  );
}