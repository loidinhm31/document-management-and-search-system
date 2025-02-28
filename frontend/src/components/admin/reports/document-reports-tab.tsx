import { Calendar, Search, UserRound, X } from "lucide-react";
import moment from "moment-timezone";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import DocumentReportProcessDialog from "@/components/admin/reports/document-report-process-dialog";
import DocumentReportReasonsDialog from "@/components/admin/reports/document-report-reasons-dialog";
import { Button } from "@/components/ui/button";
import { Calendar as CalendarComponent } from "@/components/ui/calendar";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { reportService } from "@/services/report.service";
import TableSkeleton from "@/components/common/table-skeleton";
import { CommentReport, DocumentReport, ReportStatus, ReportStatusValues, ReportType } from "@/types/document-report";
import { Badge } from "@/components/ui/badge";
import { useNavigate } from "react-router-dom";

interface DocumentReportsResponse {
  content: DocumentReport[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

const DocumentReportsTab = () => {
  const { t, i18n } = useTranslation();
  const { toast } = useToast();
  const navigate = useNavigate();
  const [reports, setReports] = useState<DocumentReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);

  const [documentTitle, setDocumentTitle] = useState("");
  const [uploaderUsername, setUploaderUsername] = useState("");
  const [reportTypeCode, setReportTypeCode] = useState<string>("all");
  const [status, setStatus] = useState("all");
  const [fromDate, setFromDate] = useState<Date | undefined>(undefined);
  const [toDate, setToDate] = useState<Date | undefined>(undefined);

  const [selectedReport, setSelectedReport] = useState<DocumentReport | null>(null);
  const [showReasonsDialog, setShowReasonsDialog] = useState(false);
  const [showProcessDialog, setShowProcessDialog] = useState(false);
  const [processingReport, setProcessingReport] = useState(false);
  const [reportTypes, setReportTypes] = useState<ReportType[]>([]);

  // Initial fetch
  useEffect(() => {
    loadReportTypes();
  }, []);

  // Fetch reports on initial load and when filters change
  useEffect(() => {
    fetchReports();
  }, [currentPage]);

  const loadReportTypes = async () => {
    try {
      const response = await reportService.getDocumentReportTypes();
      setReportTypes(response.data);
    } catch (error) {
      console.error("Error loading report types:", error);
    }
  };

  const fetchReports = async () => {
    setLoading(true);
    try {
      const filters = {
        documentTitle: documentTitle || undefined,
        uploaderUsername: uploaderUsername || undefined,
        reportTypeCode: reportTypeCode === "all" ? undefined : reportTypeCode,
        status: status === "all" ? undefined : status,
        fromDate: fromDate,
        toDate: toDate,
        page: currentPage,
        size: 10,
      };

      const response = await reportService.getDocumentReports(filters);
      const data = response.data as DocumentReportsResponse;

      setReports(data.content);
      setTotalPages(data.totalPages);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("admin.reports.documents.fetchError"),
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
    setDocumentTitle("");
    setUploaderUsername("");
    setStatus("all");
    setFromDate(undefined);
    setToDate(undefined);
    setCurrentPage(0);
    fetchReports();
  };

  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
  };

  const handleViewReasons = (report: DocumentReport) => {
    setSelectedReport(report);
    setShowReasonsDialog(true);
  };

  const handleProcessReport = (report: DocumentReport) => {
    setSelectedReport(report);
    setShowProcessDialog(true);
  };

  const handleResolveReport = async (report: DocumentReport, status: string) => {
    setProcessingReport(true);
    try {
      await reportService.updateDocumentReportStatus(report.documentId, status);
      toast({
        title: t("common.success"),
        description: t("admin.reports.documents.processSuccess"),
        variant: "success",
      });
      fetchReports();
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("admin.reports.documents.processError"),
        variant: "destructive",
      });
    } finally {
      setProcessingReport(false);
      setShowProcessDialog(false);
    }
  };

  const formatDate = (dateString: string) => {
    return moment(dateString).format("DD/MM/YYYY, h:mm a");
  };

  const canResolve = (report: DocumentReport) => {
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
      case "REMEDIATED":
        return "bg-green-100 text-green-800 ring-green-600/20";
      default:
        return "bg-gray-100 text-gray-800 ring-gray-600/20";
    }
  };

  return (
    <div className="space-y-4">
      {/* Filter Section */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        <div>
          <Label>{t("admin.reports.documents.filters.documentTitle")}</Label>
          <div className="mt-1">
            <Input
              value={documentTitle}
              onChange={(e) => setDocumentTitle(e.target.value)}
              placeholder={t("admin.reports.documents.filters.documentTitle")}
            />
          </div>
        </div>

        <div>
          <Label>{t("admin.reports.documents.filters.uploaderUsername")}</Label>
          <div className="mt-1">
            <Input
              value={uploaderUsername}
              onChange={(e) => setUploaderUsername(e.target.value)}
              placeholder={t("admin.reports.documents.filters.uploaderUsername")}
            />
          </div>
        </div>

        <div>
          <Label>{t("admin.reports.documents.filters.reportType")}</Label>
          <Select value={reportTypeCode} onValueChange={setReportTypeCode}>
            <SelectTrigger>
              <SelectValue placeholder={t("admin.reports.documents.filters.reportType")} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t("admin.reports.documents.filters.allTypes")}</SelectItem>

              {reportTypes.map((type) => (
                <SelectItem key={type.code} value={type.code}>
                  {type.translations[i18n.language] || type.translations.en}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div>
          <Label>{t("admin.reports.documents.filters.status")}</Label>
          <div className="mt-1">
            <Select value={status} onValueChange={setStatus}>
              <SelectTrigger>
                <SelectValue placeholder={t("admin.reports.documents.filters.allStatuses")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{t("admin.reports.documents.filters.allStatuses")}</SelectItem>
                <SelectItem value="PENDING">{t("admin.reports.documents.status.pending")}</SelectItem>
                <SelectItem value="RESOLVED">{t("admin.reports.documents.status.resolved")}</SelectItem>
                <SelectItem value="REJECTED">{t("admin.reports.documents.status.rejected")}</SelectItem>
                <SelectItem value="REMEDIATED">{t("admin.reports.documents.status.remediated")}</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div>
          <Label>{t("admin.reports.documents.filters.fromDate")}</Label>
          <div className="mt-1">
            <Popover>
              <PopoverTrigger asChild>
                <Button variant="outline" className="w-full justify-start text-left font-normal">
                  <Calendar className="mr-2 h-4 w-4" />
                  {fromDate ? (
                    formatDate(fromDate.toISOString())
                  ) : (
                    <span>{t("admin.reports.documents.filters.fromDate")}</span>
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
          <Label>{t("admin.reports.documents.filters.toDate")}</Label>
          <div className="mt-1">
            <Popover>
              <PopoverTrigger asChild>
                <Button variant="outline" className="w-full justify-start text-left font-normal">
                  <Calendar className="mr-2 h-4 w-4" />
                  {toDate ? (
                    formatDate(toDate.toISOString())
                  ) : (
                    <span>{t("admin.reports.documents.filters.toDate")}</span>
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
            {t("admin.reports.documents.filters.search")}
          </Button>
          <Button variant="outline" onClick={handleReset}>
            {t("admin.reports.documents.filters.reset")}
          </Button>
        </div>
      </div>

      {/* Reports Table */}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("admin.reports.documents.table.documentTitle")}</TableHead>
              <TableHead>{t("admin.reports.documents.table.uploader")}</TableHead>
              <TableHead>{t("admin.reports.documents.table.reportCount")}</TableHead>
              <TableHead>{t("admin.reports.documents.table.status")}</TableHead>
              <TableHead>{t("admin.reports.documents.table.resolvedBy")}</TableHead>
              <TableHead>{t("admin.reports.documents.table.actions")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableSkeleton rows={5} cells={6} />
            ) : reports.length > 0 ? (
              reports.map((report, index) => (
                <TableRow key={`${report.documentId}-${report.documentOwnerId}-${index}`}>
                  <TableCell>
                    {report.documentId ? (
                      <div className="flex items-center">
                        <Button
                          variant="link"
                          className="text-wrap"
                          onClick={() => navigate(`/discover/${report.documentId}`)}
                        >
                          {report.documentTitle}
                        </Button>
                      </div>
                    ) : (
                      <span className="text-muted-foreground">{report.documentTitle}</span>
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center">
                      <UserRound className="mr-2 h-4 w-4 text-muted-foreground" />
                      {report.documentOwnerUsername}
                    </div>
                  </TableCell>
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
                    {report.resolvedByUsername ? (
                      <div className="flex items-center">
                        <UserRound className="mr-2 h-4 w-4 text-muted-foreground" />
                        {report.resolvedByUsername}
                      </div>
                    ) : (
                      "-"
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex space-x-2">
                      <Button variant="outline" size="sm" onClick={() => handleViewReasons(report)}>
                        {t("admin.reports.documents.actions.viewReasons")}
                      </Button>

                      {canResolve(report) && (
                        <Button variant="outline" size="sm" onClick={() => handleProcessReport(report)}>
                          {t("admin.reports.documents.actions.process")}
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={6} className="h-24 text-center">
                  {t("admin.reports.documents.noReports")}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {reports.length > 0 && (
        <div className="flex justify-center gap-2">
          <Button
            variant="outline"
            onClick={() => handlePageChange(currentPage - 1)}
            disabled={currentPage === 0 || loading}
          >
            {t("admin.reports.documents.pagination.previous")}
          </Button>
          <div className="flex items-center">
            <span>
              {t("admin.reports.documents.pagination.pageInfo", {
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
            {t("admin.reports.documents.pagination.next")}
          </Button>
        </div>
      )}

      {/* Document Report Reasons Dialog */}
      {selectedReport && (
        <DocumentReportReasonsDialog
          open={showReasonsDialog}
          onOpenChange={setShowReasonsDialog}
          documentId={selectedReport.documentId}
          documentTitle={selectedReport.documentTitle}
          reportTypes={reportTypes}
          processed={selectedReport.processed}
        />
      )}

      {/* Document Report Process Dialog */}
      {selectedReport && (
        <DocumentReportProcessDialog
          open={showProcessDialog}
          onOpenChange={setShowProcessDialog}
          report={selectedReport}
          onResolve={(status) => handleResolveReport(selectedReport, status)}
          processing={processingReport}
        />
      )}
    </div>
  );
};

export default DocumentReportsTab;
