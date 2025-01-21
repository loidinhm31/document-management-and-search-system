import React from "react";

import { DocumentList } from "@/components/document/document-list";
import { DocumentUpload } from "@/components/document/document-upload";

export default function DocumentsPage() {
  return (
    <div className="space-y-6">
      <DocumentUpload />

      <DocumentList />
    </div>
  );
}