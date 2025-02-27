import { AlertTriangle, Calendar, CheckCircle, Eye, FileText, Filter, Loader2, Search, X } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Calendar as CalendarComponent } from "@/components/ui/calendar";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { reportService } from "@/services/report.service";
import { ReportType } from "@/types/document-report";
import { Badge } from "@/components/ui/badge";

interface CommentReport {
  id: number;
  commentId: number;
  commentContent: string;
  commentUsername: string;
  documentId: string;
  documentTitle: string;
  createdAt: string;
  reportCount: number;
  resolved: boolean;
}

interface CommentReportDetail {
  id: number;
  documentId: string;
  commentId: number;
  commentContent: string;
  reporterUserId: string;
  reporterUsername: string;
  commentUserId: string;
  commentUsername: string;
  reportTypeCode: string;
  reportTypeTranslation: {
    en: string;
    vi: string;
  };
  description?: string;
  resolved: boolean;
  resolvedBy?: string;
  resolvedByUsername?: string;
  createdAt: string;
  resolvedAt?: string;
}

export default function CommentReportsTab() {
  const { t, i18n } = useTranslation();
  const { toast } = useToast();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [reports, setReports] = useState<CommentReport[]>([]);
  const [reportDetail, setReportDetail] = useState<CommentReportDetail | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [selectedReport, setSelectedReport] = useState<CommentReport | null>(null);
  const [isProcessingReport, setIsProcessingReport] = useState(false);

  const [filters, setFilters] = useState({
    commentContent: "",
    reportTypeCode: "",
    fromDate: null,
    toDate: null,
    resolved: null,
    page: 0,
    size: 10,
  });

  const [showReasonsDialog, setShowReasonsDialog] = useState(false);
  const [showResolveDialog, setShowResolveDialog] = useState(false);
  const [reportTypes, setReportTypes] = useState<ReportType[]>([]);

  useEffect(() => {
    loadReportTypes();
  }, []);

  const loadReportTypes = async () => {
    try {
      const response = await reportService.getCommentReportTypes();
      setReportTypes(response.data);
    } catch (error) {
      console.error("Error loading report types:", error);
    }
  };

  const fetchReports = async () => {
    setLoading(true);
    try {
      const response = await reportService.getCommentReports({
        ...filters,
        page: currentPage,
      });
      setReports(response.data.content);
      setTotalPages(response.data.totalPages);
    } catch (error) {
      console.error("Error fetching comment reports:", error);
      toast({
        title: t("common.error"),
        description: t("admin.reports.comments.fetchError"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const fetchReportDetail = async (reportId: number) => {
    setLoadingDetail(true);
    try {
      const response = await reportService.getCommentReportDetail(reportId);
      setReportDetail(response.data);
    } catch (error) {
      console.error("Error fetching report detail:", error);
      toast({
        title: t("common.error"),
        description: t("admin.reports.comments.fetchDetailError"),
        variant: "destructive",
      });
      setReportDetail(null);
    } finally {
      setLoadingDetail(false);
    }
  };

  const resolveCommentReport = async () => {
    if (!selectedReport) return;

    setIsProcessingReport(true);
    try {
      const newResolvedStatus = !selectedReport.resolved;
      await reportService.resolveCommentReport(selectedReport.id, newResolvedStatus);

      // Update report in list
      setReports((prevReports) =>
        prevReports.map((report) =>
          report.id === selectedReport.id ? { ...report, resolved: newResolvedStatus } : report,
        ),
      );

      toast({
        title: t("common.success"),
        description: newResolvedStatus
          ? t("admin.reports.comments.resolveSuccess")
          : t("admin.reports.comments.unresolveSuccess"),
        variant: "success",
      });

      setShowResolveDialog(false);
    } catch (error) {
      console.error("Error resolving comment report:", error);
      toast({
        title: t("common.error"),
        description: t("admin.reports.comments.resolveError"),
        variant: "destructive",
      });
    } finally {
      setIsProcessingReport(false);
    }
  };

  const handleSearch = () => {
    setCurrentPage(0);
    fetchReports();
  };

  const handleResetFilters = () => {
    setFilters({
      commentContent: "",
      reportTypeCode: "",
      fromDate: null,
      toDate: null,
      resolved: null,
      page: 0,
      size: 10,
    });
    setCurrentPage(0);
  };

  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  const handleViewReasons = async (report: CommentReport) => {
    setSelectedReport(report);
    setShowReasonsDialog(true);
    // Fetch report details
    await fetchReportDetail(report.id);
  };

  const handleResolveClick = (report: CommentReport) => {
    setSelectedReport(report);
    setShowResolveDialog(true);
  };

  const handleDocumentClick = (documentId: string) => {
    navigate(`/discover/${documentId}`);
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const mapReportType = (reportTypeCode: string) => {
    return reportTypes?.find((type) => type?.code === reportTypeCode);
  };

  // Initial fetch
  useEffect(() => {
    fetchReports();
  }, [currentPage]);

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="space-y-4">
        <div className="flex flex-col gap-4 lg:flex-row">
          {/* Comment Content */}
          <div className="flex-1">
            <div className="relative">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder={t("admin.reports.comments.filters.commentContent")}
                value={filters.commentContent}
                onChange={(e) => setFilters((prev) => ({ ...prev, commentContent: e.target.value }))}
                className="pl-9"
              />
            </div>
          </div>

          {/* Date Range */}
          <div className="flex gap-2">
            <Popover>
              <PopoverTrigger asChild>
                <Button variant="outline" className="flex-1 justify-start text-left">
                  <Calendar className="mr-2 h-4 w-4" />
                  {filters.fromDate
                    ? new Date(filters.fromDate).toLocaleDateString()
                    : t("admin.reports.comments.filters.fromDate")}
                  {filters.fromDate && (
                    <X
                      className="ml-auto h-4 w-4 cursor-pointer"
                      onClick={(e) => {
                        e.stopPropagation();
                        setFilters((prev) => ({ ...prev, fromDate: null }));
                      }}
                    />
                  )}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <CalendarComponent
                  mode="single"
                  selected={filters.fromDate}
                  onSelect={(date) => setFilters((prev) => ({ ...prev, fromDate: date }))}
                  initialFocus
                />
              </PopoverContent>
            </Popover>

            <Popover>
              <PopoverTrigger asChild>
                <Button variant="outline" className="flex-1 justify-start text-left">
                  <Calendar className="mr-2 h-4 w-4" />
                  {filters.toDate
                    ? new Date(filters.toDate).toLocaleDateString()
                    : t("admin.reports.comments.filters.toDate")}
                  {filters.toDate && (
                    <X
                      className="ml-auto h-4 w-4 cursor-pointer"
                      onClick={(e) => {
                        e.stopPropagation();
                        setFilters((prev) => ({ ...prev, toDate: null }));
                      }}
                    />
                  )}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <CalendarComponent
                  mode="single"
                  selected={filters.toDate}
                  onSelect={(date) => setFilters((prev) => ({ ...prev, toDate: date }))}
                  initialFocus
                />
              </PopoverContent>
            </Popover>
          </div>

          {/* Report Type */}
          <Select
            value={filters.reportTypeCode}
            onValueChange={(value) => setFilters((prev) => ({ ...prev, reportTypeCode: value }))}
          >
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder={t("admin.reports.comments.filters.reportType")} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t("admin.reports.comments.filters.allTypes")}</SelectItem>

              {reportTypes.map((type) => (
                <SelectItem key={type.code} value={type.code}>
                  {type.translations[i18n.language] || type.translations.en}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {/* Resolved Status */}
          <Select
            value={filters.resolved === null ? "" : filters.resolved.toString()}
            onValueChange={(value) => {
              const resolvedValue = value === "" ? null : value === "true";
              setFilters((prev) => ({ ...prev, resolved: resolvedValue }));
            }}
          >
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder={t("admin.reports.comments.filters.status")} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t("admin.reports.comments.filters.allStatuses")}</SelectItem>
              <SelectItem value="false">{t("admin.reports.comments.status.pending")}</SelectItem>
              <SelectItem value="true">{t("admin.reports.comments.status.resolved")}</SelectItem>
            </SelectContent>
          </Select>

          {/* Search and Reset Buttons */}
          <div className="flex gap-2">
            <Button onClick={handleSearch}>
              <Search className="mr-2 h-4 w-4" />
              {t("admin.reports.comments.filters.search")}
            </Button>
            <Button variant="outline" onClick={handleResetFilters}>
              <Filter className="mr-2 h-4 w-4" />
              {t("admin.reports.comments.filters.reset")}
            </Button>
          </div>
        </div>
      </div>

      {/* Comments Reports Table */}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("admin.reports.comments.table.document")}</TableHead>
              <TableHead>{t("admin.reports.comments.table.comment")}</TableHead>
              <TableHead>{t("admin.reports.comments.table.commenter")}</TableHead>
              <TableHead>{t("admin.reports.comments.table.commentDate")}</TableHead>
              <TableHead>{t("admin.reports.comments.table.reportCount")}</TableHead>
              <TableHead>{t("admin.reports.comments.table.status")}</TableHead>
              <TableHead>{t("admin.reports.comments.table.actions")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              Array(5)
                .fill(null)
                .map((_, index) => (
                  <TableRow key={`loading-${index}`}>
                    <TableCell>
                      <Skeleton className="h-6 w-full" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-6 w-24" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-6 w-full" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-6 w-32" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-6 w-16" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-6 w-24" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-6 w-32" />
                    </TableCell>
                  </TableRow>
                ))
            ) : reports.length > 0 ? (
              reports.map((report) => (
                <TableRow key={report.id}>
                  <TableCell>
                    <Button
                      variant="link"
                      className="p-0 h-auto flex items-center gap-1"
                      onClick={() => handleDocumentClick(report.documentId)}
                    >
                      <FileText className="h-3.5 w-3.5" />
                      {report.documentTitle || report.documentId}
                    </Button>
                  </TableCell>
                  <TableCell className="max-w-[200px] truncate">
                    {report.resolved ? (
                      <span className="italic text-muted-foreground">{t("admin.reports.comments.contentRemoved")}</span>
                    ) : (
                      report.commentContent
                    )}
                  </TableCell>
                  <TableCell>{report.commentUsername}</TableCell>
                  <TableCell>{formatDate(report.createdAt)}</TableCell>
                  <TableCell>
                    <Badge variant="secondary">{report.reportCount}</Badge>
                  </TableCell>
                  <TableCell>
                    <span
                      className={`inline-flex items-center rounded-full px-2 py-1 text-xs font-medium ring-1 ring-inset 
                        ${
                        report.resolved
                          ? "bg-green-50 text-green-700 ring-green-600/20"
                          : "bg-red-50 text-red-700 ring-red-600/20"
                      }`}
                    >
                       {report.resolved
                         ? t("admin.reports.comments.status.resolved")
                         : t("admin.reports.comments.status.pending")}
                    </span>
                  </TableCell>
                  <TableCell>
                    <div className="flex gap-2">
                      <Button variant="outline" size="sm" onClick={() => handleViewReasons(report)}>
                        <Eye className="h-4 w-4 mr-1" />
                        {t("admin.reports.comments.actions.viewReasons")}
                      </Button>
                      <Button
                        variant={report.resolved ? "secondary" : "destructive"}
                        size="sm"
                        disabled={report.resolved}
                        onClick={() => handleResolveClick(report)}
                      >
                        <CheckCircle className="h-4 w-4 mr-1" />
                        {report.resolved
                          ? t("admin.reports.comments.actions.resolved")
                          : t("admin.reports.comments.actions.resolve")}
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={7} className="text-center py-6">
                  {t("admin.reports.comments.noReports")}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {reports.length > 0 && (
        <div className="flex justify-center gap-2 mt-4">
          <Button
            variant="outline"
            onClick={() => handlePageChange(currentPage - 1)}
            disabled={currentPage === 0 || loading}
          >
            {t("admin.reports.comments.pagination.previous")}
          </Button>
          <span className="flex items-center px-4">
            {t("admin.reports.comments.pagination.pageInfo", {
              current: currentPage + 1,
              total: totalPages,
            })}
          </span>
          <Button
            variant="outline"
            onClick={() => handlePageChange(currentPage + 1)}
            disabled={currentPage === totalPages - 1 || loading}
          >
            {t("admin.reports.comments.pagination.next")}
          </Button>
        </div>
      )}

      {/* Reasons Dialog */}
      <Dialog open={showReasonsDialog} onOpenChange={setShowReasonsDialog}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>{t("admin.reports.comments.dialogs.reasons.title")}</DialogTitle>
            <DialogDescription>{t("admin.reports.comments.dialogs.reasons.description")}</DialogDescription>
          </DialogHeader>

          <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-2">
            {loadingDetail ? (
              // Loading skeleton for report details
              Array(3)
                .fill(null)
                .map((_, index) => (
                  <div key={`loading-detail-${index}`} className="border rounded-lg p-4 space-y-2">
                    <div className="flex justify-between">
                      <Skeleton className="h-5 w-32" />
                      <Skeleton className="h-5 w-24" />
                    </div>
                    <Skeleton className="h-4 w-48" />
                    <Skeleton className="h-16 w-full" />
                  </div>
                ))
            ) : reportDetail ? (
              <div className="border rounded-lg p-4 space-y-4">
                <div className="flex justify-between">
                  <div className="font-medium">
                    {mapReportType(reportDetail.reportTypeCode)?.translations[i18n.language] || mapReportType(reportDetail.reportTypeCode)?.translations.en}
                  </div>
                  <div className="text-sm text-muted-foreground">{formatDate(reportDetail.createdAt)}</div>
                </div>
                <div className="text-sm text-muted-foreground">
                  {t("admin.reports.comments.dialogs.reasons.reportedBy", { user: reportDetail.reporterUsername })}
                </div>

                <div className="bg-muted rounded-md p-3">
                  <div className="text-sm font-medium mb-2">{t("admin.reports.comments.dialogs.reasons.comment")}:</div>
                  <div className="text-sm p-2 bg-background rounded-md">{reportDetail.commentContent}</div>
                </div>

                {reportDetail.description && (
                  <div className="mt-2">
                    <div className="text-sm font-medium mb-1">
                      {t("admin.reports.comments.dialogs.reasons.description")}:
                    </div>
                    <div className="text-sm p-2 bg-muted rounded-md">{reportDetail.description}</div>
                  </div>
                )}

                <div className="bg-muted rounded-md p-3 flex flex-col gap-2">
                  <div className="text-sm">
                    {t("admin.reports.comments.dialogs.reasons.status")}:
                    <span
                      className={`inline-flex items-center rounded-full px-2 py-1 text-xs font-medium ring-1 ring-inset 
                        ${
                        reportDetail.resolved
                          ? "bg-green-50 text-green-700 ring-green-600/20"
                          : "bg-red-50 text-red-700 ring-red-600/20"
                      }`}
                    >
                       {reportDetail.resolved
                         ? t("admin.reports.comments.status.resolved")
                         : t("admin.reports.comments.status.pending")}
                    </span>
                  </div>

                  {reportDetail.resolved && reportDetail.resolvedByUsername && (
                    <div className="text-sm">
                      {t("admin.reports.comments.dialogs.reasons.resolvedBy", {
                        user: reportDetail.resolvedByUsername,
                      })}
                      {reportDetail.resolvedAt && (
                        <span className="ml-2 text-muted-foreground">({formatDate(reportDetail.resolvedAt)})</span>
                      )}
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div className="text-center py-6 text-muted-foreground">
                {t("admin.reports.comments.dialogs.reasons.noReasons")}
              </div>
            )}
          </div>
        </DialogContent>
      </Dialog>

      {/* Resolve Dialog */}
      <Dialog open={showResolveDialog} onOpenChange={setShowResolveDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {t("admin.reports.comments.dialogs.resolve.title")}
            </DialogTitle>
            <DialogDescription>
              {t("admin.reports.comments.dialogs.resolve.description")}
            </DialogDescription>
          </DialogHeader>

          <div className="py-4">
            <div className="text-sm text-muted-foreground bg-muted p-4 rounded-md">
              {t("admin.reports.comments.dialogs.pending.warning")}
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setShowResolveDialog(false)} disabled={isProcessingReport}>
              {t("common.cancel")}
            </Button>
            <Button
              variant="default"
              onClick={resolveCommentReport}
              disabled={isProcessingReport}
            >
              {isProcessingReport && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {t("admin.reports.comments.dialogs.resolve.confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
