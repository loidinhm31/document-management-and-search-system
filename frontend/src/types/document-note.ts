export interface NoteRequest {
  content: string;
}

export interface NoteResponse {
  id: string;
  documentId: string;
  content: string;
  mentorUsername: string;
  createdAt: string;
  updatedAt: string;
  edited: boolean;
}