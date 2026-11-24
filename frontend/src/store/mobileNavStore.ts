import { create } from "zustand";

/** Open state of the navigation drawer that replaces the sidebar on small screens. */
interface MobileNavState {
  open: boolean;
  show: () => void;
  hide: () => void;
}

export const useMobileNavStore = create<MobileNavState>((set) => ({
  open: false,
  show: () => set({ open: true }),
  hide: () => set({ open: false }),
}));
