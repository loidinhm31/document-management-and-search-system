import { Separator } from "@radix-ui/react-separator";
import { Loader2 } from "lucide-react";
import React, { Suspense } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";

import { AppSidebar } from "@/components/app-sidebar";
import OAuth2RedirectHandler from "@/components/auth/oauth2-redirect-handler";
import ProtectedRoute from "@/components/auth/protected-route";
import { DynamicBreadcrumb } from "@/components/dynamic-breadcrumb";
import { ThemeProvider } from "@/components/theme-provider";
import { Card, CardContent } from "@/components/ui/card";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import { Toaster } from "@/components/ui/toaster";
import { AuthProvider } from "@/context/auth-context";
import { ProcessingProvider } from "@/context/processing-provider";
import { ProcessingStatus } from "@/context/processing-status";
import { getRoutes } from "@/core/route-config";

const LoadingFallback = () => (
  <div className="flex h-screen w-full items-center justify-center">
    <Card className="w-[300px]">
      <CardContent className="flex items-center justify-center p-6">
        <Loader2 className="h-6 w-6 animate-spin" />
        <span className="ml-2">Loading...</span>
      </CardContent>
    </Card>
  </div>
);

const AuthenticatedLayout = ({ children }: { children: React.ReactNode }) => (
  <SidebarProvider>
    <AppSidebar />
    <SidebarInset>
      <header
        className="flex h-16 shrink-0 items-center gap-2 transition-[width,height] ease-linear group-has-[[data-collapsible=icon]]/sidebar-wrapper:h-12">
        <div className="flex items-center gap-2 px-4">
          <SidebarTrigger className="-ml-1" />
          <Separator orientation="vertical" className="mr-2 h-4" />
          <DynamicBreadcrumb />
        </div>
      </header>
      <div className="flex flex-1 flex-col gap-4 p-4 pt-0">
        <div className="min-h-[100vh] flex-1 rounded-xl bg-muted/50 md:min-h-min">{children}</div>
      </div>
    </SidebarInset>
  </SidebarProvider>
);

export default function App() {
  return (
    <BrowserRouter>
      <ThemeProvider defaultTheme="light" storageKey="ui-theme">
        <AuthProvider>
          <ProcessingProvider>
            <Suspense fallback={<LoadingFallback />}>
              <Routes>
                <Route path="/" element={<Navigate to="/home" replace />} />
                <Route path="/oauth2/redirect" element={<OAuth2RedirectHandler />} />
                {getRoutes().map((route) => {
                  const Component = route.component;
                  return (
                    <Route
                      key={route.path}
                      path={route.path}
                      element={
                        route.isSecure ? (
                          <ProtectedRoute permission={route.permission}>
                            <AuthenticatedLayout children={<Component />} />
                          </ProtectedRoute>
                        ) : (
                          <Component />
                        )
                      }
                    />
                  );
                })}
                <Route path="*" element={<Navigate to="/home" replace />} />
              </Routes>
              <ProcessingStatus />
              <Toaster />
            </Suspense>
          </ProcessingProvider>
        </AuthProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}
