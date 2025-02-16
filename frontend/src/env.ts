// Get environment variables safely
const { VITE_APP_API_URL, VITE_OAUTH_GOOGLE_REDIRECT_URL } = import.meta.env;

export const APP_API_URL = VITE_APP_API_URL;
export const OAUTH_GOOGLE_REDIRECT_URL = VITE_OAUTH_GOOGLE_REDIRECT_URL;
