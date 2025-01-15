import axios, { AxiosError, AxiosHeaders, AxiosResponse, CreateAxiosDefaults } from "axios";
import { APP_API_URL_AUTHENTICATION } from "@/env";
import { TokenResponse } from "@/types/auth";
import { ApiResponse } from "@/types/api";

const config: CreateAxiosDefaults = {
  baseURL: `${APP_API_URL_AUTHENTICATION}/api`,
  headers: new AxiosHeaders({
    "Content-Type": "application/json",
    Accept: "application/json",
  }),
  withCredentials: true,
};

const axiosInstance = axios.create(config);

// Track if we're currently refreshing the token
let isRefreshing = false;
let refreshSubscribers: ((token: string) => void)[] = [];

// Add a request interceptor
axiosInstance.interceptors.request.use(
  async (config) => {
    const token = localStorage.getItem("JWT_TOKEN");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// Function to refresh token
const refreshAuthToken = async () => {
  const refreshToken = localStorage.getItem("REFRESH_TOKEN");
  if (!refreshToken) {
    return Promise.reject("No refresh token");
  }

  try {
    const response = await axios.post<ApiResponse<TokenResponse>>(`${APP_API_URL_AUTHENTICATION}/api/v1/auth/refresh-token`, {
      refreshToken,
    });

    const { accessToken, refreshToken: newRefreshToken } = response.data.data;
    localStorage.setItem("JWT_TOKEN", accessToken);
    localStorage.setItem("REFRESH_TOKEN", newRefreshToken);

    return accessToken;
  } catch (error) {
    return Promise.reject(error);
  }
};

// Function to add token refresh subscriber
const subscribeTokenRefresh = (callback: (token: string) => void) => {
  refreshSubscribers.push(callback);
};

// Function to notify all subscribers
const onTokenRefreshed = (token: string) => {
  refreshSubscribers.forEach((callback) => callback(token));
  refreshSubscribers = [];
};

// Add a response interceptor
axiosInstance.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config;
    if (!originalRequest) {
      return Promise.reject(error);
    }

    // If error is 401, and it's not a refresh token request
    if (error.response?.status === 401 && !originalRequest.url?.includes("refresh-token")) {
      if (!isRefreshing) {
        isRefreshing = true;

        try {
          const newToken = await refreshAuthToken();
          isRefreshing = false;
          onTokenRefreshed(newToken);

          // Retry original request with new token
          originalRequest.headers.Authorization = `Bearer ${newToken}`;
          return axiosInstance(originalRequest);
        } catch (refreshError) {
          isRefreshing = false;

          // Clear auth data and redirect to log in
          localStorage.removeItem("JWT_TOKEN");
          localStorage.removeItem("REFRESH_TOKEN");
          localStorage.removeItem("USER");
          localStorage.removeItem("IS_ADMIN");
          window.location.href = "/login";

          return Promise.reject(refreshError);
        }
      }

      // If we're already refreshing, wait for the new token
      return new Promise((resolve) => {
        subscribeTokenRefresh((token: string) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          resolve(axiosInstance(originalRequest));
        });
      });
    }

    return Promise.reject(error);
  },
);

export default axiosInstance;
