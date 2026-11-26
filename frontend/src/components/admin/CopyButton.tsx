"use client";

import { useState } from "react";
import { Check, Copy } from "lucide-react";

/** Copies text, with a textarea fallback for browsers that block the clipboard API on plain http. */
async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    try {
      const area = document.createElement("textarea");
      area.value = text;
      area.style.position = "fixed";
      area.style.opacity = "0";
      document.body.appendChild(area);
      area.select();
      const ok = document.execCommand("copy");
      document.body.removeChild(area);
      return ok;
    } catch {
      return false;
    }
  }
}

export function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState<"ok" | "fail" | null>(null);

  async function copy() {
    setCopied((await copyText(text)) ? "ok" : "fail");
    window.setTimeout(() => setCopied(null), 2500);
  }

  return (
    <button
      type="button"
      onClick={() => void copy()}
      className="flex items-center gap-2 rounded-full bg-neutral-900 px-5 py-2 text-sm font-medium text-white"
    >
      {copied === "ok" ? <Check size={15} /> : <Copy size={15} />}
      {copied === "ok" ? "Đã sao chép" : copied === "fail" ? "Không sao chép được, bôi đen rồi Ctrl + C" : "Sao chép prompt"}
    </button>
  );
}

