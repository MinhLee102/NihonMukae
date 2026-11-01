"""
backfill_frequency.py

Backfill cột words.frequency_rank (đang luôn NULL) bằng priority tag thật
từ JMdict — vì Mazii không cung cấp thông tin độ phổ biến của từ vựng, chỉ
Mazii kanji.csv có jlpt_level cho từng kanji riêng lẻ, không có cho cả từ.

JMdict mỗi entry có gắn ke_pri (kanji priority) / re_pri (reading priority):
  - nfXX: xếp theo nhóm 500 từ (nf01 = 500 từ phổ biến nhất, nf02 = 501-1000...)
  - news1/ichi1/spec1/spec2/gai1: "common" nhưng không có số nfXX cụ thể
  - news2/ichi2/gai2: "common bậc 2" (giảm hạng so với bậc 1)
  - Không có priority tag nào: JMdict không đánh dấu là từ phổ biến

Cách quy đổi ra frequency_rank (số càng nhỏ càng phổ biến, NULL = không rõ):
  - Có nfXX          -> rank = XX * 500            (vd nf01 -> 500, nf14 -> 7000)
  - Có news1/ichi1/spec1/spec2/gai1 (không nfXX) -> rank = 30000
  - Có news2/ichi2/gai2 (không nfXX)              -> rank = 40000
  - Không match được với JMdict, hoặc match nhưng không có priority tag nào
    -> rank = NULL (giữ nguyên, xếp cuối cùng qua NULLS LAST)

Cách dùng:
  1. Tải JMdict_e.gz: http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz
  2. gunzip ra JMdict_e.xml
  3. python backfill_frequency.py --jmdict-path JMdict_e.xml

License: JMdict/EDICT (CC BY-SA) — chỉ dùng dữ liệu priority tag, không copy
nghĩa tiếng Anh. Vẫn nên ghi credit "JMdict/EDICT Project" ở trang About.
"""
import argparse
import re
import sys
from collections import defaultdict

import psycopg2
import xml.etree.ElementTree as ET
from psycopg2.extras import execute_values
from tqdm import tqdm

import config

NF_PATTERN = re.compile(r"^nf(\d{2})$")
PRIORITY_TIER_1 = {"news1", "ichi1", "spec1", "spec2", "gai1"}
PRIORITY_TIER_2 = {"news2", "ichi2", "gai2"}

RANK_TIER_1 = 30000
RANK_TIER_2 = 40000


def compute_rank(tags):
    """tags: list các priority tag gộp từ mọi k_ele/r_ele trong 1 entry.
    Trả về rank số nguyên, hoặc None nếu không có priority tag nào."""
    nf_numbers = []
    for t in tags:
        m = NF_PATTERN.match(t)
        if m:
            nf_numbers.append(int(m.group(1)))
    if nf_numbers:
        return min(nf_numbers) * 500

    if any(t in PRIORITY_TIER_1 for t in tags):
        return RANK_TIER_1
    if any(t in PRIORITY_TIER_2 for t in tags):
        return RANK_TIER_2
    return None


def parse_jmdict(jmdict_path):
    """Trả về dict {(kanji_or_None, kana): rank} — kanji=None nghĩa là entry
    JMdict đó không có dạng kanji (từ thuần kana).

    Dùng xml.etree.ElementTree (built-in, không cần cài lxml) — expat (parser
    nền của ElementTree) tự xử lý đúng entity khai báo trong DOCTYPE nội bộ
    của JMdict, không cần thư viện ngoài.
    """
    freq_map = {}
    entry_count = 0

    for event, elem in tqdm(ET.iterparse(jmdict_path, events=("end",)), desc="parse JMdict entries"):
        if elem.tag != "entry":
            continue
        entry_count += 1
        kanji_forms = []  # list[str]
        kana_forms = []  # list[str]
        all_tags = []

        for k_ele in elem.findall("k_ele"):
            keb = k_ele.findtext("keb")
            if keb:
                kanji_forms.append(keb)
            for pri in k_ele.findall("ke_pri"):
                if pri.text:
                    all_tags.append(pri.text)

        for r_ele in elem.findall("r_ele"):
            reb = r_ele.findtext("reb")
            if reb:
                kana_forms.append(reb)
            for pri in r_ele.findall("re_pri"):
                if pri.text:
                    all_tags.append(pri.text)

        rank = compute_rank(all_tags)
        if rank is not None:
            if kanji_forms:
                for kanji in kanji_forms:
                    for kana in kana_forms:
                        key = (kanji, kana)
                        # 1 (kanji, kana) có thể xuất hiện ở nhiều entry JMdict
                        # khác nhau -> giữ rank TỐT NHẤT (số nhỏ nhất) tìm được
                        if key not in freq_map or rank < freq_map[key]:
                            freq_map[key] = rank
            else:
                # entry thuần kana, không có k_ele
                for kana in kana_forms:
                    key = (None, kana)
                    if key not in freq_map or rank < freq_map[key]:
                        freq_map[key] = rank

        # Giải phóng bộ nhớ: elem.clear() xoá text/children/attrib của entry
        # này (ElementTree không có API prune-root như lxml, nhưng vì mỗi
        # entry đã clear() chỉ còn 1 thẻ rỗng gắn ở root, ~200k thẻ rỗng vẫn
        # nhẹ, không đáng lo).
        elem.clear()

    print(f"Đã parse {entry_count} entries JMdict, {len(freq_map)} cặp (kanji, kana) có priority tag.")
    return freq_map


def backfill_db(db_url, freq_map, batch_size=5000):
    conn = psycopg2.connect(db_url)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, kanji, kana FROM words")
            rows = cur.fetchall()

        print(f"Đang đối chiếu {len(rows)} từ trong DB với dữ liệu JMdict...")
        updates = []
        matched = 0
        for word_id, kanji, kana in tqdm(rows, desc="match words"):
            # Mazii gộp nhiều cách đọc vào 1 chuỗi kana cách nhau bởi dấu
            # cách (vd "にほん にっぽん" cho 日本), trong khi JMdict lưu mỗi
            # cách đọc là 1 <reb> riêng -> phải tách ra thử khớp từng cách
            # đọc, không thì so nguyên chuỗi ghép sẽ KHÔNG BAO GIỜ khớp
            # được (đây chính là lý do 日本 bị bỏ sót ở lần chạy trước).
            kana_candidates = kana.split() if kana else [kana]
            best_rank = None
            for candidate in kana_candidates:
                rank = freq_map.get((kanji, candidate))
                if rank is None and kanji is None:
                    rank = freq_map.get((None, candidate))
                if rank is not None and (best_rank is None or rank < best_rank):
                    best_rank = rank
            if best_rank is not None:
                updates.append((word_id, best_rank))
                matched += 1

        print(f"Match được {matched}/{len(rows)} từ ({matched * 100 // max(len(rows), 1)}%).")

        with conn.cursor() as cur:
            for i in tqdm(range(0, len(updates), batch_size), desc="update DB"):
                batch = updates[i:i + batch_size]
                execute_values(
                    cur,
                    """
                    UPDATE words SET frequency_rank = data.rank
                    FROM (VALUES %s) AS data(id, rank)
                    WHERE words.id = data.id
                    """,
                    batch,
                )
                conn.commit()

        print("Xong.")
    finally:
        conn.close()


def main():
    parser = argparse.ArgumentParser(description="Backfill words.frequency_rank từ JMdict priority tags")
    parser.add_argument("--jmdict-path", required=True, help="Đường dẫn tới JMdict_e.xml đã gunzip")
    parser.add_argument("--db-url", default=config.DB_URL)
    args = parser.parse_args()

    freq_map = parse_jmdict(args.jmdict_path)
    backfill_db(args.db_url, freq_map)


if __name__ == "__main__":
    sys.exit(main())