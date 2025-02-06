import { Loader2, Save } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import TagInputHybrid from "@/components/common/tag-input-hybrid";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import { fetchMasterData, selectMasterData } from "@/store/slices/masterDataSlice";
import { DocumentPreferences, InteractionStats, PreferenceCategory } from "@/types/document-preference";

export default function DocumentPreferencesManager() {
  const { t } = useTranslation();
  const { toast } = useToast();
  const dispatch = useAppDispatch();
  const { majors, levels, categories, loading: masterDataLoading } = useAppSelector(selectMasterData);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [preferences, setPreferences] = useState<DocumentPreferences | null>(null);
  const [weights, setWeights] = useState<PreferenceCategory[]>([]);
  const [stats, setStats] = useState<InteractionStats | null>(null);
  const [recommendedTags, setRecommendedTags] = useState<string[]>([]);

  useEffect(() => {
    if (majors.length === 0 || levels.length === 0 || categories.length === 0) {
      dispatch(fetchMasterData());
    }
  }, [dispatch, majors.length, levels.length, categories.length]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const [prefsRes, weightsRes, statsRes, tagsRes] = await Promise.all([
          documentService.getDocumentPreferences(),
          documentService.getDocumentContentWeights(),
          documentService.getInteractionStatistics(),
          documentService.getRecommendedTags()
        ]);

        setPreferences(prefsRes.data);
        setWeights(Object.entries(weightsRes.data).map(([type, weight]) => ({
          type,
          weight: weight as number
        })));
        setStats(statsRes.data);
        setRecommendedTags(tagsRes.data);
      } catch (error) {
        toast({
          title: t("common.error"),
          description: t("preferences.fetchError"),
          variant: "destructive"
        });
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  const handleSave = async () => {
    if (!preferences) return;

    try {
      setSaving(true);
      await documentService.updateDocumentPreferences({
        preferredMajors: Array.from(preferences.preferredMajors || []),
        preferredLevels: Array.from(preferences.preferredLevels || []),
        preferredCategories: Array.from(preferences.preferredCategories || []),
        preferredTags: Array.from(preferences.preferredTags || []),
        languagePreferences: Array.from(preferences.languagePreferences || [])
      });
      toast({
        title: t("common.success"),
        description: t("preferences.updateSuccess"),
        variant: "success"
      });
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("preferences.updateError"),
        variant: "destructive"
      });
    } finally {
      setSaving(false);
    }
  };

  if (loading || masterDataLoading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Tabs defaultValue="preferences" className="w-full">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="preferences">{t("preferences.tabs.preferences")}</TabsTrigger>
          <TabsTrigger value="analytics">{t("preferences.tabs.analytics")}</TabsTrigger>
        </TabsList>

        <TabsContent value="preferences">
          <Card>
            <CardHeader>
              <CardTitle>{t("preferences.contentPreferences.title")}</CardTitle>
              <CardDescription>
                {t("preferences.contentPreferences.description")}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              {/* Language Preferences */}
              <div className="space-y-2">
                <Label>{t("preferences.contentPreferences.language.label")}</Label>
                <Select
                  value={Array.from(preferences?.languagePreferences || [])[0]}
                  onValueChange={(lang) => setPreferences(prev => ({
                    ...prev,
                    languagePreferences: new Set([lang])
                  }))}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("preferences.contentPreferences.language.placeholder")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="en">English</SelectItem>
                    <SelectItem value="vi">Tiếng Việt</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {/* Preferred Majors */}
              <div className="space-y-2">
                <Label>{t("preferences.contentPreferences.majors.label")}</Label>
                <TagInputHybrid
                  value={Array.from(preferences?.preferredMajors || [])}
                  onChange={(majors) => setPreferences(prev => ({
                    ...prev,
                    preferredMajors: new Set(majors)
                  }))}
                  recommendedTags={majors.map(major => major.code)}
                  placeholder={t("document.commonSearch.majorPlaceholder")}
                />
              </div>

              {/* Preferred Levels */}
              <div className="space-y-2">
                <Label>{t("preferences.contentPreferences.levels.label")}</Label>
                <TagInputHybrid
                  value={Array.from(preferences?.preferredLevels || [])}
                  onChange={(levels) => setPreferences(prev => ({
                    ...prev,
                    preferredLevels: new Set(levels)
                  }))}
                  recommendedTags={levels.map(level => level.code)}
                  placeholder={t("document.commonSearch.levelPlaceholder")}
                />
              </div>

              {/* Preferred Categories */}
              <div className="space-y-2">
                <Label>{t("preferences.contentPreferences.categories.label")}</Label>
                <TagInputHybrid
                  value={Array.from(preferences?.preferredCategories || [])}
                  onChange={(categories) => setPreferences(prev => ({
                    ...prev,
                    preferredCategories: new Set(categories)
                  }))}
                  recommendedTags={categories.map(category => category.code)}
                  placeholder={t("document.commonSearch.categoryPlaceholder")}
                />
              </div>

              {/* Preferred Tags */}
              <div className="space-y-2">
                <Label>{t("preferences.contentPreferences.tags.label")}</Label>
                <TagInputHybrid
                  value={Array.from(preferences?.preferredTags || [])}
                  onChange={(tags) => setPreferences(prev => ({
                    ...prev,
                    preferredTags: new Set(tags)
                  }))}
                  recommendedTags={recommendedTags}
                  onSearch={async (query) => {
                    try {
                      const response = await documentService.getTagSuggestions(query);
                      return response.data;
                    } catch (error) {
                      console.error("Error fetching tag suggestions:", error);
                      return [];
                    }
                  }}
                  placeholder={t("document.commonSearch.tagsPlaceholder")}
                />
              </div>

              <Button
                onClick={handleSave}
                disabled={saving}
                className="w-full"
              >
                {saving && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                <Save className="mr-2 h-4 w-4" />
                {t("preferences.actions.save")}
              </Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="analytics">
          <Card>
            <CardHeader>
              <CardTitle>{t("preferences.analytics.title")}</CardTitle>
              <CardDescription>
                {t("preferences.analytics.description")}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              {/* Content Type Weights */}
              <div className="space-y-4">
                <Label>{t("preferences.analytics.weights.label")}</Label>
                <ScrollArea className="h-72 rounded-md border p-4">
                  <div className="space-y-2">
                    {weights.map(({ type, weight }) => (
                      <div key={type} className="flex items-center justify-between space-x-2">
                        <div className="flex-1">
                          <p className="font-medium">{type}</p>
                          <p className="text-sm text-muted-foreground">
                            {t(`preferences.contentType.${type.toLowerCase()}`)}
                          </p>
                        </div>
                        <div className="w-24 text-right">
                          <span className="text-sm font-medium">
                            {(weight * 100).toFixed(1)}%
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </ScrollArea>
              </div>

              {/* Interaction Statistics */}
              {stats && (
                <div className="space-y-4">
                  <Label>{t("preferences.analytics.stats.label")}</Label>
                  <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                    <div className="rounded-lg border p-3">
                      <div className="text-sm font-medium text-muted-foreground">
                        {t("preferences.analytics.stats.comments")}
                      </div>
                      <div className="text-2xl font-bold">
                        {stats.interactionCounts.COMMENT || 0}
                      </div>
                    </div>
                    <div className="rounded-lg border p-3">
                      <div className="text-sm font-medium text-muted-foreground">
                        {t("preferences.analytics.stats.uniqueDocuments")}
                      </div>
                      <div className="text-2xl font-bold">
                        {stats.uniqueDocumentsAccessed}
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}