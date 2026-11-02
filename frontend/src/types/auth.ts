export interface AuthResponse {
  accessToken: string;
}
 
export interface ApiErrorBody {
  error: string;
}
 
export interface RegisterPayload {
  email: string;
  password: string;
  name: string;
}
 
export interface LoginPayload {
  email: string;
  password: string;
}
 
export interface AuthUser {
  email: string;
  name?: string;
  role?: string;
}