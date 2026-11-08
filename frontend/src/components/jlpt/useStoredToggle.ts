"use client";

import { useCallback, useEffect, useState } from "react";

/** An open/closed switch remembered per browser (answer sheet, instruction header). Storage may be unavailable. */
export function useStoredToggle(key: string, initial = true): [boolean, () => void] {
  const [open, setOpen] = useState(initial);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(key);
      if (saved === "open" || saved === "closed") setOpen(saved === "open");
    } catch {
      // storage unavailable: keep the default
    }
  }, [key]);

  const toggle = useCallback(
    () =>
      setOpen((value) => {
        try {
          localStorage.setItem(key, value ? "closed" : "open");
        } catch {
          // ignore
        }
        return !value;
      }),
    [key]
  );

  return [open, toggle];
}
