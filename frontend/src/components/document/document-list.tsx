import { Download, Eye, Loader2, Trash2 } from "lucide-react";
import React, { useEffect, useState } from "react";

import { DocumentViewer } from "@/components/document/document-viewer";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { searchService } from "@/services/search.service";
import { Document } from "@/types/document";
import DocumentSearch from "@/components/document/document-search";


export const DocumentList = () => {
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedDoc, setSelectedDoc] = useState<Document>(null);
  const { toast } = useToast();

  // Initial data load
  useEffect(() => {
    fetchDocuments();
  }, []);

  const fetchDocuments = async (query = "") => {
    setLoading(true);
    try {
      const response = await searchService.searchDocuments(query);
      setDocuments(response.data.content);
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to fetch documents",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await documentService.deleteDocument(id);
      toast({
        title: "Success",
        description: "Document deleted successfully",
        variant: "success",
      });
      fetchDocuments();
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to delete document",
        variant: "destructive",
      });
    }
  };

  const handleDownload = async (id: string, filename: string) => {
    try {
      const response = await documentService.downloadDocument(id);
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to download document",
        variant: "destructive",
      });
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Documents</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="mb-4">
            <DocumentSearch
              onSearch={fetchDocuments}
              className="max-w-xs"
            />
          </div>

          {loading ? (
            <div className="flex justify-center p-4">
              <Loader2 className="w-6 h-6 animate-spin" />
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Size</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {documents.map((doc) => (
                  <TableRow key={doc.id}>
                    <TableCell className="font-medium">{doc.filename}</TableCell>
                    <TableCell>{doc.documentType}</TableCell>
                    <TableCell>{(doc.fileSize / 1024).toFixed(2)} KB</TableCell>
                    <TableCell>{new Date(doc.createdAt).toLocaleDateString()}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-2">
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => setSelectedDoc(doc)}
                        >
                          <Eye className="w-4 h-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleDownload(doc.id, doc.filename)}
                        >
                          <Download className="w-4 h-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleDelete(doc.id)}
                        >
                          <Trash2 className="w-4 h-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {selectedDoc && (
        <Dialog open={!!selectedDoc} onOpenChange={() => setSelectedDoc(null)}>
          <DialogContent className="max-w-4xl h-[80vh]">
            <DialogHeader>
              <DialogTitle>{selectedDoc?.filename}</DialogTitle>
              <DialogDescription>
                {selectedDoc?.mimeType} - {(selectedDoc?.fileSize / 1024).toFixed(2)} KB
              </DialogDescription>
            </DialogHeader>
            <div className="flex-1 overflow-auto">
              <DocumentViewer
                documentId={selectedDoc?.id}
                documentType={selectedDoc?.documentType}
                mimeType={selectedDoc?.mimeType}
                fileName={selectedDoc?.filename}
              />
            </div>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
};