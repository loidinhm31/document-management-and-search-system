import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { CategoryPrediction } from "@/types/document";

interface ModelPredictionResponse {
  predictions: CategoryPrediction[];
}

class PredictionService extends BaseService {
  getDocumentPrediction(text: string, filename: string, language = "en") {
    return this.handleApiResponse<ModelPredictionResponse>(
      axiosInstance.post(`/document-interaction/api/v1/prediction/classification`, null, {
        params: {
          text,
          filename,
          language
        }
      })
    );
  }
}

export const predictionService = new PredictionService();