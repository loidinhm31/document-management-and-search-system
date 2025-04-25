import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";

class DocumentRecommendationService extends BaseService {
  async recommendDocument(documentId: string, recommend: boolean) {
    return this.handleApiResponse(
      axiosInstance.post(`/document-interaction/api/v1/documents/${documentId}/recommendations?recommend=${recommend}`),
    );
  }

  async isDocumentRecommended(documentId: string) {
    return axiosInstance.get(`/document-interaction/api/v1/documents/${documentId}/recommendations/status`);
  }
}

export const documentRecommendationService = new DocumentRecommendationService();
