import { Calendar, Loader2 } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { adminService } from "@/services/admin.service";

export default function UserList() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { toast } = useToast();

  const [loading, setLoading] = useState(true);
  const [users, setUsers] = useState([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    fetchUsers();
  }, [searchQuery, statusFilter, roleFilter, currentPage]);

  const fetchUsers = async () => {
    try {
      const response = await adminService.getAllUsers({
        search: searchQuery,
        enabled: statusFilter === "active" ? true : statusFilter === "inactive" ? false : undefined,
        role: roleFilter,
        page: currentPage,
        size: 10,
      });

      const { content, totalPages: total } = response.data;
      setUsers(content);
      setTotalPages(total);
    } catch (error) {
      console.log("Error:", error);
      toast({
        title: t("common.error"),
        description: t("admin.users.messages.fetchError"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString();
  };

  if (loading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  return (
    <Card>
      <CardHeader className="space-y-1">
        <CardTitle>{t("admin.users.title")}</CardTitle>
        <CardDescription>{t("admin.users.description")}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="mb-4 flex gap-4">
          <Input
            placeholder={t("admin.users.search")}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="max-w-xs"
          />
          <Select
            value={statusFilter}
            onValueChange={(value) => {
              setStatusFilter(value === "all" ? "" : value);
            }}
          >
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder={t("admin.users.filters.status")}>
                {statusFilter === "" ? "All" : statusFilter === "active" ? "Active" : "Inactive"}
              </SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              <SelectItem value="active">Active</SelectItem>
              <SelectItem value="inactive">Inactive</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={roleFilter}
            onValueChange={(value) => {
              setRoleFilter(value === "all" ? "" : value);
            }}
          >
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder={t("admin.users.filters.role")}>
                {roleFilter === "" ? "All" : roleFilter === "ROLE_USER" ? "User" : "Admin"}
              </SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              <SelectItem value="ROLE_USER">User</SelectItem>
              <SelectItem value="ROLE_ADMIN">Admin</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("admin.users.headers.username")}</TableHead>
                <TableHead>{t("admin.users.headers.email")}</TableHead>
                <TableHead>{t("admin.users.headers.createdDate")}</TableHead>
                <TableHead>{t("admin.users.headers.status")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.map((user) => (
                <TableRow key={user.userId} onClick={() => navigate(`${user.userId}`)} className="cursor-pointer hover:bg-muted">                  <TableCell className="font-medium">{user.username}</TableCell>
                  <TableCell>{user.email}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Calendar className="h-4 w-4 text-muted-foreground" />
                      {formatDate(user.createdDate)}
                    </div>
                  </TableCell>
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
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>

        <div className="mt-4 flex justify-center gap-2">
          <Button
            variant="outline"
            onClick={() => setCurrentPage((prev) => Math.max(0, prev - 1))}
            disabled={currentPage === 0}
          >
            Previous
          </Button>
          <span className="flex items-center px-4">
                {t("document.discover.pagination.pageInfo", {
                  current: users.length > 0 ? currentPage + 1 : 0,
                  total: totalPages
                })}
            </span>
          <Button
            variant="outline"
            onClick={() => setCurrentPage((prev) => Math.min(totalPages - 1, prev + 1))}
            disabled={currentPage === totalPages - 1}
          >
            Next
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
