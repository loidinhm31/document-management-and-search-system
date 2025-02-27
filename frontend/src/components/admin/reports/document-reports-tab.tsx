import { AlertTriangle, Calendar, Eye, Filter, Loader2, Search, User, X } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { Calendar as CalendarComponent } from "@/components/ui/calendar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { reportService } from "@/services/report.service";
import { ReportType } from "@/types/document-report";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";

export interface DocumentReport {
  documentId: string;
  documentTitle: string;
  documentOwnerId: string;
  documentOwnerUsername: string;
  status: "PENDING" | "RESOLVED" | "UNRESOLVED";
  reportCount: number;
  resolvedBy?: string;
  resolvedByUsername?: string;
  resolvedAt?: string;
  reportDetails: ReportDetail[];
}

export interface ReportDetail {
  reportId: number;
  reporterUserId: string;
  reporterUsername: string;
  reportTypeCode: string;
  description?: string;
  status: "PENDING" | "RESOLVED" | "UNRESOLVED";
  createdAt: string;
}

export default function DocumentReportsTab() {
  const { t, i18n } = useTranslation();
  const { toast } = useToast();

  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [reports, setReports] = useState<DocumentReport[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [selectedReport, setSelectedReport] = useState<DocumentReport | null>(null);
  const [isProcessingReport, setIsProcessingReport] = useState(false);
  const [reportTypes, setReportTypes] = useState<ReportType[]>([]);

  const [filters, setFilters] = useState({
    documentTitle: "",
    reportTypeCode: "",
    reporterUsername: "",
    fromDate: null,
    toDate: null,
    status: "",
    page: 0,
    size: 10,
  });

  const [showReasonsDialog, setShowReasonsDialog] = useState(false);
  const [showProcessDialog, setShowProcessDialog] = useState(false);
  const [newStatus, setNewStatus] = useState<"PENDING" | "RESOLVED" | "UNRESOLVED">(null);

  const loadReportTypes = async () => {
    try {
      const response = await reportService.getDocumentReportTypes();
      setReportTypes(response.data);
    } catch (error) {
      console.error("Error loading report types:", error);
    }
  };

  // Fetch data with filters
  const fetchReports = async () => {
    setLoading(true);
    try {
      const response = await reportService.getDocumentReports({
        ...filters,
        page: currentPage,
      });
      setReports(response.data.content);
      setTotalPages(response.data.totalPages);
    } catch (error) {
      console.error("Error fetching document reports:", error);
      toast({
        title: t("common.error"),
        description: t("admin.reports.documents.fetchError"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  // Process report
  const processReport = async () => {
    if (!selectedReport || !newStatus) return;

    setIsProcessingReport(true);
    try {
      await reportService.updateDocumentReportStatus(selectedReport.documentId, newStatus);

      // Update report in list
      setReports((prevReports) =>
        prevReports.map((report) =>
          report.documentId === selectedReport.documentId ? { ...report, status: newStatus } : report,
        ),
      );

      toast({
        title: t("common.success"),
        description: t("admin.reports.documents.processSuccess"),
        variant: "success",
      });

      setShowProcessDialog(false);
      setNewStatus(null);
    } catch (error) {
      console.error("Error processing report:", error);
      toast({
        title: t("common.error"),
        description: t("admin.reports.documents.processError"),
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
      documentTitle: "",
      reportTypeCode: "",
      reporterUsername: "",
      fromDate: null,
      toDate: null,
      status: "",
      page: 0,
      size: 10,
    });
    setCurrentPage(0);
  };

  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  const handleViewReasons = (report: DocumentReport) => {
    setSelectedReport(report);
    setShowReasonsDialog(true);
  };

  const handleProcessClick = (report: DocumentReport) => {
    setSelectedReport(report);
    setNewStatus(report.status === "PENDING" ? "RESOLVED" : report.status === "RESOLVED" ? "UNRESOLVED" : "RESOLVED");
    setShowProcessDialog(true);
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

  // Initial fetch
  useEffect(() => {
    loadReportTypes();
  }, []);

  useEffect(() => {
    fetchReports();
  }, [currentPage]);

  const handleChangResolvedStatus = (newStatus: "PENDING" | "RESOLVED" | "UNRESOLVED") => {
    setNewStatus(newStatus);
  };

  const mapReportType = (reportTypeCode: string) => {
    return reportTypes?.find((type) => type?.code === reportTypeCode);
  };

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="space-y-4">
        <div className="flex flex-col gap-4 lg:flex-row">
          {/* Document Title */}
          <div className="flex-1 max-w-full">
            <div className="relative">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder={t("admin.reports.documents.filters.documentTitle")}
                value={filters.documentTitle}
                onChange={(e) => setFilters((prev) => ({ ...prev, documentTitle: e.target.value }))}
                className="pl-9 max-w-full"
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
                    : t("admin.reports.documents.filters.fromDate")}
                  {filters.fromDate && (
                    <X
                      className="ml-auto h-4 w-4 cursor-pointer"
                      onClick={(e) => {
                        e.stopPropagation();
                        setFilters(prev => ({ ...prev, fromDate: null }));
                      }}
                    />
                  )}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <CalendarComponent
                  mode="single"
                  selected={filters.fromDate}
                  onSelect={(date) => setFilters(prev => ({ ...prev, fromDate: date }))}
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
                    : t("admin.reports.documents.filters.toDate")}
                  {filters.toDate && (
                    <X
                      className="ml-auto h-4 w-4 cursor-pointer"
                      onClick={(e) => {
                        e.stopPropagation();
                        setFilters(prev => ({ ...prev, toDate: null }));
                      }}
                    />
                  )}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <CalendarComponent
                  mode="single"
                  selected={filters.toDate}
                  onSelect={(date) => setFilters(prev => ({ ...prev, toDate: date }))}
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

          {/* Status Filter */}
          <Select value={filters.status} onValueChange={(value) => setFilters((prev) => ({ ...prev, status: value }))}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder={t("admin.reports.documents.filters.status")} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t("admin.reports.documents.status.allStatuses")}</SelectItem>
              <SelectItem value="PENDING">{t("admin.reports.documents.status.pending")}</SelectItem>
              <SelectItem value="RESOLVED">{t("admin.reports.documents.status.resolved")}</SelectItem>
              <SelectItem value="UNRESOLVED">{t("admin.reports.documents.status.unresolved")}</SelectItem>
            </SelectContent>
          </Select>

          {/* Search and Reset Buttons */}
          <div className="flex gap-2">
            <Button onClick={handleSearch}>
              <Search className="mr-2 h-4 w-4" />
              {t("admin.reports.documents.filters.search")}
            </Button>
            <Button variant="outline" onClick={handleResetFilters}>
              <Filter className="mr-2 h-4 w-4" />
              {t("admin.reports.documents.filters.reset")}
            </Button>
          </div>
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
                      <Skeleton className="h-6 w-16" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-6 w-24" />
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
                <TableRow key={report.documentId}>
                  <TableCell className="font-medium">
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
                  <TableCell>{report.documentOwnerUsername}</TableCell>
                  <TableCell>
                    <Badge variant="secondary">{report.reportCount}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant={
                        report.status === "PENDING"
                          ? "outline"
                          : report.status === "RESOLVED"
                            ? "destructive"
                            : "secondary"
                      }
                    >
                      {t(`admin.reports.documents.status.${report.status.toLowerCase()}`)}
                    </Badge>
                  </TableCell>
                  <TableCell>{report.resolvedByUsername || "-"}</TableCell>
                  <TableCell>
                    <div className="flex gap-2">
                      <Button variant="outline" size="sm" onClick={() => handleViewReasons(report)}>
                        <Eye className="h-4 w-4 mr-1" />
                        {t("admin.reports.documents.actions.viewReasons")}
                      </Button>
                      <Button
                        variant={report.status === "RESOLVED" ? "secondary" : "destructive"}
                        size="sm"
                        onClick={() => handleProcessClick(report)}
                      >
                        <AlertTriangle className="h-4 w-4 mr-1" />
                        {report.status === "PENDING"
                          ? t("admin.reports.documents.actions.process")
                          : report.status === "RESOLVED"
                            ? t("admin.reports.documents.actions.unresolve")
                            : t("admin.reports.documents.actions.resolve")}
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={6} className="text-center py-6">
                  {t("admin.reports.documents.noReports")}
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
            {t("admin.reports.documents.pagination.previous")}
          </Button>
          <span className="flex items-center px-4">
            {t("admin.reports.documents.pagination.pageInfo", {
              current: currentPage + 1,
              total: totalPages,
            })}
          </span>
          <Button
            variant="outline"
            onClick={() => handlePageChange(currentPage + 1)}
            disabled={currentPage === totalPages - 1 || loading}
          >
            {t("admin.reports.documents.pagination.next")}
          </Button>
        </div>
      )}

      {/* Reasons Dialog */}
      <Dialog open={showReasonsDialog} onOpenChange={setShowReasonsDialog}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>
              {t("admin.reports.documents.dialogs.reasons.title", { document: selectedReport?.documentTitle })}
            </DialogTitle>
            <DialogDescription>{t("admin.reports.documents.dialogs.reasons.description")}</DialogDescription>
          </DialogHeader>

          <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-2">
            {selectedReport?.reportDetails && selectedReport.reportDetails.length > 0 ? (
              selectedReport.reportDetails.map((reason, index) => (
                <div key={index} className="border rounded-lg p-4 space-y-2">
                  <div className="flex justify-between">
                    <div className="font-medium">
                      {mapReportType(reason.reportTypeCode)?.translations[i18n.language] ||
                        mapReportType(reason.reportTypeCode)?.translations.en}
                    </div>
                    <div className="text-sm text-muted-foreground">{formatDate(reason.createdAt)}</div>
                  </div>
                  <div className="text-sm text-muted-foreground">
                    {t("admin.reports.documents.dialogs.reasons.reportedBy", { user: reason.reporterUsername })}
                  </div>
                  {reason.description && (
                    <div className="mt-2 text-sm p-2 bg-muted rounded-md">{reason.description}</div>
                  )}
                </div>
              ))
            ) : (
              <div className="text-center py-6 text-muted-foreground">
                {t("admin.reports.documents.dialogs.reasons.noReasons")}
              </div>
            )}
          </div>
        </DialogContent>
      </Dialog>

      {/* Process Dialog */}
      <Dialog open={showProcessDialog} onOpenChange={setShowProcessDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {selectedReport?.status === "PENDING"
                ? t("admin.reports.documents.dialogs.process.title")
                : selectedReport?.status === "RESOLVED"
                  ? t("admin.reports.documents.dialogs.unresolve.title")
                  : t("admin.reports.documents.dialogs.resolve.title")}
            </DialogTitle>
            <DialogDescription>
              {selectedReport?.status === "PENDING"
                ? t("admin.reports.documents.dialogs.process.description", { document: selectedReport?.documentTitle })
                : selectedReport?.status === "RESOLVED"
                  ? t("admin.reports.documents.dialogs.unresolve.description", {
                      document: selectedReport?.documentTitle,
                    })
                  : t("admin.reports.documents.dialogs.resolve.description", {
                      document: selectedReport?.documentTitle,
                    })}
            </DialogDescription>
          </DialogHeader>

          <div className="py-4">
            <Label className="mb-2 block">{t("admin.reports.documents.dialogs.process.selectStatus")}</Label>
            <Select value={newStatus} onValueChange={handleChangResolvedStatus}>
              <SelectTrigger>
                <SelectValue placeholder={t("admin.reports.documents.dialogs.process.selectStatus")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="RESOLVED">
                  {t("admin.reports.documents.status.resolved")} -{" "}
                  {t("admin.reports.documents.dialogs.process.resolvedDescription")}
                </SelectItem>
                <SelectItem value="UNRESOLVED">
                  {t("admin.reports.documents.status.unresolved")} -{" "}
                  {t("admin.reports.documents.dialogs.process.unresolvedDescription")}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setShowProcessDialog(false)} disabled={isProcessingReport}>
              {t("common.cancel")}
            </Button>
            <Button
              variant={newStatus === "RESOLVED" ? "destructive" : "default"}
              onClick={processReport}
              disabled={isProcessingReport || !newStatus}
            >
              {isProcessingReport && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {t("common.confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
