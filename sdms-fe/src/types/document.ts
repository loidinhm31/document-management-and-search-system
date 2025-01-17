export enum DocumentType {
  PDF = "PDF",
  WORD = "WORD",
  TEXT = "TEXT",
  OTHER = "OTHER"
}

export interface Document {
  id: string;
  filename: string;
  fileSize: number;
  documentType: DocumentType;
  mimeType: string;
  userId: string;
  metadata: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentUploadResponse {
  success: boolean;
  data: Document;
}

export interface DocumentSearchResponse {
  content: Document[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}