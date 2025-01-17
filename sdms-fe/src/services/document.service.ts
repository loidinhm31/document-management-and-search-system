import axiosInstance from "@/services/document.axios.config";
import { BaseService } from "@/services/base.service";
import { Document, DocumentUploadResponse, DocumentSearchResponse } from "@/types/document";
import { ApiResponse } from "@/types/api";

class DocumentService extends BaseService {
  private baseURL = "http://localhost:8080/api/v1/documents";

  uploadDocument(file: File, metadata?: Record<string, string>) {
    const formData = new FormData();
    formData.append("file", file);
    if (metadata) {
      formData.append("metadata", JSON.stringify(metadata));
    }

    return this.handleApiResponse<DocumentUploadResponse>(
      axiosInstance.post(this.baseURL, formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      })
    );
  }

  getDocument(id: string) {
    return this.handleApiResponse<Document>(
      axiosInstance.get(`${this.baseURL}/${id}`)
    );
  }

  downloadDocument(id: string) {
    return axiosInstance.get(`${this.baseURL}/${id}`, {
      responseType: "blob",
    });
  }

  searchDocuments(query: string, page = 0, size = 10) {
    return axiosInstance.get(`${this.baseURL}/search`, {
      params: {
        query,
        page,
        size,
      },
    })
  }

  updateDocument(id: string, metadata: Record<string, string>) {
    return this.handleApiResponse(
      axiosInstance.put(`${this.baseURL}/${id}/metadata`, metadata)
    );
  }

  deleteDocument(id: string) {
    return this.handleApiResponse(
      axiosInstance.delete(`${this.baseURL}/${id}`)
    );
  }
}

export const documentService = new DocumentService();
