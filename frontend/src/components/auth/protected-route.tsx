import React from "react";
import { Navigate } from "react-router-dom";

import { useAuth } from "@/context/auth-context";

interface ProtectedRouteProps {
  children: React.ReactNode;
  permission?: string[];
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, permission }) => {
  const { token, role } = useAuth();

  // Navigate to login page to an unauthenticated
  if (!token) {
    return <Navigate to="/login" />;
  }
  console.log("permission", permission);
  console.log("role", role);
  console.log("permission", permission.includes(role));

  // Navigate to access-denied page if a user try to access the admin page
  if (token && permission.length > 0 && !permission?.includes(role)) {
    return <Navigate to="/access-denied" />;
  }

  return <>{children}</>;
};

export default ProtectedRoute;
