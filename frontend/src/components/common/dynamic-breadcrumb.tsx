import React from "react";
import { useTranslation } from "react-i18next";
import { useLocation } from "react-router-dom";

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator
} from "@/components/ui/breadcrumb";
import { RoutePaths, routes } from "@/core/route-config";
import { useAppSelector } from "@/store/hook";
import { selectCurrentDocument } from "@/store/slices/document-slice";

export function DynamicBreadcrumb() {
  const location = useLocation();
  const { t } = useTranslation();
  const currentDocument = useAppSelector(selectCurrentDocument);

  const getBreadcrumbs = () => {
    const currentPath = location.pathname;

    // Special handling for discover document detail pages
    if (currentPath.match(/^\/discover\/[^/]+$/)) {
      return [
        {
          path: RoutePaths.HOME,
          label: t("pages.home"),
          isLast: false
        },
        {
          path: currentPath,
          label: currentDocument?.filename || t("pages.document.detail"),
          isLast: true
        }
      ];
    }

    // Special handling for my document detail pages
    if (currentPath.match(/^\/documents\/me\/[^/]+$/)) {
      return [
        {
          path: "/documents/me",
          label: t("pages.my-document"),
          isLast: false,
        },
        {
          path: currentPath,
          label: currentDocument?.filename || t("pages.my-document.detail"),
          isLast: true,
        },
      ];
    }

    // Handle other special path pairs
    const specialPaths = {
      "/documents/me": {
        detailPathRegex: /^\/documents\/me\/[^/]+$/,
        parentPath: "/documents/me",
        parentLabel: "pages.my-document",
        detailLabel: "pages.my-document.detail",
      },
      "/admin/users": {
        detailPathRegex: /^\/admin\/users\/[^/]+$/,
        parentPath: "/admin/users",
        parentLabel: "pages.admin.users",
        detailLabel: "pages.admin.userDetail",
      },
    };

    // Check if current path is part of a special path pair
    for (const [_parentPath, config] of Object.entries(specialPaths)) {
      if (config.detailPathRegex.test(currentPath)) {
        return [
          {
            path: config.parentPath,
            label: t(config.parentLabel),
            isLast: false,
          },
          {
            path: currentPath,
            label: t(config.detailLabel),
            isLast: true,
          },
        ];
      }
    }

    // For all other routes, return single-level breadcrumb
    const currentRoute = routes.find((route) => {
      const pathRegex = new RegExp(
        `^${route.path.replace(/:\w+/g, "[^/]+")}$`
      );
      return pathRegex.test(currentPath);
    });

    if (!currentRoute) return [];

    return [
      {
        path: currentPath,
        label: t(currentRoute.pageTitle),
        isLast: true,
      },
    ];
  };

  const breadcrumbs = getBreadcrumbs();

  if (breadcrumbs.length === 0) return null;

  return (
    <Breadcrumb>
      <BreadcrumbList>
        {breadcrumbs.map((crumb, index) => (
          <React.Fragment key={crumb.path}>
            <BreadcrumbItem>
              {crumb.isLast ? (
                <BreadcrumbPage>{crumb.label}</BreadcrumbPage>
              ) : (
                <BreadcrumbLink href={crumb.path}>{crumb.label}</BreadcrumbLink>
              )}
            </BreadcrumbItem>
            {index < breadcrumbs.length - 1 && <BreadcrumbSeparator />}
          </React.Fragment>
        ))}
      </BreadcrumbList>
    </Breadcrumb>
  );
}