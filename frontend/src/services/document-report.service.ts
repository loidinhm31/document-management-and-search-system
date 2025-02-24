import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { CreateReportRequest, DocumentReport, ReportType } from "@/types/document-report";

class DocumentReportService extends BaseService {
  getReportTypes() {
    return this.handleApiResponse<ReportType[]>(
      axiosInstance.get("/document-interaction/api/v1/master-data/reports/types")
    );
  }

  createReport(documentId: string, request: CreateReportRequest) {
    return this.handleApiResponse<DocumentReport>(
      axiosInstance.post(`/document-interaction/api/v1/documents/${documentId}/reports`, request)
    );
  }

  async getUserReport(documentId: string) {
    return axiosInstance.get(`/document-interaction/api/v1/documents/${documentId}/reports/user`);
  }
}

export const documentReportService = new DocumentReportService();