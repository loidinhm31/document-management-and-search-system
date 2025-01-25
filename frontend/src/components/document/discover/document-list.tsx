import { DropdownMenu, DropdownMenuTrigger } from "@radix-ui/react-dropdown-menu";
import { Calendar, Download, Eye, Filter, Loader2, MoreHorizontal, SortAsc, SortDesc } from "lucide-react";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { HighlightCell } from "@/components/document/discover/highlight-cell";
import SearchSuggestions from "@/components/document/discover/search-suggestions";
import DocumentFilter from "@/components/document/my-document/document-filter";
import { DocumentViewer } from "@/components/document/viewers/document-viewer";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { DropdownMenuContent, DropdownMenuItem } from "@/components/ui/dropdown-menu";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { searchService } from "@/services/search.service";

const sortOptions = [
  { label: "Created Date (Newest)", value: "createdDate,desc" },
  { label: "Created Date (Oldest)", value: "createdDate,asc" },
  { label: "Name (A-Z)", value: "filename,asc" },
  { label: "Name (Z-A)", value: "filename,desc" }
];

export const DocumentList = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { toast } = useToast();

  // States
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedDoc, setSelectedDoc] = useState(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const pageSize = 10;

  // Filter states
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedSort, setSelectedSort] = useState(sortOptions[0].value);
  const [selectedMajor, setSelectedMajor] = useState("all");
  const [selectedLevel, setSelectedLevel] = useState("all");
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);

  const fetchDocuments = useCallback(async (query: string, page: number) => {
    setLoading(true);
    try {
      const filters = {
        search: query,
        major: selectedMajor === "all" ? undefined : selectedMajor,
        level: selectedLevel === "all" ? undefined : selectedLevel,
        category: selectedCategory === "all" ? undefined : selectedCategory,
        tags: selectedTags.length > 0 ? selectedTags : undefined,
        sort: selectedSort
      };

      const response = await searchService.searchDocuments(filters, page, pageSize);

      const documentsWithHighlights = response.data.content.map(doc => ({
        ...doc,
        highlights: doc.highlights || []
      }));

      setDocuments(documentsWithHighlights);
      setTotalPages(response.data.totalPages);
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to fetch documents",
        variant: "destructive"
      });
    } finally {
      setLoading(false);
    }
  }, [selectedMajor, selectedLevel, selectedCategory, selectedTags, selectedSort, toast]);

  const handleSearch = useCallback(() => {
    setCurrentPage(0);
    fetchDocuments(searchTerm, 0);
  }, [searchTerm, fetchDocuments]);

  const handlePageChange = useCallback((newPage: number) => {
    setCurrentPage(newPage);
    fetchDocuments(searchTerm, newPage);
  }, [searchTerm, fetchDocuments]);

  const handleReset = () => {
    setSelectedMajor("all");
    setSelectedLevel("all");
    setSelectedCategory("all");
    setSelectedTags([]);
    fetchDocuments(searchTerm, 0);
  };

  const handleDownload = async (id: string, filename: string) => {
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

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString();
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("document.discover.title")}</CardTitle>
        <CardDescription>{t("document.discover.description")}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {/* Search Section */}
          <div className="flex flex-col gap-4 sm:flex-row">
            {/* Search Input and Button */}
            <div className="flex flex-1 gap-2">
              <div className="flex-1">
                <SearchSuggestions
                  onSearch={handleSearch}
                  onInputChange={setSearchTerm}
                  className="max-w-none"
                  placeholder={t("document.search.placeholder")}
                  filters={{
                    major: selectedMajor === "all" ? undefined : selectedMajor,
                    level: selectedLevel === "all" ? undefined : selectedLevel,
                    category: selectedCategory === "all" ? undefined : selectedCategory,
                    tags: selectedTags.length > 0 ? selectedTags : undefined
                  }}
                />
              </div>
              <Button onClick={handleSearch} className="shrink-0">
                {t("document.commonSearch.apply")}
              </Button>
            </div>

            {/* Sort, Filter, and Reset Buttons */}
            <div className="flex items-center gap-2">
              <Select value={selectedSort} onValueChange={setSelectedSort}>
                <SelectTrigger className="w-[200px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {sortOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      <span className="flex items-center gap-2">
                        {option.value.endsWith('desc') ?
                          <SortDesc className="h-4 w-4" /> :
                          <SortAsc className="h-4 w-4" />
                        }
                        {option.label}
                      </span>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              <Button
                variant="outline"
                onClick={() => setShowAdvanced(!showAdvanced)}
                className="relative gap-2"
              >
                <Filter className="h-4 w-4" />
                {t("document.commonSearch.filters")}
              </Button>

              <Button variant="outline" onClick={handleReset}>
                {t("document.commonSearch.reset")}
              </Button>
            </div>
          </div>

          {/* Advanced Filters */}
          {showAdvanced && (
            <div className="space-y-4">
              <DocumentFilter
                majorValue={selectedMajor}
                onMajorChange={setSelectedMajor}
                levelValue={selectedLevel}
                onLevelChange={setSelectedLevel}
                categoryValue={selectedCategory}
                onCategoryChange={setSelectedCategory}
                tagsValue={selectedTags}
                onTagsChange={setSelectedTags}
                className="md:grid-cols-3 lg:grid-cols-4"
              />
            </div>
          )}

          {/* Results Table */}
          {loading ? (
            <div className="flex justify-center p-4">
              <Loader2 className="h-6 w-6 animate-spin" />
              <span className="ml-2">{t("document.search.loading")}</span>
            </div>
          ) : (
            <div className="relative overflow-hidden rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("document.discover.headers.name")}</TableHead>
                    <TableHead>{t("document.discover.headers.course")}</TableHead>
                    <TableHead className="w-[10%]">Major</TableHead>
                    <TableHead
                      className="hidden md:table-cell w-[10%]">{t("document.discover.headers.level")}</TableHead>
                    <TableHead
                      className="hidden lg:table-cell w-[10%]">{t("document.discover.headers.category")}</TableHead>
                    <TableHead
                      className="hidden xl:table-cell w-[10%]">{t("document.discover.headers.tags")}</TableHead>
                    <TableHead className="w-[30%]">{t("document.discover.headers.matches")}</TableHead>
                    <TableHead className="w-[8%]">{t("document.discover.headers.created")}</TableHead>
                    <TableHead className="w-[5%] text-right">{t("document.discover.headers.actions")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {documents.map((doc) => (
                    <TableRow key={doc.id}>
                      <TableCell className="font-medium truncate">
                        <Button
                          variant="link"
                          className="font-medium truncate p-0 h-auto"
                          onClick={() => navigate(`/document/${doc.id}`)}
                        >
                          {doc.filename}
                        </Button>
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
                              {t("document.actions.view")}
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleDownload(doc.id, doc.filename)}>
                              <Download className="mr-2 h-4 w-4" />
                              {t("document.actions.download")}
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="mt-4 flex justify-center gap-2">
              <Button
                variant="outline"
                onClick={() => handlePageChange(currentPage - 1)}
                disabled={currentPage === 0 || loading}
              >
                {t("document.discover.pagination.previous")}
              </Button>
              <span className="flex items-center px-4">
                {t("document.discover.pagination.pageInfo", {
                  current: currentPage + 1,
                  total: totalPages
                })}
              </span>
              <Button
                variant="outline"
                onClick={() => handlePageChange(currentPage + 1)}
                disabled={currentPage === totalPages - 1 || loading}
              >
                {t("document.discover.pagination.next")}
              </Button>
            </div>
          )}
        </div>

        {/* Document Preview Dialog */}
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
      </CardContent>
    </Card>
  );
};