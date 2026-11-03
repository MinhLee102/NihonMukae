import type { Metadata } from "next";
import localFont from "next/font/local";
import "@fontsource/noto-serif/latin-400.css";
import "@fontsource/noto-serif/latin-600.css";
import "@fontsource/noto-serif/latin-700.css";
import "@fontsource/noto-serif/vietnamese-400.css";
import "@fontsource/noto-serif/vietnamese-600.css";
import "@fontsource/noto-serif/vietnamese-700.css";
import "./globals.css";

const geistSans = localFont({
  src: "./fonts/GeistVF.woff",
  variable: "--font-geist-sans",
  weight: "100 900",
});
const geistMono = localFont({
  src: "./fonts/GeistMonoVF.woff",
  variable: "--font-geist-mono",
  weight: "100 900",
});

// Headings use Noto Serif, bundled from npm (no network at build time) with the Vietnamese subset so stacked
// diacritics render correctly. Kanji and kana fall back to the system Japanese serif (see tailwind.config.ts).
export const metadata: Metadata = {
  title: "Nihon Mukae",
  description: "Japanese learning platform",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi" className="h-full">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        {children}
      </body>
    </html>
  );
}
