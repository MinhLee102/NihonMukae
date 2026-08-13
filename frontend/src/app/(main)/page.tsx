import { Clock } from "lucide-react";
import { QuoteBanner } from "../../components/QuoteBanner";
import { SearchBar } from "../../components/SearchBar";
import { RecentWordCard, type RecentWord } from "../../components/RecentWordCard";

// Placeholder data — replace with the real /dictionary/search-history
// response once DICT-05 is wired up.

const RECENT_WORDS: RecentWord[] = [
  { id: "1", avatarChar: "日", word: "日本語", reading: "にほんご", meaning: "Tiếng Nhật" },
  { id: "2", avatarChar: "勉", word: "勉強", reading: "べんきょう", meaning: "Học tập" },
  { id: "3", avatarChar: "桜", word: "桜", reading: "さくら", meaning: "Hoa anh đào" },
  { id: "4", avatarChar: "食", word: "食べる", reading: "たべる", meaning: "Ăn" },
  { id: "5", avatarChar: "友", word: "友達", reading: "ともだち", meaning: "Bạn bè" },
  { id: "6", avatarChar: "山", word: "山", reading: "やま", meaning: "Núi" },
  { id: "7", avatarChar: "海", word: "海", reading: "うみ", meaning: "Biển" },
  { id: "8", avatarChar: "猫", word: "猫", reading: "ねこ", meaning: "Mèo" },
  { id: "9", avatarChar: "電", word: "電車", reading: "でんしゃ", meaning: "Tàu điện" },
  { id: "10", avatarChar: "東", word: "東京", reading: "とうきょう", meaning: "Tokyo" },
];

export default function DictionaryPage() {
  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
        <QuoteBanner
          imageUrl="/images/kyoto-dusk.jpg"
          japanese="千里の道も一歩から"
          translation="Hành trình vạn dặm bắt đầu từ một bước chân."
        />

        <SearchBar />

        <section>
          <div className="mb-3 flex items-center gap-2 text-sm text-neutral-500">
            <Clock size={14} strokeWidth={1.75} />
            Đã tra gần đây
          </div>
          <div className="grid grid-cols-2 gap-3">
            {RECENT_WORDS.map((word) => (
              <RecentWordCard key={word.id} word={word} />
            ))}
          </div>
        </section>
      </div>
  );
}