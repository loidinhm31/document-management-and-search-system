import React, { createContext, useContext, useEffect, useState } from "react";

import { useToast } from "@/hooks/use-toast";
import { authService } from "@/services/auth.service";
import { User } from "@/types/auth";

interface AuthContextType {
  token: string | null;
  setToken: (token: string | null) => void;
  currentUser: User | null;
  setCurrentUser: (user: User | null) => void;
  isAdmin: boolean;
  setIsAdmin: (isAdmin: boolean) => void;
}

const AuthContext = createContext<AuthContextType>({
  token: null,
  setToken: () => {},
  currentUser: null,
  setCurrentUser: () => {},
  isAdmin: false,
  setIsAdmin: () => {},
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(localStorage.getItem("JWT_TOKEN"));
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [isAdmin, setIsAdmin] = useState<boolean>(localStorage.getItem("IS_ADMIN") === "true");

  const fetchUser = async () => {
    try {
      const response = await authService.getCurrentUser();
      const userData = response.data.data;

      // Check if user has admin role
      const isUserAdmin = userData.roles.includes("ROLE_ADMIN");
      if (isUserAdmin) {
        localStorage.setItem("IS_ADMIN", "true");
        setIsAdmin(true);
      } else {
        localStorage.removeItem("IS_ADMIN");
        setIsAdmin(false);
      }

      setCurrentUser(userData);
    } catch (error: any) {
      // Error handling is already done by the service
      if (error?.response?.status === 401) {
        localStorage.removeItem("JWT_TOKEN");
        localStorage.removeItem("USER");
        localStorage.removeItem("IS_ADMIN");
        setToken(null);
        setCurrentUser(null);
        setIsAdmin(false);
      }
    }
  };

  // Fetch user data when token changes or component mounts
  useEffect(() => {
    if (token) {
      fetchUser();
    } else {
      // Clear user data if there's no token
      setCurrentUser(null);
      setIsAdmin(false);
    }
  }, [token]);

  return (
    <AuthContext.Provider
      value={{
        token,
        setToken,
        currentUser,
        setCurrentUser,
        isAdmin,
        setIsAdmin,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
