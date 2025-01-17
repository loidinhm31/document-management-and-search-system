import axios, { AxiosError, AxiosHeaders, AxiosResponse, CreateAxiosDefaults } from "axios";
import { TokenResponse } from "@/types/auth";
import { ApiResponse } from "@/types/api";

const config: CreateAxiosDefaults = {
  baseURL: "http://localhost:8080/api",
  headers: new AxiosHeaders({
    "Content-Type": "application/json",
    Accept: "application/json",
  }),
  withCredentials: true,
};

const documentAxiosInstance = axios.create(config);

// Add request interceptor to attach token
documentAxiosInstance.interceptors.request.use(
  async (config) => {
    const token = localStorage.getItem("JWT_TOKEN");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Add response interceptor for token refresh
documentAxiosInstance.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config;
    if (!originalRequest) {
      return Promise.reject(error);
    }

    if (error.response?.status === 401) {
      // Handle token refresh using auth service
      try {
        const refreshToken = localStorage.getItem("REFRESH_TOKEN");
        if (!refreshToken) {
          throw new Error("No refresh token available");
        }

        const response = await axios.post<ApiResponse<TokenResponse>>(
          "http://localhost:9090/api/v1/auth/refresh-token",
          { refreshToken }
        );

        const { accessToken, refreshToken: newRefreshToken } = response.data.data;
        localStorage.setItem("JWT_TOKEN", accessToken);
        localStorage.setItem("REFRESH_TOKEN", newRefreshToken);

        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return documentAxiosInstance(originalRequest);
      } catch (refreshError) {
        // Clear auth data and redirect to login if refresh fails
        localStorage.removeItem("JWT_TOKEN");
        localStorage.removeItem("REFRESH_TOKEN");
        localStorage.removeItem("USER");
        localStorage.removeItem("IS_ADMIN");
        window.location.href = "/login";
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default documentAxiosInstance;