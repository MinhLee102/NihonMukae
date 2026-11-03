import type { Metadata } from "next";
import localFont from "next/font/local";
import { Noto_Serif, Noto_Serif_JP } from "next/font/google";
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

// Headings use a serif. Noto Serif carries the full Vietnamese diacritics (the system serif drew them detached);
// Noto Serif JP supplies the kanji and kana, the browser picks per character.
const notoSerif = Noto_Serif({
  subsets: ["latin", "vietnamese"],
  weight: ["400", "600", "700"],
  variable: "--font-noto-serif",
  display: "swap",
});
const notoSerifJp = Noto_Serif_JP({
  weight: ["400", "600", "700"],
  variable: "--font-noto-serif-jp",
  display: "swap",
  preload: false,
});

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
        className={`${geistSans.variable} ${geistMono.variable} ${notoSerif.variable} ${notoSerifJp.variable} antialiased`}
      >
        {children}
      </body>
    </html>
  );
}
