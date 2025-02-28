import { Calendar, FileText, Search, User, X } from "lucide-react";
import moment from "moment-timezone";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import CommentReportProcessDialog from "@/components/admin/reports/comment-report-process-dialog";
import CommentReportReasonsDialog from "@/components/admin/reports/comment-report-reasons-dialog";
import TableSkeleton from "@/components/common/table-skeleton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Calendar as CalendarComponent } from "@/components/ui/calendar";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { reportService } from "@/services/report.service";
import { CommentReport, ReportStatus, ReportStatusValues, ReportType } from "@/types/document-report";

interface CommentReportsResponse {
  content: CommentReport[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

const CommentReportsTab = () => {
  const { t, i18n } = useTranslation();
  const { toast } = useToast();
  const navigate = useNavigate();

  const [reports, setReports] = useState<CommentReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);

  const [commentContent, setCommentContent] = useState("");
  const [reportType, setReportType] = useState("all");
  const [status, setStatus] = useState("all");
  const [fromDate, setFromDate] = useState<Date | undefined>(undefined);
  const [toDate, setToDate] = useState<Date | undefined>(undefined);

  const [selectedReport, setSelectedReport] = useState<CommentReport | null>(null);
  const [showReasonsDialog, setShowReasonsDialog] = useState(false);
  const [showResolveDialog, setShowResolveDialog] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [reportTypes, setReportTypes] = useState<ReportType[]>([]);

  useEffect(() => {
    loadReportTypes();
  }, []);

  // Fetch reports on initial load and when filters change
  useEffect(() => {
    fetchReports();
  }, [currentPage]);

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
      const filters = {
        commentContent: commentContent || undefined,
        reportTypeCode: reportType === "all" ? undefined : reportType,
        resolved: status === "all" ? undefined : status === "resolved",
        fromDate: fromDate,
        toDate: toDate,
        page: currentPage,
        size: 10,
      };

      const response = await reportService.getCommentReports(filters);
      const data = response.data as CommentReportsResponse;

      setReports(data.content);
      setTotalPages(data.totalPages);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("admin.reports.comments.fetchError"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = () => {
    setCurrentPage(0);
    fetchReports();
  };

  const handleReset = () => {
    setCommentContent("");
    setReportType("all");
    setStatus("all");
    setFromDate(undefined);
    setToDate(undefined);
    setCurrentPage(0);
    fetchReports();
  };

  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
  };

  const handleViewReasons = (report: CommentReport) => {
    setSelectedReport(report);
    setShowReasonsDialog(true);
  };

  const handleResolveComment = (report: CommentReport) => {
    setSelectedReport(report);
    setShowResolveDialog(true);
  };

  const handleResolveReport = async (report: CommentReport, status: string) => {
    setResolving(true);
    try {
      await reportService.resolveCommentReport(report.commentId, status);
      toast({
        title: t("common.success"),
        description: t("admin.reports.comments.processSuccess"),
        variant: "success",
      });
      fetchReports();
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("admin.reports.comments.processError"),
        variant: "destructive",
      });
    } finally {
      setResolving(false);
      setShowResolveDialog(false);
    }
  };

  const formatDate = (dateString: string) => {
    return moment(dateString).format("DD/MM/YYYY, h:mm a");
  };

  const truncateContent = (content: string, maxLength = 50) => {
    if (content?.length <= maxLength) return content;
    return content?.substring(0, maxLength) + "...";
  };

  const canResolve = (report: CommentReport) => {
    return !report.processed;
  };

  const getStatusBadgeClasses = (status: ReportStatus) => {
    switch (status) {
      case "PENDING":
        return "bg-yellow-100 text-yellow-800 ring-yellow-600/20";
      case "RESOLVED":
        return "bg-red-100 text-red-800 ring-red-600/20";
      case "REJECTED":
        return "bg-gray-100 text-gray-800 ring-gray-600/20";
      default:
        return "bg-gray-100 text-gray-800 ring-gray-600/20";
    }
  };

  return (
    <div className="space-y-4">
      {/* Filter Section */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        <div>
          <Label>{t("admin.reports.comments.filters.commentContent")}</Label>
          <div className="mt-1">
            <Input
              value={commentContent}
              onChange={(e) => setCommentContent(e.target.value)}
              placeholder={t("admin.reports.comments.filters.commentContent")}
            />
          </div>
        </div>

        <div>
          <Label>{t("admin.reports.comments.filters.reportType")}</Label>
          <div className="mt-1">
            <Select value={reportType} onValueChange={setReportType}>
              <SelectTrigger>
                <SelectValue placeholder={t("admin.reports.comments.filters.allTypes")} />
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
          </div>
        </div>

        <div>
          <Label>{t("admin.reports.comments.filters.status")}</Label>
          <div className="mt-1">
            <Select value={status} onValueChange={setStatus}>
              <SelectTrigger>
                <SelectValue placeholder={t("admin.reports.comments.filters.allStatuses")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{t("admin.reports.comments.filters.allStatuses")}</SelectItem>
                <SelectItem value="pending">{t("admin.reports.comments.status.pending")}</SelectItem>
                <SelectItem value="resolved">{t("admin.reports.comments.status.resolved")}</SelectItem>
                <SelectItem value="rejected">{t("admin.reports.comments.status.rejected")}</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div>
          <Label>{t("admin.reports.comments.filters.fromDate")}</Label>
          <div className="mt-1">
            <Popover>
              <PopoverTrigger asChild>
                <Button variant="outline" className="w-full justify-start text-left font-normal">
                  <Calendar className="mr-2 h-4 w-4" />
                  {fromDate ? (
                    formatDate(fromDate.toISOString())
                  ) : (
                    <span>{t("admin.reports.comments.filters.fromDate")}</span>
                  )}
                  {fromDate && (
                    <X
                      className="ml-auto h-4 w-4 cursor-pointer"
                      onClick={(e) => {
                        e.stopPropagation();
                        setFromDate(undefined);
                      }}
                    />
                  )}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <CalendarComponent mode="single" selected={fromDate} onSelect={setFromDate} initialFocus />
              </PopoverContent>
            </Popover>
          </div>
        </div>

        <div>
          <Label>{t("admin.reports.comments.filters.toDate")}</Label>
          <div className="mt-1">
            <Popover>
              <PopoverTrigger asChild>
                <Button variant="outline" className="w-full justify-start text-left font-normal">
                  <Calendar className="mr-2 h-4 w-4" />
                  {toDate ? (
                    formatDate(toDate.toISOString())
                  ) : (
                    <span>{t("admin.reports.comments.filters.toDate")}</span>
                  )}
                  {toDate && (
                    <X
                      className="ml-auto h-4 w-4 cursor-pointer"
                      onClick={(e) => {
                        e.stopPropagation();
                        setToDate(undefined);
                      }}
                    />
                  )}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <CalendarComponent mode="single" selected={toDate} onSelect={setToDate} initialFocus />
              </PopoverContent>
            </Popover>
          </div>
        </div>

        <div className="flex items-end gap-2">
          <Button onClick={handleSearch} className="flex-1">
            <Search className="mr-2 h-4 w-4" />
            {t("admin.reports.comments.filters.search")}
          </Button>
          <Button variant="outline" onClick={handleReset}>
            {t("admin.reports.comments.filters.reset")}
          </Button>
        </div>
      </div>

      {/* Reports Table */}
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
              <TableSkeleton rows={5} cells={7} />
            ) : reports.length > 0 ? (
              reports.map((report, index) => (
                <TableRow key={`${report.commentId}-${index}`}>
                  <TableCell>
                    <div className="flex items-center">
                      <Button
                        variant="link"
                        className="p-0 h-auto flex items-center gap-1"
                        onClick={() => navigate(`/discover/${report.documentId}`)}
                      >
                        <FileText className="mr-2 h-4 w-4 text-muted-foreground" />
                        {truncateContent(report.documentTitle || report.documentId, 30)}
                      </Button>
                    </div>
                  </TableCell>
                  <TableCell>
                    {report.processed && report.status === ReportStatusValues.RESOLVED ? (
                      <span className="text-muted-foreground italic">{t("admin.reports.comments.contentRemoved")}</span>
                    ) : (
                      truncateContent(report.commentContent)
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center">
                      <User className="mr-2 h-4 w-4 text-muted-foreground" />
                      {report.commentUsername}
                    </div>
                  </TableCell>
                  <TableCell>{formatDate(report.commentDate)}</TableCell>
                  <TableCell>
                    <Badge variant="secondary">{report.reportCount}</Badge>
                  </TableCell>
                  <TableCell>
                    <span
                      className={`inline-flex items-center rounded-full px-2 py-1 text-xs font-medium ring-1 ring-inset ${getStatusBadgeClasses(
                        report.status,
                      )}`}
                    >
                      {t(`admin.reports.documents.status.${report.status.toLowerCase()}`)}
                    </span>
                  </TableCell>
                  <TableCell>
                    <div className="flex space-x-2">
                      <Button variant="outline" size="sm" onClick={() => handleViewReasons(report)}>
                        {t("admin.reports.comments.actions.viewReasons")}
                      </Button>

                      {canResolve(report) && (
                        <Button variant="outline" size="sm" onClick={() => handleResolveComment(report)}>
                          {t("admin.reports.comments.actions.resolve")}
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={7} className="h-24 text-center">
                  {t("admin.reports.comments.noReports")}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {reports?.length > 0 && (
        <div className="flex justify-center gap-2">
          <Button
            variant="outline"
            onClick={() => handlePageChange(currentPage - 1)}
            disabled={currentPage === 0 || loading}
          >
            {t("admin.reports.comments.pagination.previous")}
          </Button>
          <div className="flex items-center">
            <span>
              {t("admin.reports.comments.pagination.pageInfo", {
                current: currentPage + 1,
                total: totalPages,
              })}
            </span>
          </div>
          <Button
            variant="outline"
            onClick={() => handlePageChange(currentPage + 1)}
            disabled={currentPage >= totalPages - 1 || loading}
          >
            {t("admin.reports.comments.pagination.next")}
          </Button>
        </div>
      )}

      {/* Dialogs */}
      {selectedReport && (
        <>
          <CommentReportReasonsDialog
            open={showReasonsDialog}
            onOpenChange={setShowReasonsDialog}
            commentId={selectedReport.commentId}
            commentContent={selectedReport.commentContent}
            isResolved={selectedReport.processed}
            reportTypes={reportTypes}
          />

          <CommentReportProcessDialog
            open={showResolveDialog}
            onOpenChange={setShowResolveDialog}
            report={selectedReport}
            onResolve={(resolve) => handleResolveReport(selectedReport, resolve)}
            resolving={resolving}
          />
        </>
      )}
    </div>
  );
};

export default CommentReportsTab;
