export enum DocumentType {
  PDF = "PDF",
  WORD = "WORD",
  WORD_DOCX = "WORD_DOCX",
  EXCEL = "EXCEL",
  EXCEL_XLSX = "EXCEL_XLSX",
  POWERPOINT = "POWERPOINT",
  POWERPOINT_PPTX = "POWERPOINT_PPTX",
  TEXT_PLAIN = "TEXT_PLAIN",
  CSV = "CSV",
  XML = "XML",
  JSON = "JSON",
  MARKDOWN = "MARKDOWN",
}

export const MAX_FILE_SIZE = 25 * 1024 * 1024 // 10MB in bytes

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
  sharingType?: string;
  sharedWith?: string[];
  language?: string;

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

export interface DocumentVersion {
  versionNumber: number;
  filePath: string;
  thumbnailPath?: string;
  filename: string;
  fileSize: number;
  mimeType: string;
  documentType: DocumentType;
  status: DocumentStatus;
  language?: string;
  extractedMetadata?: Record<string, string>;
  processingError?: string;
  createdBy: string;
  createdAt: Date;
}

export const ACCEPT_TYPE_MAP = {
  "application/pdf": [".pdf"],
  "application/x-pdf": [".pdf"],
  "application/msword": [".doc"],
  "application/vnd.ms-word": [".doc"],
  "application/x-msword": [".doc"],
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document": [".docx"],
  "application/vnd.ms-word.document.macroenabled.12": [".docx"],
  "application/vnd.ms-excel": [".xls"],
  "application/msexcel": [".xls"],
  "application/x-msexcel": [".xls"],
  "application/x-ms-excel": [".xls"],
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": [".xlsx"],
  "application/vnd.ms-excel.sheet.macroenabled.12": [".xlsx"],
  "application/vnd.ms-powerpoint": [".ppt"],
  "application/mspowerpoint": [".ppt"],
  "application/x-mspowerpoint": [".ppt"],
  "application/vnd.openxmlformats-officedocument.presentationml.presentation": [".pptx"],
  "application/vnd.ms-powerpoint.presentation.macroenabled.12": [".pptx"],
  "text/plain": [".txt"],
  "text/x-log": [".txt"],
  "text/x-java-source": [".txt"],
  "text/csv": [".csv"],
  "text/x-csv": [".csv"],
  "application/csv": [".csv"],
  "application/x-csv": [".csv"],
  "application/xml": [".xml"],
  "text/xml": [".xml"],
  "application/json": [".json"],
  "application/x-json": [".json"],
  "text/json": [".json"],
  "text/markdown": [".md"],
  "text/x-markdown": [".md"],
  "application/markdown": [".md"],
}