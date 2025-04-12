export type ReportStatus = "PENDING" | "RESOLVED" | "REJECTED" | "REMEDIATED";

export const ReportStatusValues = {
  PENDING: "PENDING" as ReportStatus,
  RESOLVED: "RESOLVED" as ReportStatus,
  REJECTED: "REJECTED" as ReportStatus,
  REMEDIATED: "REMEDIATED" as ReportStatus,
};

export interface DocumentReport {
  documentId: string;
  documentOwnerId: string;
  documentTitle: string;
  documentOwnerUsername: string;
  reportCount: number;
  status: ReportStatus;
  processed: boolean;
  reportTypeTranslation?: {
    [key: string]: string;
  };
  description?: string;
  resolvedBy: string | null;
  resolvedByUsername: string | null;
  createdAt: string;
}

export interface ReportReason {
  id: string;
  reportTypeCode: string;
  description: string;
  reporterUsername: string;
  createdAt: string;
}

export interface ReportType {
  code: string;
  translations: {
    [key: string]: string;
  };
  description?: string;
}

export interface CreateReportRequest {
  reportTypeCode: string;
  description?: string;
}

export interface CommentReport {
  commentId: number;
  commenter: string;
  documentId: string;
  documentTitle: string;
  commentContent: string;
  createdAt: Date;
  commentUsername: string;
  reportCount: number;
  status: ReportStatus;
  processed: boolean;
  resolvedBy: string | null;
}

export interface CommentReportDetail {
  id: number;
  documentId: string;
  commentId: number;
  commentContent: string;
  reporterUserId: string;
  reporterUsername: string;
  commentUserId: string;
  commentUsername: string;
  reportTypeCode: string;
  reportTypeTranslation: {
    en: string;
    vi: string;
  };
  description?: string;
  resolved: boolean;
  resolvedBy?: string;
  resolvedByUsername?: string;
  createdAt: string;
  resolvedAt?: string;
}
