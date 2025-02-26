import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { NoteRequest, NoteResponse } from "@/types/document-note";

class DocumentNoteService extends BaseService {
  createOrUpdateNote(documentId: string, request: NoteRequest) {
    return this.handleApiResponse<NoteResponse>(
      axiosInstance.post(`/document-interaction/api/v1/documents/${documentId}/notes`, request),
    );
  }

  getMentorNote(documentId: string) {
    return this.handleApiResponse<NoteResponse>(
      axiosInstance.get(`/document-interaction/api/v1/documents/${documentId}/notes/creator`),
    );
  }

  hasNote(documentId: string) {
    return this.handleApiResponse<boolean>(
      axiosInstance.get(`/document-interaction/api/v1/documents/${documentId}/notes/status`),
    );
  }

  getAllNotes(documentId: string) {
    return this.handleApiResponse<NoteResponse[]>(
      axiosInstance.get(`/document-interaction/api/v1/documents/${documentId}/notes`),
    );
  }
}

export const documentNoteService = new DocumentNoteService();
