import React from 'react';

import { DocumentList } from "@/components/document/document-list";
import { DocumentUpload } from "@/components/document/document-upload";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export default function DocumentsPage() {
  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Upload Document</CardTitle>
          <CardDescription>
            Drag and drop your document or click to browse
          </CardDescription>
        </CardHeader>
        <CardContent>
          <DocumentUpload />
        </CardContent>
      </Card>

      <DocumentList />
    </div>
  );
}