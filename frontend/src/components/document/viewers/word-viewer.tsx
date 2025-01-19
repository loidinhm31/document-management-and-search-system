import { Download } from "lucide-react";
import React from "react";
import { Button } from "@/components/ui/button";

interface WordViewerProps {
  content: string;
  onDownload: () => void;
}

export const WordViewer: React.FC<WordViewerProps> = ({ content, onDownload }) => {
  return (
    <div className="h-full flex flex-col">
      <div className="flex justify-end p-2 bg-muted">
        <Button onClick={onDownload} variant="outline" size="sm">
          <Download className="h-4 w-4 mr-2" />
          Download
        </Button>
      </div>
      <div
        className="flex-1 overflow-auto bg-white p-4"
        dangerouslySetInnerHTML={{ __html: content }}
      />
    </div>
  );
};