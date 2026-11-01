"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { RequireAuth } from "@/components/flashcard/RequireAuth";
import { ShelfForm } from "@/components/shelf/ShelfForm";
import { useAuthStore } from "@/store/authStore";
import { getShelfItem, type ShelfItemDto } from "@/lib/api/shelf";
import { getApiErrorMessage } from "@/lib/api/flashcard";

function EditShelfItem() {
  const { id } = useParams<{ id: string }>();
  const accessToken = useAuthStore((s) => s.accessToken);
  const [item, setItem] = useState<ShelfItemDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    getShelfItem(id)
      .then(setItem)
      .catch((err) => setError(getApiErrorMessage(err, "Không tải được bài.")));
  }, [accessToken, id]);

  if (error) return <p className="py-20 text-center text-sm text-red-600">{error}</p>;
  if (!item) return <p className="py-20 text-center text-sm text-neutral-400">Đang tải...</p>;

  return <ShelfForm id={item.id} initialTitle={item.title} initialContent={item.content ?? ""} />;
}

export default function EditShelfItemPage() {
  return (
    <RequireAuth>
      <EditShelfItem />
    </RequireAuth>
  );
}
