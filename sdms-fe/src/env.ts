// Get environment variables safely
const { VITE_APP_API_URL_AUTHENTICATION } = import.meta.env;

export const APP_API_URL_AUTHENTICATION = VITE_APP_API_URL_AUTHENTICATION;
