import { Activity, FileText, Filter, Search } from "lucide-react";
import moment from "moment-timezone";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import DatePicker from "@/components/common/date-picker";
import TableSkeleton from "@/components/common/table-skeleton";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import { documentService } from "@/services/document.service";
import { UserDocumentActionType, UserHistoryFilter, UserHistoryResponse } from "@/types/document-user-history";

export default function DocumentUserHistoryPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { toast } = useToast();

  const [loading, setLoading] = useState(true);
  const [histories, setHistories] = useState<UserHistoryResponse[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);

  const pageSizeOptions = [10, 20, 50, 100];

  const [filters, setFilters] = useState<UserHistoryFilter>({
    page: 0,
    size: 20,
  });

  const [searchInput, setSearchInput] = useState("");
  const [selectedActionType, setSelectedActionType] = useState<UserDocumentActionType | undefined>(undefined);
  const [selectedFromDate, setSelectedFromDate] = useState<Date | undefined>(undefined);
  const [selectedToDate, setSelectedToDate] = useState<Date | undefined>(undefined);
  const [dateRangeError, setDateRangeError] = useState<string | null>(null);

  // Load histories with current filters
  const loadHistories = useCallback(async () => {
    setLoading(true);
    try {
      const response = await documentService.getUserHistory({
        ...filters,
        page: currentPage,
      });

      setHistories(response.content);
      setTotalPages(response.totalPages);
    } catch (error) {
      console.error("Error loading history:", error);
      toast({
        title: t("common.error"),
        description: t("document.history.fetchError"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  }, [filters, currentPage, toast, t]);

  // Load initial data
  useEffect(() => {
    loadHistories();
  }, [loadHistories]);

  // Validate date range
  const validateDateRange = useCallback(() => {
    if (selectedFromDate && selectedToDate) {
      // Create date objects for midnight on the selected dates for proper comparison
      const fromDate = new Date(
        selectedFromDate.getFullYear(),
        selectedFromDate.getMonth(),
        selectedFromDate.getDate(),
      );

      const toDate = new Date(selectedToDate.getFullYear(), selectedToDate.getMonth(), selectedToDate.getDate());

      // Compare dates
      if (fromDate > toDate) {
        setDateRangeError(t("document.history.validation.dateRange"));
      } else {
        setDateRangeError(null);
      }
    } else {
      // If both dates aren't selected, no error
      setDateRangeError(null);
    }
  }, [selectedFromDate, selectedToDate, t]);

  // Check validation whenever dates change
  useEffect(() => {
    validateDateRange();
  }, [selectedFromDate, selectedToDate, validateDateRange]);

  const handleSearch = () => {
    // Reset to first page when applying new filters
    setCurrentPage(0);

    // Update filters with all temporary values
    const updatedFilters: UserHistoryFilter = {
      ...filters,
      page: 0,
      documentName: searchInput || undefined,
      actionType: selectedActionType,
      fromDate: selectedFromDate
        ? new Date(
            selectedFromDate.getFullYear(),
            selectedFromDate.getMonth(),
            selectedFromDate.getDate(),
            0,
            0,
            0,
          ).toISOString()
        : undefined,
      toDate: selectedToDate
        ? new Date(
            selectedToDate.getFullYear(),
            selectedToDate.getMonth(),
            selectedToDate.getDate(),
            23,
            59,
            59,
            999,
          ).toISOString()
        : undefined,
    };

    setFilters(updatedFilters);
  };

  // Handle filter reset
  const handleReset = () => {
    setSearchInput("");
    setSelectedActionType(undefined);
    setSelectedFromDate(undefined);
    setSelectedToDate(undefined);
    // dateRangeError will be cleared by the useEffect that monitors date changes
    setCurrentPage(0);
    setFilters({
      page: 0,
      size: 20,
    });
  };

  // Handle page change
  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
  };

  // Handle page size change
  const handlePageSizeChange = (size: string) => {
    setCurrentPage(0); // Reset to first page when changing page size
    setFilters((prev) => ({
      ...prev,
      size: parseInt(size),
    }));
  };

  // Format timestamp
  const formatTimestamp = (timestamp: string) => {
    return moment(timestamp).format("DD/MM/YYYY, h:mm a");
  };

  // Get action type display text and badge variant
  const getActionTypeDetails = (type: UserDocumentActionType) => {
    const actionMap: Record<
      UserDocumentActionType,
      {
        label: string;
        variant: string;
      }
    > = {
      [UserDocumentActionType.UPLOAD_DOCUMENT]: {
        label: t("document.history.actionTypes.uploadDocument"),
        variant: "bg-primary/10 text-primary ring-primary/30",
      },
      [UserDocumentActionType.VIEW_DOCUMENT]: {
        label: t("document.history.actionTypes.viewDocument"),
        variant: "bg-secondary/10 text-secondary ring-secondary/30",
      },
      [UserDocumentActionType.UPDATE_DOCUMENT]: {
        label: t("document.history.actionTypes.updateDocument"),
        variant: "bg-secondary/15 text-secondary ring-secondary/30",
      },
      [UserDocumentActionType.UPDATE_DOCUMENT_FILE]: {
        label: t("document.history.actionTypes.updateDocumentFile"),
        variant: "bg-secondary/20 text-secondary ring-secondary/30",
      },
      [UserDocumentActionType.DELETE_DOCUMENT]: {
        label: t("document.history.actionTypes.deleteDocument"),
        variant: "bg-destructive/10 text-destructive ring-destructive/30",
      },
      [UserDocumentActionType.DOWNLOAD_FILE]: {
        label: t("document.history.actionTypes.downloadFile"),
        variant: "bg-accent/10 text-accent ring-accent/30",
      },
      [UserDocumentActionType.DOWNLOAD_VERSION]: {
        label: t("document.history.actionTypes.downloadVersion"),
        variant: "bg-accent/15 text-accent ring-accent/30",
      },
      [UserDocumentActionType.REVERT_VERSION]: {
        label: t("document.history.actionTypes.revertVersion"),
        variant: "bg-accent/20 text-accent ring-accent/30",
      },
      [UserDocumentActionType.SHARE]: {
        label: t("document.history.actionTypes.share"),
        variant: "bg-primary/15 text-primary ring-primary/30",
      },
      [UserDocumentActionType.FAVORITE]: {
        label: t("document.history.actionTypes.favorite"),
        variant: "bg-accent/25 text-accent ring-accent/30",
      },
      [UserDocumentActionType.COMMENT]: {
        label: t("document.history.actionTypes.comment"),
        variant: "bg-secondary/25 text-secondary ring-secondary/30",
      },
      [UserDocumentActionType.RECOMMEND]: {
        label: t("document.history.actionTypes.recommend"),
        variant: "bg-primary/20 text-primary ring-primary/30",
      },
      [UserDocumentActionType.ADD_NOTE]: {
        label: t("document.history.actionTypes.addNote"),
        variant: "bg-secondary/30 text-secondary ring-secondary/30",
      },
    };

    return actionMap[type] || { label: type, variant: "bg-muted text-muted-foreground" };
  };

  // Navigate to document view
  const handleDocumentClick = (documentId: string) => {
    navigate(`/discover/${documentId}`);
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Activity className="h-5 w-5" />
          <div>
            <CardTitle className="flex items-center gap-2">{t("document.history.title")}</CardTitle>
            <CardDescription>{t("document.history.description")}</CardDescription>
          </div>
        </div>
      </CardHeader>

      <CardContent className="space-y-6">
        {/* Filters */}
        <div className="space-y-4">
          <div className="flex flex-col gap-4 lg:flex-row">
            <div className="flex-1">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder={t("document.history.searchPlaceholder")}
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                  className="pl-9"
                />
              </div>
            </div>

            <Select
              value={selectedActionType}
              onValueChange={(value) => setSelectedActionType(value as UserDocumentActionType)}
            >
              <SelectTrigger className="w-[200px]">
                <SelectValue placeholder={t("document.history.filters.actionType")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{t("document.history.filters.allActions")}</SelectItem>
                {Object.values(UserDocumentActionType).map((type) => (
                  <SelectItem key={type} value={type}>
                    {getActionTypeDetails(type).label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <div className="flex flex-col gap-2">
              <div className="flex gap-2">
                <DatePicker
                  value={selectedFromDate}
                  onChange={setSelectedFromDate}
                  placeholder={t("document.history.filters.fromDate")}
                  clearAriaLabel="Clear from date"
                />

                <DatePicker
                  value={selectedToDate}
                  onChange={setSelectedToDate}
                  placeholder={t("document.history.filters.toDate")}
                  clearAriaLabel="Clear to date"
                />
              </div>

              {/* Date Range Error Display */}
              {dateRangeError && (
                <div className="rounded-md bg-destructive/15 px-3 py-2 text-sm text-destructive">
                  <span>{dateRangeError}</span>
                </div>
              )}
            </div>

            <div className="flex gap-2">
              <Button onClick={handleSearch} disabled={!!dateRangeError}>
                <Search className="mr-2 h-4 w-4" />
                {t("document.history.filters.search")}
              </Button>

              <Button variant="outline" onClick={handleReset}>
                <Filter className="mr-2 h-4 w-4" />
                {t("document.history.filters.reset")}
              </Button>
            </div>
          </div>
        </div>

        {/* History Table */}
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("document.history.tableHeaders.actionType")}</TableHead>
                <TableHead>{t("document.history.tableHeaders.documentTitle")}</TableHead>
                <TableHead className="hidden md:table-cell">{t("document.history.tableHeaders.details")}</TableHead>
                <TableHead className="hidden lg:table-cell">{t("document.history.tableHeaders.version")}</TableHead>
                <TableHead>{t("document.history.tableHeaders.timestamp")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableSkeleton rows={5} cells={5} />
              ) : histories.length > 0 ? (
                histories.map((history) => {
                  const { label, variant } = getActionTypeDetails(history.actionType);
                  return (
                    <TableRow key={history.id}>
                      <TableCell>
                        <span
                          className={cn(
                            "inline-flex items-center rounded-full px-2 py-1 text-xs font-medium ring-1 ring-inset",
                            variant,
                          )}
                        >
                          {label}
                        </span>
                      </TableCell>
                      <TableCell className="font-medium">
                        {history.documentId ? (
                          <div className="flex items-center">
                            <FileText className="mr-2 h-4 w-4 text-muted-foreground" />
                            <Button
                              variant="link"
                              className="p-0 h-auto"
                              onClick={() => handleDocumentClick(history.documentId)}
                            >
                              {history.documentTitle}
                            </Button>
                          </div>
                        ) : (
                          <span className="text-muted-foreground">
                            {history.documentTitle || t("document.history.documentNotAvailable")}
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="hidden md:table-cell">{history.detail || "-"}</TableCell>
                      <TableCell className="hidden lg:table-cell">
                        {history.version !== null ? history.version + 1 : "-"}
                      </TableCell>
                      <TableCell>{formatTimestamp(history.timestamp)}</TableCell>
                    </TableRow>
                  );
                })
              ) : (
                <TableRow>
                  <TableCell colSpan={5} className="text-center py-6">
                    {t("document.history.noResults")}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>

        <div className="flex flex-col sm:flex-row justify-between items-center gap-4">
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">{t("document.discover.pagination.pageSize")}</span>
            <Select value={filters.size?.toString()} onValueChange={handlePageSizeChange}>
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

          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">
              {t("document.history.pagination.showing", {
                start: currentPage * filters.size + 1,
                end: Math.min((currentPage + 1) * filters.size, histories.length + currentPage * filters.size),
                total: totalPages * filters.size,
              })}
            </span>
          </div>
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
  );
}
