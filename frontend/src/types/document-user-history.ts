export enum UserDocumentActionType {
  ALL = 'ALL',
  UPLOAD_DOCUMENT = "UPLOAD_DOCUMENT",
  VIEW_DOCUMENT = "VIEW_DOCUMENT",
  UPDATE_DOCUMENT = "UPDATE_DOCUMENT",
  UPDATE_DOCUMENT_FILE = "UPDATE_DOCUMENT_FILE",
  DELETE_DOCUMENT = "DELETE_DOCUMENT",
  DOWNLOAD_FILE = "DOWNLOAD_FILE",
  DOWNLOAD_VERSION = "DOWNLOAD_VERSION",
  REVERT_VERSION = "REVERT_VERSION",
  SHARE = "SHARE",
  FAVORITE = "FAVORITE",
  COMMENT = "COMMENT",
  RECOMMENDATION = "RECOMMENDATION",
  NOTE = "NOTE",
}

export interface UserHistoryResponse {
  id: string;
  actionType: UserDocumentActionType;
  documentId: string;
  documentTitle: string;
  detail: string | null;
  version: number | null;
  timestamp: string;
}

export interface UserHistoryPage {
  content: UserHistoryResponse[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export interface UserHistoryFilter {
  actionType?: UserDocumentActionType;
  fromDate?: string;
  toDate?: string;
  documentName?: string;
  page?: number;
  size?: number;
}
