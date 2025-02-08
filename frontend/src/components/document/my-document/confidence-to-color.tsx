import i18n from "i18next";
import { Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { predictionService } from "@/services/prediction.service";
import { CategoryPrediction } from "@/types/document";
import { MasterData } from "@/types/master-data";

const confidenceToColor = (confidence: number) => {
  if (confidence >= 0.9) return "bg-green-50 text-green-700 ring-1 ring-green-600/20";
  if (confidence >= 0.7) return "bg-yellow-50 text-yellow-700 ring-1 ring-yellow-600/20";
  if (confidence >= 0.5) return "bg-orange-50 text-orange-700 ring-1 ring-orange-600/20";
  return "";
};

const normalizeCategory = (category: string): string => {
  return category
    .toLowerCase()
    .replace(/\s+/g, "_")
    .replace(/[^a-z0-9_]/g, "");
};

interface CategoryPredictionsProps {
  text: string;
  filename: string;
  language: string;
  value: string;
  categories: MasterData[];
  onValueChange: (value: string) => void;
  shouldFetchPredictions?: boolean;
  setShouldFetchPredictions?: (shouldFetchPredictions: boolean) => void;
  disabled?: boolean;
}

export function CategoryPredictions({
                                      text,
                                      filename,
                                      language,
                                      value,
                                      categories,
                                      onValueChange,
                                      shouldFetchPredictions = false,
                                      setShouldFetchPredictions,
                                      disabled = false
                                    }: CategoryPredictionsProps) {
  const { t } = useTranslation();
  const [predictions, setPredictions] = useState<CategoryPrediction[]>([]);
  const [loading, setLoading] = useState(false);

  const getPredictions = async () => {
    if (!text || text.length < 50 || text.length > 500) return;

    setLoading(true);
    try {
      const response = await predictionService.getDocumentPrediction(
        text,
        filename,
        language
      );
      setPredictions(response.data.predictions);

      // Auto-select the highest confidence prediction if no value is selected
      if (!value && response.data.predictions.length > 0) {
        const topPrediction = response.data.predictions[0];
        const categoryCode = mapPredictionToCategory(topPrediction.category);
        if (categoryCode) {
          onValueChange(categoryCode);
        }
      }
    } catch (error) {
      console.error("Error getting predictions:", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (shouldFetchPredictions) {
      getPredictions().then(() => setShouldFetchPredictions(false));
    }
  }, [shouldFetchPredictions]);

  const mapPredictionToCategory = (predictionCategory: string) => {
    const normalizedPrediction = normalizeCategory(predictionCategory);

    const matchingCategory = categories.find(category => {
      const normalizedCategory = normalizeCategory(
        category.translations.en || category.code
      );
      return normalizedCategory === normalizedPrediction;
    });

    return matchingCategory?.code;
  };

  const getCategoryPrediction = (categoryCode: string) => {
    const category = categories.find(c => c.code === categoryCode);
    if (!category) return null;

    return predictions.find(p =>
      normalizeCategory(p.category) === normalizeCategory(category.translations.en || category.code)
    );
  };

  return (
    <Select value={value} onValueChange={onValueChange} disabled={disabled}>
      <SelectTrigger className="w-full">
        {loading ? (
          <div className="flex items-center gap-2">
            <Loader2 className="h-4 w-4 animate-spin" />
            {t("document.upload.form.category.analyzing")}
          </div>
        ) : (
          <SelectValue placeholder={t("document.upload.form.category.placeholder")} />
        )}
      </SelectTrigger>
      <SelectContent>
        {categories.map((category) => {
          const prediction = getCategoryPrediction(category.code);
          const colorClass = prediction ? confidenceToColor(prediction.confidence) : "";

          return (
            <SelectItem
              key={category.code}
              value={category.code}
              className={cn(
                "flex justify-between items-center rounded-md transition-colors",
                colorClass
              )}
            >
              <div className="flex justify-between items-center w-full">
                <span>{category.translations[i18n.language] || category.translations.en}</span>
                {prediction && (
                  <span
                    className={cn(
                      "ml-2 inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
                      colorClass
                    )}
                  >
                    {Math.round(prediction.confidence * 100)}%
                  </span>
                )}
              </div>
            </SelectItem>
          );
        })}
      </SelectContent>
    </Select>
  );
}