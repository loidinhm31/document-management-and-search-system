import * as React from "react";

import { useSidebar } from "@/components/ui/sidebar";

export function LogoHeader() {
  const { state } = useSidebar();
  const isCollapsed = state === "collapsed";

  return (
    <div className={`flex items-center ${isCollapsed ? "justify-center" : ""}`}>
      {/* If using an image logo */}
      {/*<img*/}
      {/*  src="/logo.png"*/}
      {/*  alt="Logo"*/}
      {/*  className={`${isCollapsed ? "w-6 h-6" : "w-8 h-8"}`} // Smaller logo when collapsed*/}
      {/*/>*/}
      {/* Or if using text logo */}

      <p className={`font-semibold ${isCollapsed ? "text-sm" : "text-base"}`}>DMS FUNiX</p>
    </div>
  );
}
