import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";

class DocumentRecommendationService extends BaseService {
  async recommendDocument(documentId: string) {
    return this.handleApiResponse(
      axiosInstance.post(`/document-interaction/api/v1/documents/${documentId}/recommendations`),
    );
  }

  async unrecommendDocument(documentId: string) {
    return this.handleApiResponse(
      axiosInstance.delete(`/document-interaction/api/v1/documents/${documentId}/recommendations`),
    );
  }

  async isDocumentRecommended(documentId: string) {
    return axiosInstance.get(`/document-interaction/api/v1/documents/${documentId}/recommendations/status`);
  }
}

export const documentRecommendationService = new DocumentRecommendationService();
