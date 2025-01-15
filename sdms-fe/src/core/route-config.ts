import React, { lazy } from "react";

const Login = lazy(() => import("@/pages/login-page"));
const Register = lazy(() => import("@/pages/register-page"));
const ForgotPassword = lazy(() => import("@/pages/forgot-password-page"));
const Home = lazy(() => import("@/pages/home-page"));
const Profile = lazy(() => import("@/pages/profile-page"));
const UserList = lazy(() => import("@/pages/admin/user-list-page"));
const UserDetails = lazy(() => import("@/pages/admin/user-detail-page"));

export interface Route {
  path: string;
  pageTitle: string;
  component: React.LazyExoticComponent<React.FC>;
  isSecure: boolean;
  permission?: string[];
  subPages?: Route[];
  adminRequired?: boolean;
}

export const RoutePaths = {
  UNAUTHORIZED: "/unauthorized",
  HOME: "/home",
  LOGIN: "/login",
  REGISTER: "/register",
  FORGOT_PASSWORD: "/forgot-password",
  EMPTY: "/empty",
  PROFILE: "/profile",
  ADMIN: {
    USERS: "/admin/users",
    USER_DETAILS: "/admin/users/:userId",
  },
} as const;

export const routes: Route[] = [
  {
    path: RoutePaths.LOGIN,
    pageTitle: "Login",
    component: Login,
    isSecure: false,
  },
  {
    path: RoutePaths.REGISTER,
    pageTitle: "Sign Up",
    component: Register,
    isSecure: false,
  },
  {
    path: RoutePaths.FORGOT_PASSWORD,
    pageTitle: "Forgot Password",
    component: ForgotPassword,
    isSecure: false,
  },
  {
    path: RoutePaths.HOME,
    pageTitle: "pages.home",
    component: Home,
    isSecure: true,
    permission: [],
  },
  {
    path: RoutePaths.PROFILE,
    pageTitle: "pages.profile",
    component: Profile,
    isSecure: true,
    permission: [],
  },
  // Admin Routes
  {
    path: RoutePaths.ADMIN.USERS,
    pageTitle: "pages.admin.users",
    component: UserList,
    isSecure: true,
    adminRequired: true,
    permission: [],
  },
  {
    path: RoutePaths.ADMIN.USER_DETAILS,
    pageTitle: "pages.admin.userDetail",
    component: UserDetails,
    isSecure: true,
    adminRequired: true,
    permission: [],
  },
];

export const getRoutes = (initRoutes = routes): Route[] => {
  const flattenRoutes = (routes: Route[]): Route[] => {
    return routes.reduce<Route[]>((acc, route) => {
      acc.push(route);
      if (route.subPages && route.subPages.length > 0) {
        acc.push(...flattenRoutes(route.subPages));
      }
      return acc;
    }, []);
  };

  return flattenRoutes(initRoutes);
};
