"use client";

import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { ShelfForm } from "@/components/shelf/ShelfForm";

export default function NewShelfItemPage() {
  return (
    <RequireAuth>
      <ShelfForm />
    </RequireAuth>
  );
}
