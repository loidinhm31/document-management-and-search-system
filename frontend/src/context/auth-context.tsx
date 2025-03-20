import { jwtDecode } from "jwt-decode";
import React, { createContext, useContext, useEffect, useRef, useState } from "react";

import { authService } from "@/services/auth.service";
import { userService } from "@/services/user.service";
import { JwtPayload, TokenResponse, User } from "@/types/auth";

interface AuthContextType {
  token: string | null;
  refreshToken: string | null;
  setAuthData: (data: TokenResponse) => void;
  clearAuthData: () => void;
  currentUser: User | null;
  setCurrentUser: (user: User | null) => void;
  role: string;
  setRole: (role: string) => void;
}

const AuthContext = createContext<AuthContextType>({
  token: null,
  refreshToken: null,
  setAuthData: () => {},
  clearAuthData: () => {},
  currentUser: null,
  setCurrentUser: () => {},
  role: null,
  setRole: () => {},
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(localStorage.getItem("JWT_TOKEN"));
  const [refreshToken, setRefreshToken] = useState<string | null>(localStorage.getItem("REFRESH_TOKEN"));
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [role, setRole] = useState<string>(localStorage.getItem("ROLE"));
  const refreshTimeoutRef = useRef<ReturnType<typeof setTimeout>>();

  const setAuthData = (data: TokenResponse) => {
    // Store tokens
    localStorage.setItem("JWT_TOKEN", data.accessToken);
    localStorage.setItem("REFRESH_TOKEN", data.refreshToken);
    setToken(data.accessToken);
    setRefreshToken(data.refreshToken);

    // Start refresh timer
    startRefreshTimer(data.accessToken);
  };

  const clearAuthData = () => {
    authService.logout(refreshToken).then(() => {
      localStorage.removeItem("JWT_TOKEN");
      localStorage.removeItem("REFRESH_TOKEN");
      localStorage.removeItem("USER");
      localStorage.removeItem("ROLES");
      setToken(null);
      setRefreshToken(null);
      setCurrentUser(null);
      stopRefreshTimer();
    });
  };

  const refreshAuthToken = async () => {
    try {
      if (!refreshToken) {
        throw new Error("No refresh token available");
      }

      const response = await authService.refreshToken(refreshToken);
      const newTokenData = response.data;
      setAuthData(newTokenData);

      console.log("Token refreshed successfully");
    } catch (error) {
      console.info("Failed to refresh token:", error);
      clearAuthData();
      // Optionally redirect to log in
      window.location.href = "/login";
    }
  };

  const startRefreshTimer = (accessToken: string) => {
    // Clear any existing timer
    stopRefreshTimer();

    try {
      const decodedToken = jwtDecode<JwtPayload>(accessToken);
      const expiryTime = decodedToken.exp * 1000; // Convert to milliseconds
      const currentTime = Date.now();

      // Calculate time until token expires (subtract 10 seconds as buffer)
      const timeUntilExpiry = expiryTime - currentTime - 10000;

      console.log("Token will expire in:", timeUntilExpiry / 1000, "seconds");

      if (timeUntilExpiry > 0) {
        refreshTimeoutRef.current = setTimeout(refreshAuthToken, timeUntilExpiry);
      } else {
        // Token is already expired or very close to expiring
        refreshAuthToken();
      }
    } catch (error) {
      console.info("Error starting refresh timer:", error);
    }
  };

  const stopRefreshTimer = () => {
    if (refreshTimeoutRef.current) {
      clearTimeout(refreshTimeoutRef.current);
    }
  };

  const fetchCurrentUser = async () => {
    try {
      const response = await userService.getCurrentUser();
      const userData = response.data;
      setCurrentUser(userData);
      setRole(userData.roles[0]);

      localStorage.setItem("ROLE", userData.roles[0]);
    } catch (error) {
      console.info("Error fetching user data:", error);
      clearAuthData();
    }
  };

  // Initialize the refresh timer if we have a token
  useEffect(() => {
    if (token) {
      startRefreshTimer(token);
      fetchCurrentUser();
    }
    return () => stopRefreshTimer();
  }, [token]);

  const contextValue = {
    token,
    refreshToken,
    setAuthData,
    clearAuthData,
    currentUser,
    setCurrentUser,
    role,
    setRole,
  };

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
}

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};