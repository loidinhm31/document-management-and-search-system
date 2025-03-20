import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { ReportStatus, ReportType } from "@/types/document-report";

interface DocumentReportFilter {
  documentTitle?: string;
  uploaderUsername?: string;
  fromDate?: Date;
  toDate?: Date;
  reportTypeCode?: string;
  status?: string;
  page?: number;
  size?: number;
}

interface CommentReportFilter {
  commentContent?: string;
  reportTypeCode?: string;
  fromDate?: Date;
  toDate?: Date;
  resolved?: boolean;
  page?: number;
  size?: number;
}

class ReportService extends BaseService {
  getDocumentReports(filters: DocumentReportFilter = {}) {
    const params = new URLSearchParams();

    if (filters.documentTitle) params.append("documentTitle", filters.documentTitle);
    if (filters.uploaderUsername) params.append("uploaderUsername", filters.uploaderUsername);
    if (filters.reportTypeCode && filters.reportTypeCode !== "all")
      params.append("reportTypeCode", filters.reportTypeCode);
    if (filters.status && filters.status !== "all") params.append("status", filters.status);
    if (filters.fromDate) params.append("fromDate", filters.fromDate.toISOString());
    if (filters.toDate) params.append("toDate", filters.toDate.toISOString());

    params.append("page", String(filters.page || 0));
    params.append("size", String(filters.size || 10));

    return this.handleApiResponse(
      axiosInstance.get(`/document-interaction/api/v1/reports/documents?${params.toString()}`),
    );
  }

  updateDocumentReportStatus(documentId: string, status: string) {
    return this.handleApiResponse(
      axiosInstance.put(`/document-interaction/api/v1/reports/documents/${documentId}/status?status=${status}`),
    );
  }

  getDocumentReportDetail(documentId: string, status: ReportStatus) {
    return this.handleApiResponse(
      axiosInstance.get(`/document-interaction/api/v1/reports/documents/${documentId}`, {
        params: {
          status,
        },
      }),
    );
  }

  getCommentReports(filters: CommentReportFilter = {}) {
    const params = new URLSearchParams();

    if (filters.commentContent) params.append("commentContent", filters.commentContent);
    if (filters.fromDate) params.append("fromDate", filters.fromDate.toISOString());
    if (filters.toDate) params.append("toDate", filters.toDate.toISOString());
    if (filters.reportTypeCode && filters.reportTypeCode !== "all")
      params.append("reportTypeCode", filters.reportTypeCode);
    if (filters.resolved !== null && filters.resolved !== undefined) {
      params.append("resolved", filters.resolved.toString());
    }

    params.append("page", String(filters.page || 0));
    params.append("size", String(filters.size || 10));

    return this.handleApiResponse(
      axiosInstance.get(`/document-interaction/api/v1/reports/comments?${params.toString()}`),
    );
  }

  getCommentReportDetail(commentId: number, status: ReportStatus) {
    return this.handleApiResponse(
      axiosInstance.get(`/document-interaction/api/v1/reports/comments/${commentId}`, {
        params: {
          status,
        },
      }),
    );
  }

  resolveCommentReport(reportId: number, status: string) {
    return this.handleApiResponse(
      axiosInstance.put(`/document-interaction/api/v1/reports/comments/${reportId}/resolve?status=${status}`),
    );
  }

  getDocumentReportTypes() {
    return this.handleApiResponse<ReportType[]>(
      axiosInstance.get("/document-interaction/api/v1/master-data/documents/reports/types"),
    );
  }

  getCommentReportTypes() {
    return this.handleApiResponse<ReportType[]>(
      axiosInstance.get("/document-interaction/api/v1/master-data/comments/reports/types"),
    );
  }
}

export const reportService = new ReportService();
