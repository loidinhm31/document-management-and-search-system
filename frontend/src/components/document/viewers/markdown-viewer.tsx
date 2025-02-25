import { Download } from "lucide-react";
import React from "react";
import { useTranslation } from "react-i18next";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

interface MarkdownViewerProps {
  content: string;
  onDownload: () => void;
  isDownloading?: boolean;
  loading?: boolean;
}

export const MarkdownViewer: React.FC<MarkdownViewerProps> = ({ content, onDownload, isDownloading, loading }) => {
  const { t } = useTranslation();

  // Custom components for markdown rendering
  const components = {
    // Custom table components using shadcn/ui
    table: ({ children, ...props }) => (
      <div className="my-4 w-full overflow-auto">
        <Table {...props}>{children}</Table>
      </div>
    ),
    thead: TableHeader,
    tbody: TableBody,
    tr: TableRow,
    th: ({ children, ...props }) => <TableHead {...props}>{children}</TableHead>,
    td: ({ children, ...props }) => <TableCell {...props}>{children}</TableCell>,
    // Custom code block with syntax highlighting
    code: ({ _node, inline, className, children, ...props }) => {
      const match = /language-(\w+)/.exec(className || "");
      const language = match ? match[1] : "";

      if (inline) {
        return (
          <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-sm font-semibold" {...props}>
            {children}
          </code>
        );
      }

      return (
        <div className="my-4 w-full overflow-auto rounded-lg bg-muted p-4">
          <pre className="text-sm">
            <code className={language ? `language-${language}` : ""} {...props}>
              {children}
            </code>
          </pre>
        </div>
      );
    },
    // Custom blockquote styling
    blockquote: ({ children }) => (
      <blockquote className="mt-6 border-l-2 border-muted pl-6 italic">{children}</blockquote>
    ),
    // Custom list styling
    ul: ({ children }) => <ul className="my-6 ml-6 list-disc [&>li]:mt-2">{children}</ul>,
    ol: ({ children }) => <ol className="my-6 ml-6 list-decimal [&>li]:mt-2">{children}</ol>,
    // Custom heading styles
    h1: ({ children }) => (
      <h1 className="scroll-m-20 text-4xl font-extrabold tracking-tight lg:text-5xl mb-4">{children}</h1>
    ),
    h2: ({ children }) => <h2 className="scroll-m-20 text-3xl font-semibold tracking-tight mb-4">{children}</h2>,
    h3: ({ children }) => <h3 className="scroll-m-20 text-2xl font-semibold tracking-tight mb-4">{children}</h3>,
    h4: ({ children }) => <h4 className="scroll-m-20 text-xl font-semibold tracking-tight mb-4">{children}</h4>,
    // Custom paragraph styling
    p: ({ children }) => <p className="leading-7 [&:not(:first-child)]:mt-6">{children}</p>,
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
        <div className="p-6">
          <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
            {content}
          </ReactMarkdown>
        </div>
      </ScrollArea>
    </div>
  );
};
