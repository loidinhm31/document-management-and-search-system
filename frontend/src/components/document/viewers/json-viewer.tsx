import { Download } from "lucide-react";
import React, { ReactElement } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";

interface JsonViewerProps {
  content: string;
  onDownload: () => void;
  isDownloading?: boolean;
  loading?: boolean;
}

type LineElement = {
  content: string;
  indent: number;
};

export const JsonViewer: React.FC<JsonViewerProps> = ({ content, onDownload, isDownloading, loading }) => {
  const { t } = useTranslation();

  const processJsonLine = (line: string): LineElement => {
    const indent = line.match(/^\s*/)?.[0].length || 0;
    return {
      content: line.trim(),
      indent,
    };
  };

  const highlightSyntax = (jsonString: string): ReactElement => {
    try {
      const obj = JSON.parse(jsonString);
      const formatted = JSON.stringify(obj, null, 2);
      const lines = formatted.split("\n");

      return (
        <>
          {lines.map((line, index) => {
            const { content, indent } = processJsonLine(line);

            // Apply syntax highlighting
            const highlightedContent = content
              // Highlight keys
              .replace(/"([^"]+)":/g, '<span class="text-blue-600 dark:text-blue-400">"$1"</span>:')
              // Highlight string values
              .replace(/: "([^"]+)"/g, ': <span class="text-green-600 dark:text-green-400">"$1"</span>')
              // Highlight numbers
              .replace(/: (\d+\.?\d*)/g, ': <span class="text-orange-600 dark:text-orange-400">$1</span>')
              // Highlight booleans and null
              .replace(/: (true|false|null)/g, ': <span class="text-purple-600 dark:text-purple-400">$1</span>');

            return (
              <div
                key={`json-line-${index}`}
                className="hover:bg-muted py-[2px]"
                style={{
                  paddingLeft: `${indent * 8}px`,
                }}
                dangerouslySetInnerHTML={{
                  __html: highlightedContent,
                }}
              />
            );
          })}
        </>
      );
    } catch (error) {
      console.info("Error parsing JSON:", error);
      return <>{jsonString}</>;
    }
  };

  return (
    <div className="h-full flex flex-col">
      <div className="flex justify-end p-2 bg-muted">
        <Button onClick={onDownload} variant="outline" size="sm" disabled={isDownloading || loading}>
          <Download className="h-4 w-4 mr-2" />
          {!isDownloading ? t("document.viewer.buttons.download") : t("document.viewer.buttons.downloading")}
        </Button>
      </div>
      <ScrollArea className="flex-1">
        <div className="p-4 text-sm font-mono whitespace-pre bg-background text-foreground">{highlightSyntax(content)}</div>
      </ScrollArea>
    </div>
  );
};