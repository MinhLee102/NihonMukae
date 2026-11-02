import {
  BookOpen,
  Layers,
  Bookmark,
  AlignLeft,
  Headphones,
  GraduationCap,
  Mic,
  type LucideIcon,
} from "lucide-react";
 
export type NavItem = {
  /** Route path, used for active-state matching against usePathname() */
  href: string;
  /** Label shown in the sidebar */
  label: string;
  /** Icon component from lucide-react */
  icon: LucideIcon;
  /** Japanese-script eyebrow shown in the header breadcrumb */
  breadcrumbJa: string;
  /** Vietnamese label shown in the header breadcrumb */
  breadcrumbVi: string;
  /** Whether this route is reachable without being logged in */
  publicRoute: boolean;
};
 
export const NAV_ITEMS: NavItem[] = [
  {
    href: "/",
    label: "Từ điển",
    icon: BookOpen,
    breadcrumbJa: "辞書",
    breadcrumbVi: "Từ điển",
    publicRoute: true,
  },
  {
    href: "/flashcard",
    label: "Flashcard",
    icon: Layers,
    breadcrumbJa: "フラッシュカード",
    breadcrumbVi: "Flashcard",
    publicRoute: false,
  },
  {
    href: "/shelf",
    label: "My Shelf",
    icon: Bookmark,
    breadcrumbJa: "本棚",
    breadcrumbVi: "My Shelf",
    publicRoute: false,
  },
  {
    href: "/reading",
    label: "Reading",
    icon: AlignLeft,
    breadcrumbJa: "読解",
    breadcrumbVi: "Reading",
    publicRoute: false,
  },
  {
    href: "/listening",
    label: "Listening",
    icon: Headphones,
    breadcrumbJa: "聴解",
    breadcrumbVi: "Listening",
    publicRoute: false,
  },
  {
    href: "/jlpt",
    label: "JLPT",
    icon: GraduationCap,
    breadcrumbJa: "試験",
    breadcrumbVi: "JLPT",
    publicRoute: false,
  },
  {
    href: "/shadowing",
    label: "Shadowing",
    icon: Mic,
    breadcrumbJa: "発音",
    breadcrumbVi: "Shadowing",
    publicRoute: false,
  },
];
 
/** Finds the closest matching nav item for a given pathname (falls back to Dictionary). */
export function getActiveNavItem(pathname: string): NavItem {
  if (pathname.startsWith("/profile")) {
    return { ...NAV_ITEMS[0], href: "/profile", breadcrumbJa: "プロフィール", breadcrumbVi: "Hồ sơ" };
  }
  const exact = NAV_ITEMS.find((item) => item.href === pathname);
  if (exact) return exact;
 
  const partial = NAV_ITEMS.filter(
    (item) => item.href !== "/" && pathname.startsWith(item.href)
  ).sort((a, b) => b.href.length - a.href.length)[0];
 
  return partial ?? NAV_ITEMS[0];
}