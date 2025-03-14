import { Search, User2, UsersRound } from "lucide-react";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import TableSkeleton from "@/components/common/table-skeleton";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { formatDate } from "@/lib/utils";
import { adminService } from "@/services/admin.service";
import { User } from "@/types/auth";

export default function UserList() {
  const { t } = useTranslation();
  const { toast } = useToast();
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);

  const [searchQuery, setSearchQuery] = useState("");
  const [selectedStatus, setSelectedStatus] = useState<string>("all");
  const [selectedRole, setSelectedRole] = useState<string>("all");

  const [appliedSearchQuery, setAppliedSearchQuery] = useState("");
  const [appliedStatus, setAppliedStatus] = useState<string>("all");
  const [appliedRole, setAppliedRole] = useState<string>("all");

  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Add page size options and state
  const pageSizeOptions = [10, 20, 50, 100];
  const [pageSize, setPageSize] = useState(10);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const response = await adminService.getAllUsers({
        search: appliedSearchQuery || undefined,
        enabled: appliedStatus === "active" ? true : appliedStatus === "inactive" ? false : undefined,
        role: appliedRole !== "all" ? appliedRole : undefined,
        page: currentPage,
        size: pageSize, // Use the pageSize state
      });

      setUsers(response.data.content);
      setTotalPages(response.data.totalPages);
      setTotalElements(response.data.totalElements);
    } catch (error) {
      console.error("Error fetching users:", error);
      toast({
        title: t("common.error"),
        description: t("admin.users.messages.fetchError"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  }, [appliedSearchQuery, appliedStatus, appliedRole, currentPage, pageSize, toast, t]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleSearch = () => {
    setCurrentPage(0);
    setAppliedSearchQuery(searchQuery);
    setAppliedStatus(selectedStatus);
    setAppliedRole(selectedRole);
  };

  const handleReset = () => {
    // Reset form input states
    setSearchQuery("");
    setSelectedStatus("all");
    setSelectedRole("all");

    // Reset applied states
    setAppliedSearchQuery("");
    setAppliedStatus("all");
    setAppliedRole("all");

    setCurrentPage(0);
  };

  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
  };

  // Add handler for page size changes
  const handlePageSizeChange = (value: string) => {
    setPageSize(parseInt(value));
    setCurrentPage(0); // Reset to first page when changing page size
  };

  return (
    <div className="container mx-auto py-6">
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <UsersRound className="h-5 w-5" />
            <div>
              <CardTitle>{t("admin.users.title")}</CardTitle>
              <CardDescription>{t("admin.users.description")}</CardDescription>
            </div>
          </div>
        </CardHeader>

        <CardContent className="space-y-6">
          {/* Search and Filters */}
          <div className="flex flex-col gap-4 md:flex-row">
            <div className="flex-1">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  placeholder={t("admin.users.search")}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-9"
                  onKeyDown={(e) => e.key === "Enter" && handleSearch()}
                />
              </div>
            </div>

            <Select value={selectedStatus} onValueChange={setSelectedStatus}>
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder={t("admin.users.filters.status")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{t("common.all")}</SelectItem>
                <SelectItem value="active">{t("admin.users.status.active")}</SelectItem>
                <SelectItem value="inactive">{t("admin.users.status.inactive")}</SelectItem>
              </SelectContent>
            </Select>

            <Select value={selectedRole} onValueChange={setSelectedRole}>
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder={t("admin.users.filters.role")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{t("document.commonSearch.all")}</SelectItem>
                <SelectItem value="ROLE_ADMIN">Admin</SelectItem>
                <SelectItem value="ROLE_MENTOR">Mentor</SelectItem>
                <SelectItem value="ROLE_USER">User</SelectItem>
              </SelectContent>
            </Select>

            <div className="flex gap-2">
              <Button onClick={handleSearch}>{t("document.commonSearch.apply")}</Button>
              <Button variant="outline" onClick={handleReset}>
                {t("document.commonSearch.reset")}
              </Button>
            </div>
          </div>

          {/* Users table */}
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("admin.users.headers.username")}</TableHead>
                  <TableHead>{t("admin.users.headers.email")}</TableHead>
                  <TableHead>{t("admin.users.headers.createdDate")}</TableHead>
                  <TableHead>{t("admin.users.headers.status")}</TableHead>
                  <TableHead className="text-center">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading ? (
                  <TableSkeleton rows={5} cells={5} />
                ) : users.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="h-24 text-center">
                      {t("admin.users.noUser")}
                    </TableCell>
                  </TableRow>
                ) : (
                  users.map((user) => (
                    <TableRow key={user.userId}>
                      <TableCell className="font-medium">{user.username}</TableCell>
                      <TableCell>{user.email}</TableCell>
                      <TableCell>{formatDate(user.createdDate)}</TableCell>
                      <TableCell>
                        <span
                          className={`inline-flex items-center rounded-full px-2 py-1 text-xs font-medium ring-1 ring-inset 
                          ${
                            user.enabled
                              ? "bg-green-50 text-green-700 ring-green-600/20"
                              : "bg-red-50 text-red-700 ring-red-600/20"
                          }`}
                        >
                          {user.enabled ? t("admin.users.status.active") : t("admin.users.status.inactive")}
                        </span>
                      </TableCell>
                      <TableCell className="text-right">
                        <Button variant="ghost" size="sm" asChild className="flex items-center gap-1 h-8">
                          <Link to={`/admin/users/${user.userId}`}>
                            <User2 className="h-4 w-4" />
                            <span>{t("admin.users.actions.viewDetails")}</span>
                          </Link>
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>

          {/* Pagination */}
          <div className="flex flex-col sm:flex-row justify-between items-center gap-4">
            {/* Page Size Selector */}
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">{t("document.discover.pagination.pageSize")}</span>
              <Select value={pageSize.toString()} onValueChange={handlePageSizeChange}>
                <SelectTrigger className="w-[80px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {pageSizeOptions.map((size) => (
                    <SelectItem key={size} value={size.toString()}>
                      {size}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Records info */}
            <div className="text-sm text-muted-foreground">
              {t("document.history.pagination.showing", {
                start: currentPage * pageSize + 1,
                end: Math.min((currentPage + 1) * pageSize, totalElements),
                total: totalElements,
              })}
            </div>

            {/* Page navigation */}
            <div className="flex items-center space-x-2">
              <Button
                variant="outline"
                onClick={() => handlePageChange(currentPage - 1)}
                disabled={currentPage === 0 || loading}
              >
                {t("document.history.pagination.previous")}
              </Button>
              <div className="text-sm">
                {t("document.history.pagination.page", {
                  current: currentPage + 1,
                  total: totalPages || 1,
                })}
              </div>
              <Button
                variant="outline"
                onClick={() => handlePageChange(currentPage + 1)}
                disabled={currentPage >= totalPages - 1 || loading}
              >
                {t("document.history.pagination.next")}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
