/**
 * Gọi Gemini trực tiếp từ trình duyệt bằng key của chính người dùng.
 * Key chỉ nằm trong localStorage của máy họ và chỉ được gửi tới Google (qua header, không nằm trong URL);
 * server của app không nhìn thấy và không lưu key.
 */

const KEY_STORAGE = "kaiwa.geminiKey";
const ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models";
// Thử lần lượt: model mới trước, model cũ nếu model đó không còn
const MODELS = ["gemini-2.5-flash", "gemini-2.0-flash"];
/** Số lượt gần nhất gửi kèm mỗi yêu cầu (một lượt = một câu của người dùng hoặc của AI) */
export const HISTORY_WINDOW = 20;

export interface ChatMessage {
  role: "user" | "ai";
  text: string;
}

export class GeminiError extends Error {
  constructor(
    message: string,
    readonly kind: "key" | "quota" | "network" | "other"
  ) {
    super(message);
  }
}

export function loadApiKey(): string {
  try {
    return localStorage.getItem(KEY_STORAGE) ?? "";
  } catch {
    return "";
  }
}

export function saveApiKey(key: string): boolean {
  try {
    localStorage.setItem(KEY_STORAGE, key.trim());
    return true;
  } catch {
    return false;
  }
}

export function clearApiKey() {
  try {
    localStorage.removeItem(KEY_STORAGE);
  } catch {
    // trình duyệt chặn lưu trữ: không còn gì để xoá
  }
}

interface Content {
  role: "user" | "model";
  parts: { text: string }[];
}

interface GenerateOptions {
  system: string;
  contents: Content[];
  json?: boolean;
  maxTokens?: number;
  temperature?: number;
}

async function generate(apiKey: string, opts: GenerateOptions): Promise<string> {
  let lastError: GeminiError | null = null;
  for (const model of MODELS) {
    let res: Response;
    try {
      res = await fetch(`${ENDPOINT}/${model}:generateContent`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: opts.system }] },
          contents: opts.contents,
          generationConfig: {
            temperature: opts.temperature ?? 0.8,
            maxOutputTokens: opts.maxTokens ?? 700,
            ...(opts.json ? { responseMimeType: "application/json" } : {}),
            // Hội thoại cần trả lời nhanh, không cần "suy nghĩ" dài
            ...(model.startsWith("gemini-2.5") ? { thinkingConfig: { thinkingBudget: 0 } } : {}),
          },
        }),
      });
    } catch {
      throw new GeminiError("Không kết nối được tới Gemini. Kiểm tra mạng rồi thử lại.", "network");
    }

    if (res.ok) {
      const data = (await res.json()) as {
        candidates?: { content?: { parts?: { text?: string }[] }; finishReason?: string }[];
        promptFeedback?: { blockReason?: string };
      };
      const text = data.candidates?.[0]?.content?.parts?.map((p) => p.text ?? "").join("").trim();
      if (!text) {
        throw new GeminiError(
          data.promptFeedback?.blockReason ? "Gemini từ chối nội dung này, hãy nói theo cách khác." : "Gemini không trả lời, hãy thử lại.",
          "other"
        );
      }
      return text;
    }

    const body = (await res.json().catch(() => null)) as { error?: { message?: string; status?: string } } | null;
    const detail = body?.error?.message ?? "";
    if (res.status === 404) {
      lastError = new GeminiError("Model Gemini không còn khả dụng.", "other");
      continue;
    }
    if (res.status === 429) throw new GeminiError("Key của bạn đã hết hạn mức miễn phí trong lúc này. Đợi một chút rồi thử lại.", "quota");
    if (res.status === 400 && /api key/i.test(detail)) throw new GeminiError("Key Gemini không hợp lệ.", "key");
    if (res.status === 401 || res.status === 403) throw new GeminiError("Key Gemini không được phép dùng. Kiểm tra lại key.", "key");
    throw new GeminiError(`Gemini báo lỗi (${res.status}).`, "other");
  }
  throw lastError ?? new GeminiError("Không gọi được Gemini.", "other");
}

const LEVEL_HINT: Record<number, string> = {
  5: "N5: dùng từ vựng và ngữ pháp rất cơ bản, câu ngắn, thể です/ます, ít kanji.",
  4: "N4: ngữ pháp sơ cấp (thể て, たい, ている...), câu ngắn đến vừa.",
  3: "N3: câu tự nhiên hằng ngày, có thể dùng thể thường và các mẫu trung cấp.",
  2: "N2: hội thoại tự nhiên, từ vựng phong phú, có thể có kính ngữ cơ bản.",
  1: "N1: hội thoại tự nhiên như người bản xứ, có thể dùng thành ngữ và kính ngữ.",
};

function chatSystem(level: number, topic: string): string {
  return [
    "Bạn là giáo viên tiếng Nhật đang luyện hội thoại với một người Việt đang học tiếng Nhật.",
    `Chủ đề buổi nói chuyện: ${topic}`,
    `Trình độ của học viên: ${LEVEL_HINT[level] ?? LEVEL_HINT[3]} Chỉ dùng từ và ngữ pháp trong tầm trình độ này.`,
    "Quy tắc:",
    "- Trả lời bằng tiếng Nhật, ngắn gọn (1 đến 3 câu), rồi hỏi lại một câu để học viên nói tiếp.",
    "- Nếu câu của học viên có lỗi về ngữ pháp, từ vựng hoặc cách nói chưa tự nhiên, sau câu trả lời xuống dòng và viết một dòng bắt đầu bằng \"💡\" giải thích nhẹ nhàng bằng tiếng Việt, kèm câu đúng. Không có lỗi thì không viết dòng này.",
    "- Câu của học viên có thể là kết quả nhận dạng giọng nói nên có thể sai chính tả nhẹ, đừng coi đó là lỗi nếu nghĩa rõ.",
    "- Nếu học viên viết tiếng Việt vì không biết nói, hãy dạy lại cách nói bằng tiếng Nhật rồi mời họ nói thử.",
    "- Không dùng markdown, không dùng bảng.",
  ].join("\n");
}

function toContents(history: ChatMessage[]): Content[] {
  return history.slice(-HISTORY_WINDOW).map((m) => ({ role: m.role === "user" ? "user" : "model", parts: [{ text: m.text }] }));
}

/** Câu mở đầu của AI (history rỗng) hoặc câu trả lời cho lượt vừa nói. */
export async function nextReply(apiKey: string, level: number, topic: string, history: ChatMessage[]): Promise<string> {
  const contents = toContents(history);
  if (contents.length === 0) {
    contents.push({ role: "user", parts: [{ text: "(Hãy bắt đầu cuộc trò chuyện: chào học viên và mở đầu bằng một câu hỏi về chủ đề.)" }] });
  }
  return generate(apiKey, { system: chatSystem(level, topic), contents, maxTokens: 500 });
}

/** 3 chủ đề gợi ý hợp trình độ. */
export async function suggestTopics(apiKey: string, level: number): Promise<string[]> {
  const text = await generate(apiKey, {
    system: "Bạn là giáo viên tiếng Nhật. Chỉ trả về JSON.",
    contents: [
      {
        role: "user",
        parts: [
          {
            text: `Gợi ý 3 chủ đề hội thoại tiếng Nhật đời thường, khác nhau, phù hợp học viên trình độ N${level}. Mỗi chủ đề viết bằng tiếng Việt, tối đa 8 từ, kèm tên tiếng Nhật trong ngoặc. Trả về mảng JSON gồm 3 chuỗi.`,
          },
        ],
      },
    ],
    json: true,
    maxTokens: 300,
    temperature: 1,
  });
  try {
    const arr = JSON.parse(text) as unknown;
    if (Array.isArray(arr)) return arr.filter((x): x is string => typeof x === "string" && x.trim() !== "").slice(0, 3);
  } catch {
    // rơi xuống dưới
  }
  throw new GeminiError("Không đọc được gợi ý chủ đề, hãy thử lại.", "other");
}

/** Tóm tắt buổi nói chuyện để lưu lại. */
export async function summarize(apiKey: string, level: number, topic: string, history: ChatMessage[]): Promise<string> {
  const transcript = history
    .map((m) => `${m.role === "user" ? "Học viên" : "Giáo viên"}: ${m.text}`)
    .join("\n")
    .slice(-8000);
  return generate(apiKey, {
    system: "Bạn là giáo viên tiếng Nhật, viết nhận xét ngắn gọn bằng tiếng Việt.",
    contents: [
      {
        role: "user",
        parts: [
          {
            text: `Dưới đây là một buổi hội thoại luyện tập (trình độ N${level}, chủ đề: ${topic}). Hãy viết tóm tắt tối đa 120 từ gồm: nội dung đã nói, 1 đến 3 lỗi hoặc điểm cần cải thiện (kèm câu đúng), và 1 lời khuyên. Không dùng markdown.\n\n${transcript}`,
          },
        ],
      },
    ],
    maxTokens: 500,
    temperature: 0.4,
  });
}
