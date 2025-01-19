import { Download, FileText } from "lucide-react";
import React from "react";
import { Button } from "@/components/ui/button";

interface UnsupportedViewerProps {
  documentType: string;
  onDownload: () => void;
}

export const UnsupportedViewer: React.FC<UnsupportedViewerProps> = ({ documentType, onDownload }) => {
  return (
    <div className="h-full flex flex-col items-center justify-center gap-4">
      <FileText className="h-16 w-16 text-muted-foreground" />
      <p className="text-muted-foreground">
        Preview not available for {documentType.toLowerCase()}
      </p>
      <Button onClick={onDownload} variant="outline">
        Download File
      </Button>
    </div>
  );
};