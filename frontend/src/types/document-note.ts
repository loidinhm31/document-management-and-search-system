export interface NoteRequest {
  content: string;
}

export interface NoteResponse {
  id: string;
  documentId: string;
  content: string;
  mentorUsername: string;
  mentorId: string;
  createdAt: Date;
  updatedAt: Date;
  edited: boolean;
}
