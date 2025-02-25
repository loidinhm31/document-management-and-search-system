import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { CreateReportRequest, DocumentReport, ReportType } from "@/types/document-report";

class DocumentReportService extends BaseService {
  getDocumentReportTypes() {
    return this.handleApiResponse<ReportType[]>(
      axiosInstance.get("/document-interaction/api/v1/master-data/documents/reports/types"),
    );
  }

  createDocumentReport(documentId: string, request: CreateReportRequest) {
    return this.handleApiResponse<DocumentReport>(
      axiosInstance.post(`/document-interaction/api/v1/documents/${documentId}/reports`, request),
    );
  }

  async getDocumentUserReport(documentId: string) {
    return axiosInstance.get(`/document-interaction/api/v1/documents/${documentId}/reports/user`);
  }

  createCommentReport(documentId: string, commentId: number, data: { reportTypeCode: string; description?: string }) {
    return this.handleApiResponse(
      axiosInstance.post(`/document-interaction/api/v1/documents/${documentId}/comments/${commentId}`, data),
    );
  }

  async getCommentUserReport(documentId: string, commentId: number) {
    return axiosInstance.get(`/document-interaction/api/v1/documents/${documentId}/comments/${commentId}/user`);
  }

  getCommentReportTypes() {
    return this.handleApiResponse<ReportType[]>(
      axiosInstance.get("/document-interaction/api/v1/master-data/comments/reports/types"),
    );
  }
}

export const documentReportService = new DocumentReportService();
