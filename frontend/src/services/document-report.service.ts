import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { CreateReportRequest, DocumentReport } from "@/types/document-report";

class DocumentReportService extends BaseService {
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
}

export const documentReportService = new DocumentReportService();
