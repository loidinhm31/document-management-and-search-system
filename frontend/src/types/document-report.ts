export interface DocumentReport {
  id: number;
  documentId: string;
  reportTypeCode: string;
  reportTypeTranslation: {
    en: string;
    vi: string;
  };
  description?: string;
  resolved: boolean;
  createdAt: string;
}

export interface ReportType {
  code: string;
  translations: {
    en: string;
    vi: string;
  };
  description?: string;
}

export interface CreateReportRequest {
  reportTypeCode: string;
  description?: string;
}