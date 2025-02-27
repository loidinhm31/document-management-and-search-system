import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import DocumentReportsTab from "@/components/admin/reports/document-reports-tab";
import CommentReportsTab from "@/components/admin/reports/comment-reports-tab";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { AlertCircle } from "lucide-react";

export default function ReportsManagementPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState("documents");

  return (
    <div className="container mx-auto py-6">
      <Card>
        <CardHeader>
          <div className="flex items-start gap-2">
            <AlertCircle className="h-5 w-5 text-destructive mt-1" />
            <div>
              <CardTitle>{t("admin.reports.title")}</CardTitle>
              <CardDescription>{t("admin.reports.description")}</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <Tabs
            defaultValue="documents"
            value={activeTab}
            onValueChange={setActiveTab}
            className="w-full">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="documents">{t("admin.reports.tabs.documents")}</TabsTrigger>
              <TabsTrigger value="comments">{t("admin.reports.tabs.comments")}</TabsTrigger>
            </TabsList>
            <TabsContent value="documents">
              <DocumentReportsTab />
            </TabsContent>
            <TabsContent value="comments">
              <CommentReportsTab />
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    </div>
  );
}