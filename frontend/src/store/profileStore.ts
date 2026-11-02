import { create } from 'zustand';
import { fetchProfile, updateProfile, type Profile } from '@/lib/api/user';

interface ProfileState {
  profile: Profile | null;
  load: () => Promise<void>;
  save: (payload: { name: string; targetLevel: number | null }) => Promise<void>;
  clear: () => void;
}

export const useProfileStore = create<ProfileState>((set) => ({
  profile: null,
  load: async () => {
    try {
      set({ profile: await fetchProfile() });
    } catch {
      // The sidebar falls back to the token's email; nothing to show here.
    }
  },
  save: async (payload) => {
    set({ profile: await updateProfile(payload) });
  },
  clear: () => set({ profile: null }),
}));
