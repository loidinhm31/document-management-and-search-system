import {
  ArrowLeft,
  ArrowRight,
  Calendar,
  Download,
  Eye,
  Filter,
  Loader2,
  MoreHorizontal,
  SortAsc,
  SortDesc
} from "lucide-react";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { HighlightCell } from "@/components/document/discover/highlight-cell";
import SearchSuggestions from "@/components/document/discover/search-suggestions";
import DocumentFilter from "@/components/document/my-document/document-filter";
import { DocumentViewer } from "@/components/document/viewers/document-viewer";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import {
  fetchDocuments,
  resetFilters,
  selectSearchLoading,
  selectSearchResults,
  selectSearchState,
  setCategory,
  setLevel,
  setMajor,
  setPage,
  setSort,
  setTags
} from "@/store/slices/searchSlice";

const sortOptions = [
  { label: "Created Date (Newest)", value: "createdDate,desc" },
  { label: "Created Date (Oldest)", value: "createdDate,asc" },
  { label: "Name (A-Z)", value: "filename,asc" },
  { label: "Name (Z-A)", value: "filename,desc" }
];

const PAGE_SIZE = 10; // Number of items per page

export const DocumentList = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { toast } = useToast();

  const loading = useAppSelector(selectSearchLoading);
  const documents = useAppSelector(selectSearchResults);
  const {
    selectedSort,
    selectedMajor,
    selectedLevel,
    selectedCategory,
    selectedTags,
    currentPage,
    totalPages
  } = useAppSelector(selectSearchState);
  const [selectedDoc, setSelectedDoc] = React.useState(null);
  const [showAdvanced, setShowAdvanced] = React.useState(false);

  // Initial fetch
  useEffect(() => {
    dispatch(fetchDocuments());
  }, [dispatch]);

  const handleSearch = () => {
    dispatch(setPage(0)); // Reset to first page when searching
    dispatch(fetchDocuments());
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
        title: t("common.error"),
        description: t("document.viewer.error.download"),
        variant: "destructive"
      });
    }
  };

  const handleReset = () => {
    dispatch(resetFilters());
    dispatch(fetchDocuments());
  };

  const handlePageChange = (newPage: number) => {
    dispatch(setPage(newPage));
    dispatch(fetchDocuments());
  };

  const formatDate = (dateString: string | Date) => {
    const date = typeof dateString === "string" ? new Date(dateString) : dateString;
    return date.toLocaleString();
  };

  const getActiveFilterCount = () => {
    let count = 0;
    if (selectedMajor !== "all") count++;
    if (selectedLevel !== "all") count++;
    if (selectedCategory !== "all") count++;
    if (selectedTags.length > 0) count++;
    return count;
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
          <div className="flex flex-col gap-4 lg:flex-row">
            {/* Search Input and Button */}
            <div className="flex flex-1 flex-col gap-2 sm:flex-row">
              <div className="flex-1">
                <SearchSuggestions
                  onSearch={handleSearch}
                  className="max-w-none"
                  placeholder={t("document.search.placeholder")}
                />
              </div>
              <Button onClick={handleSearch} className="shrink-0">
                {t("document.commonSearch.apply")}
              </Button>
            </div>

            {/* Sort, Filter, and Reset Buttons */}
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <Select value={selectedSort} onValueChange={(value) => dispatch(setSort(value))}>
                <SelectTrigger className="w-full sm:w-[200px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {sortOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      <span className="flex items-center gap-2">
                        {option.value.endsWith("desc") ? (
                          <SortDesc className="h-4 w-4" />
                        ) : (
                          <SortAsc className="h-4 w-4" />
                        )}
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
                <span className="hidden sm:inline">{t("document.commonSearch.filters")}</span>
                {getActiveFilterCount() > 0 && (
                  <span
                    className="absolute -right-2 -top-2 flex h-5 w-5 items-center justify-center rounded-full bg-primary text-xs text-primary-foreground">
                    {getActiveFilterCount()}
                  </span>
                )}
              </Button>

              <Button variant="outline" onClick={handleReset} className="sm:w-auto">
                {t("document.commonSearch.reset")}
              </Button>
            </div>
          </div>

          {/* Advanced Filters */}
          {showAdvanced && (
            <div className="space-y-4">
              <DocumentFilter
                majorValue={selectedMajor}
                onMajorChange={(value) => dispatch(setMajor(value))}
                levelValue={selectedLevel}
                onLevelChange={(value) => dispatch(setLevel(value))}
                categoryValue={selectedCategory}
                onCategoryChange={(value) => dispatch(setCategory(value))}
                tagsValue={selectedTags}
                onTagsChange={(tags) => dispatch(setTags(tags))}
                className="grid-cols-1 sm:grid-cols-2 lg:grid-cols-4"
              />
            </div>
          )}

          {/* Results Section */}
          {loading ? (
            <div className="flex justify-center p-4">
              <Loader2 className="h-8 w-8 animate-spin" />
              <span className="ml-2">{t("document.search.loading")}</span>
            </div>
          ) : (
            <>
              <div className="space-y-4 lg:hidden">
                {documents.map((doc) => (
                  <Card key={doc.id} className="overflow-hidden">
                    <CardHeader className="p-4">
                      <CardTitle className="text-base">
                        <Button
                          variant="link"
                          className="h-auto p-0 text-left"
                          onClick={() => navigate(`/document/${doc.id}`)}
                        >
                          {doc.filename}
                        </Button>
                      </CardTitle>
                      <CardDescription>
                        <div className="flex items-center gap-2">
                          <Calendar className="h-4 w-4" />
                          {formatDate(doc.createdAt)}
                        </div>
                      </CardDescription>
                    </CardHeader>
                    <CardContent className="p-4 pt-0">
                      <div className="space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium">{t("document.discover.headers.course")}:</span>
                          <span className="text-sm">{doc.courseCode}</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium">{t("document.discover.headers.major")}:</span>
                          <span className="text-sm">{doc.major}</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium">{t("document.discover.headers.level")}:</span>
                          <span className="text-sm">{doc.courseLevel}</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium">{t("document.discover.headers.category")}:</span>
                          <span className="text-sm">{doc.category}</span>
                        </div>
                        {doc.highlights && doc.highlights.length > 0 && (
                          <div className="mt-2">
                            <HighlightCell highlights={doc.highlights} />
                          </div>
                        )}
                        <div className="flex justify-end gap-2 pt-2">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setSelectedDoc(doc)}
                          >
                            <Eye className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleDownload(doc.id, doc.filename)}
                          >
                            <Download className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>

              {/* Desktop Table View */}
              <div className="hidden lg:block">
                <div className="rounded-md border">
                  {/* Results Table */}
                  {loading ? (
                    <div className="flex justify-center p-4">
                      <Loader2 className="h-6 w-6 animate-spin" />
                      <span className="ml-2">{t("document.search.loading")}</span>
                    </div>
                  ) : (
                    <>
                      <div className="relative overflow-hidden rounded-md border">
                        <Table>
                          <TableHeader>
                            <TableRow>
                              <TableHead>{t("document.discover.headers.name")}</TableHead>
                              <TableHead>{t("document.discover.headers.course")}</TableHead>
                              <TableHead className="w-[10%]">{t("document.discover.headers.major")}</TableHead>
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
                        </div>)
                      }
                    </>
                  )}
                </div>
              </div>

              {/* Simplified Pagination */}
              {totalPages > 1 && (
                <div className="mt-4 flex justify-center gap-2">
                  <Button
                    variant="outline"
                    onClick={() => handlePageChange(currentPage - 1)}
                    disabled={currentPage === 0 || loading}
                    className="gap-2"
                  >
                    <ArrowLeft className="h-4 w-4" />
                    <span className="hidden sm:inline">
                      {t("document.discover.pagination.previous")}
                    </span>
                  </Button>

                  <span className="hidden items-center px-4 sm:flex">
                    {t("document.discover.pagination.pageInfo", {
                      current: currentPage + 1,
                      total: totalPages
                    })}
                  </span>

                  <Button
                    variant="outline"
                    onClick={() => handlePageChange(currentPage + 1)}
                    disabled={currentPage === totalPages - 1 || loading}
                    className="gap-2"
                  >
                    <span className="hidden sm:inline">
                      {t("document.discover.pagination.next")}
                    </span>
                    <ArrowRight className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </>
          )}

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
        </div>
      </CardContent>
    </Card>
  );
};

export default DocumentList;