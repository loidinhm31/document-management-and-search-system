import axios, { AxiosError, AxiosHeaders, AxiosResponse, CreateAxiosDefaults } from "axios";

import { APP_API_URL } from "@/env";
import { TokenResponse } from "@/types/auth";

// Constants for retry mechanism
const MAX_REFRESH_RETRIES = 3;
const RETRY_STORAGE_KEY = "refresh_retry_count";
const RETRY_TIMESTAMP_KEY = "refresh_retry_timestamp";
const RETRY_COOLDOWN = 60 * 1000; // 1 minute cooldown between retry cycles

const config: CreateAxiosDefaults = {
  baseURL: `${APP_API_URL}`,
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
    // Only add Authorization header for non-auth endpoints
    const isAuthEndpoint = config.url?.match(/^\/auth\/api\/v\d+\/auth/);
    if (token && !isAuthEndpoint) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// Function to check if refresh should be attempted based on retry limits
const shouldAttemptRefresh = () => {
  // Check if we're in a retry cooldown period
  const lastRetryTimestamp = localStorage.getItem(RETRY_TIMESTAMP_KEY);
  if (lastRetryTimestamp) {
    const cooldownEndTime = parseInt(lastRetryTimestamp) + RETRY_COOLDOWN;
    if (Date.now() < cooldownEndTime) {
      console.log("In cooldown period, not attempting refresh");
      return false;
    }
  }

  // Get current retry count
  const currentRetryCount = parseInt(localStorage.getItem(RETRY_STORAGE_KEY) || "0");

  // If we've exceeded max retries, don't attempt refresh
  if (currentRetryCount >= MAX_REFRESH_RETRIES) {
    console.log(`Max retries (${MAX_REFRESH_RETRIES}) exceeded, not attempting refresh`);
    return false;
  }

  return true;
};

// Function to refresh token
const refreshAuthToken = async () => {
  const refreshToken = localStorage.getItem("REFRESH_TOKEN");
  if (!refreshToken) {
    return Promise.reject("No refresh token");
  }

  // Check if we should attempt a refresh based on retry count
  if (!shouldAttemptRefresh()) {
    return Promise.reject("Max refresh attempts reached");
  }

  // Increment retry count
  const currentRetryCount = parseInt(localStorage.getItem(RETRY_STORAGE_KEY) || "0");
  localStorage.setItem(RETRY_STORAGE_KEY, (currentRetryCount + 1).toString());
  localStorage.setItem(RETRY_TIMESTAMP_KEY, Date.now().toString());

  try {
    const response = await axios.post<TokenResponse>(`${APP_API_URL}/auth/api/v1/auth/refresh-token`, {
      refreshToken,
    });

    const { accessToken, refreshToken: newRefreshToken } = response.data;
    localStorage.setItem("JWT_TOKEN", accessToken);
    localStorage.setItem("REFRESH_TOKEN", newRefreshToken);

    // Reset retry counter on successful refresh
    localStorage.removeItem(RETRY_STORAGE_KEY);
    localStorage.removeItem(RETRY_TIMESTAMP_KEY);

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

    // Extract the request URL path
    const requestUrl = originalRequest.url || '';

    // Only handle 401 errors for non-auth endpoints
    if (error.response?.status === 401 && !requestUrl.match(/^\/auth\/api\/v\d+\/auth/)) {
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

          // Only redirect if hit max retries
          const currentRetryCount = parseInt(localStorage.getItem(RETRY_STORAGE_KEY) || "0");
          if (currentRetryCount >= MAX_REFRESH_RETRIES) {
            window.location.href = "/login";
          }

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