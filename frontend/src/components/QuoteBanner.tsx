type QuoteBannerProps = {
  imageUrl: string;
  japanese: string;
  translation: string;
};

/**
 * Static hero banner. Content is hardcoded for now — later this can be
 * swapped for a "quote of the day" fetched from the backend.
 */
export function QuoteBanner({ imageUrl, japanese, translation }: QuoteBannerProps) {
  return (
    <div
      className="relative h-[220px] overflow-hidden rounded-2xl bg-cover bg-center"
      style={{ backgroundImage: `url(${imageUrl})` }}
    >
      <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/10 to-transparent" />
      <div className="absolute bottom-6 left-8 right-8 text-white">
        <p className="mb-2 text-xs font-semibold tracking-[0.15em] text-white/70">
          TRÍCH DẪN HÔM NAY
        </p>
        <p className="mb-1 font-serif text-2xl">「{japanese}」</p>
        <p className="text-sm text-white/80">{translation}</p>
      </div>
    </div>
  );
}
