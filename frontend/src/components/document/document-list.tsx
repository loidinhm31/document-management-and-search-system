import { Calendar, Download, Eye, Loader2, MoreHorizontal, Trash2 } from "lucide-react";
import React, { useCallback, useEffect, useState } from "react";

import { DocumentViewer } from "@/components/document/document-viewer";
import SearchSuggestions from "@/components/document/search-suggestions";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { searchService } from "@/services/search.service";
import { HighlightCell } from "@/components/document/highlight-cell";
import { DropdownMenu, DropdownMenuTrigger } from "@radix-ui/react-dropdown-menu";
import { DropdownMenuContent, DropdownMenuItem } from "@/components/ui/dropdown-menu";

export const DocumentList = () => {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedDoc, setSelectedDoc] = useState(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [currentSearchQuery, setCurrentSearchQuery] = useState("");
  const pageSize = 10;

  const { toast } = useToast();

  const fetchDocuments = useCallback(async (query, page) => {
    setLoading(true);
    try {
      const response = await searchService.searchDocuments(query, page, pageSize);

      // Transform the response to include highlights
      const documentsWithHighlights = response.data.content.map(doc => ({
        ...doc,
        highlights: doc.highlights || []
      }));

      setDocuments(documentsWithHighlights);
      setTotalPages(response.data.totalPages);
      setCurrentSearchQuery(query);
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to fetch documents",
        variant: "destructive"
      });
    } finally {
      setLoading(false);
    }
  }, [toast]);

  const handleSearch = useCallback((query) => {
    setCurrentPage(0);
    fetchDocuments(query, 0);
  }, [fetchDocuments]);

  const handlePageChange = useCallback((newPage) => {
    setCurrentPage(newPage);
    fetchDocuments(currentSearchQuery, newPage);
  }, [currentSearchQuery, fetchDocuments]);

  const handleDelete = async (id) => {
    try {
      await documentService.deleteDocument(id);
      toast({
        title: "Success",
        description: "Document deleted successfully",
        variant: "success",
      });

      const isLastItemOnPage = documents.length === 1 && currentPage > 0;
      const newPage = isLastItemOnPage ? currentPage - 1 : currentPage;
      fetchDocuments(currentSearchQuery, newPage);

      if (isLastItemOnPage) {
        setCurrentPage(newPage);
      }
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to delete document",
        variant: "destructive",
      });
    }
  };

  const handleDownload = async (id, filename) => {
    try {
      const response = await documentService.downloadDocument(id);
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to download document",
        variant: "destructive"
      });
    }
  };

  useEffect(() => {
    fetchDocuments("", 0);
  }, [fetchDocuments]);

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleString();
  };


  return (
    <Card>
      <CardHeader>
        <CardTitle>Documents</CardTitle>
      </CardHeader>
      <CardContent>
        <SearchSuggestions onSearch={handleSearch} className="mb-4 max-w-none" />

        {loading ? (
          <div className="flex justify-center p-4">
            <Loader2 className="h-6 w-6 animate-spin" />
          </div>
        ) : (
          <>
            <div className="relative overflow-hidden rounded-md border">
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-[7%]">Name</TableHead>
                      <TableHead className="w-[10%]">Course</TableHead>
                      <TableHead className="w-[10%]">Major</TableHead>
                      <TableHead className="hidden md:table-cell w-[10%]">Level</TableHead>
                      <TableHead className="hidden lg:table-cell w-[10%]">Category</TableHead>
                      <TableHead className="hidden xl:table-cell w-[10%]">Tags</TableHead>
                      <TableHead className="w-[30%]">Matches</TableHead>
                      <TableHead className="w-[8%]">Created</TableHead>
                      <TableHead className="w-[5%] text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {documents.map((doc) => (
                      <TableRow key={doc.id}>
                        <TableCell className="font-medium truncate">
                          {doc.filename}
                        </TableCell>
                        <TableCell className="truncate">{doc.courseCode}</TableCell>
                        <TableCell className="truncate">{doc.major}</TableCell>
                        <TableCell className="hidden md:table-cell">{doc.courseLevel}</TableCell>
                        <TableCell className="hidden lg:table-cell">{doc.category}</TableCell>
                        <TableCell className="hidden xl:table-cell">
                          <div className="flex flex-wrap gap-1">
                            {doc.tags?.map((tag, index) => (
                              <span
                                key={index}
                                className="inline-flex items-center rounded-md bg-blue-50 px-2 py-1 text-xs font-medium text-blue-700 ring-1 ring-inset ring-blue-600/20"
                              >
                                {tag}
                              </span>
                            ))}
                          </div>
                        </TableCell>
                        <TableCell>
                          <HighlightCell highlights={doc.highlights} />
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            <Calendar className="h-4 w-4 text-muted-foreground" />
                            {formatDate(doc.createdAt)}
                          </div>
                        </TableCell>
                        <TableCell className="text-right">
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button variant="ghost" size="icon">
                                <MoreHorizontal className="h-4 w-4" />
                                <span className="sr-only">Actions</span>
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              <DropdownMenuItem onClick={() => setSelectedDoc(doc)}>
                                <Eye className="mr-2 h-4 w-4" />
                                View
                              </DropdownMenuItem>
                              <DropdownMenuItem onClick={() => handleDownload(doc.id, doc.filename)}>
                                <Download className="mr-2 h-4 w-4" />
                                Download
                              </DropdownMenuItem>
                              <DropdownMenuItem onClick={() => handleDelete(doc.id)}>
                                <Trash2 className="mr-2 h-4 w-4" />
                                Delete
                              </DropdownMenuItem>
                            </DropdownMenuContent>
                          </DropdownMenu>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </div>

            {totalPages > 1 && (
              <div className="mt-4 flex justify-center gap-2">
                <Button
                  variant="outline"
                  onClick={() => handlePageChange(currentPage - 1)}
                  disabled={currentPage === 0 || loading}
                >
                  Previous
                </Button>
                <span className="flex items-center px-4">
                  Page {currentPage + 1} of {totalPages}
                </span>
                <Button
                  variant="outline"
                  onClick={() => handlePageChange(currentPage + 1)}
                  disabled={currentPage === totalPages - 1 || loading}
                >
                  Next
                </Button>
              </div>
            )}
          </>
        )}
      </CardContent>

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
    </Card>
  );
};