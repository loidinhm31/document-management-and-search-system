import { Download } from "lucide-react";
import React, { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { Components } from "react-markdown";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { PluggableList } from "unified";

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
  const components: Components = {
    // Custom table components using shadcn/ui
    table: ({ children, ...props }: { children: ReactNode; [key: string]: any }) => (
      <div className="my-4 w-full overflow-auto">
        <Table {...props}>{children}</Table>
      </div>
    ),
    thead: TableHeader,
    tbody: TableBody,
    tr: TableRow,
    th: ({ children, ...props }: { children: ReactNode; [key: string]: any }) => (
      <TableHead {...props}>{children}</TableHead>
    ),
    td: ({ children, ...props }: { children: ReactNode; [key: string]: any }) => (
      <TableCell {...props}>{children}</TableCell>
    ),
    // Custom code block with syntax highlighting
    code: ({
      _node,
      inline,
      className,
      children,
      ...props
    }: {
      _node?: any;
      inline?: boolean;
      className?: string;
      children: ReactNode;
      [key: string]: any;
    }) => {
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
    blockquote: ({ children }: { children: ReactNode }) => (
      <blockquote className="mt-6 border-l-2 border-muted pl-6 italic">{children}</blockquote>
    ),
    // Custom list styling
    ul: ({ children }: { children: ReactNode }) => <ul className="my-6 ml-6 list-disc [&>li]:mt-2">{children}</ul>,
    ol: ({ children }: { children: ReactNode }) => <ol className="my-6 ml-6 list-decimal [&>li]:mt-2">{children}</ol>,
    // Custom heading styles
    h1: ({ children }: { children: ReactNode }) => (
      <h1 className="scroll-m-20 text-4xl font-extrabold tracking-tight lg:text-5xl mb-4">{children}</h1>
    ),
    h2: ({ children }: { children: ReactNode }) => (
      <h2 className="scroll-m-20 text-3xl font-semibold tracking-tight mb-4">{children}</h2>
    ),
    h3: ({ children }: { children: ReactNode }) => (
      <h3 className="scroll-m-20 text-2xl font-semibold tracking-tight mb-4">{children}</h3>
    ),
    h4: ({ children }: { children: ReactNode }) => (
      <h4 className="scroll-m-20 text-xl font-semibold tracking-tight mb-4">{children}</h4>
    ),
    // Custom paragraph styling
    p: ({ children }: { children: ReactNode }) => <p className="leading-7 [&:not(:first-child)]:mt-6">{children}</p>,
  };

  return (
    <div className="h-full flex flex-col">
      <div className="flex justify-end p-2 bg-muted">
        <Button onClick={onDownload} variant="outline" size="sm" disabled={isDownloading || loading}>
          <Download className="h-4 w-4 mr-2" />
          {!isDownloading ? t("document.viewer.buttons.download") : t("document.viewer.buttons.downloading")}
        </Button>
      </div>
      <ScrollArea className="flex-1 bg-background">
        <div className="p-6">
          <ReactMarkdown remarkPlugins={[remarkGfm] as PluggableList} components={components}>
            {content}
          </ReactMarkdown>
        </div>
      </ScrollArea>
    </div>
  );
};
