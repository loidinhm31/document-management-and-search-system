export enum DocumentType {
  PDF = "PDF",
  WORD = "WORD",
  WORD_DOCX = "WORD_DOCX",
  EXCEL = "EXCEL",
  EXCEL_XLSX = "EXCEL_XLSX",
  POWERPOINT = "POWERPOINT",
  POWERPOINT_PPTX = "POWERPOINT_PPTX",
  TEXT_PLAIN = "TEXT_PLAIN",
  RTF = "RTF",
  CSV = "CSV",
  XML = "XML",
  JSON = "JSON"
}

export interface DocumentInformation {
  id: string;
  status: DocumentStatus;
  filename: string;
  filePath: string;
  thumbnailPath?: string;
  fileSize: number;
  mimeType: string;
  documentType: DocumentType;
  content?: string;
  summary?: string;
  major: string;
  courseCode: string;
  courseLevel: string;
  category: string;
  tags?: string[];
  extractedMetadata?: Record<string, string>;
  userId: string;
  createdAt: Date;
  updatedAt: Date;
  createdBy: string;
  updatedBy: string;
  deleted: boolean;
  processingError: string;
  highlights?: string[];

  // Add version-related fields
  currentVersion: number;
  versions: DocumentVersion[];
}
export interface DocumentSearchResponse {
  content: DocumentInformation[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface DocumentMetadataUpdate {
  summary?: string;
  courseCode?: string;
  major?: string;
  level?: string;
  category?: string;
  tags?: string[];
}

export enum DocumentStatus {
  PENDING = "PENDING",
  PROCESSING = "PROCESSING",
  COMPLETED = "COMPLETED",
  FAILED = "FAILED"
}

export interface MasterData {
  id: string;
  type: string;
  code: string;
  translations: {
    en: string;
    vi: string;
  };
  description?: string;
  isActive: boolean;
}

export interface CategoryPrediction {
  category: string;
  confidence: number;
}

export interface DocumentVersion {
  versionNumber: number;
  filePath: string;
  thumbnailPath?: string;
  filename: string;
  fileSize: number;
  mimeType: string;
  status: DocumentStatus;
  language?: string;
  extractedMetadata?: Record<string, string>;
  processingError?: string;
  createdBy: string;
  createdAt: Date;
}

export interface VersionHistoryResponse {
  versions: DocumentVersion[];
  currentVersion: number;
  totalVersions: number;
}