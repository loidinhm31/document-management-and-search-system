import { Eye, FileDown } from "lucide-react";
import { useTranslation } from "react-i18next";


interface DocumentStatsProps {
  viewCount: number;
  downloadCount: number;
}

const DocumentStats = ({ viewCount, downloadCount }: DocumentStatsProps) => {
  const { t } = useTranslation();

  return (
    <div className="grid grid-cols-2 gap-2">
      <div className="flex items-center space-x-1 rounded-lg border p-1">
        <div className="rounded-full bg-primary/10 p-1">
          <Eye className="h-4 w-4 text-primary" />
        </div>
        <div>
          <p className="text-xs font-medium leading-none">{t("document.discover.stats.views")}</p>
          <p className="text-sm font-bold">{viewCount}</p>
        </div>
      </div>
      <div className="flex items-center space-x-1 rounded-lg border p-1">
        <div className="rounded-full bg-primary/10 p-1">
          <FileDown className="h-4 w-4 text-primary" />
        </div>
        <div>
          <p className="text-xs font-medium leading-none">{t("document.discover.stats.downloads")}</p>
          <p className="text-sm font-bold">{downloadCount}</p>
        </div>
      </div>
    </div>
  );
};

export default DocumentStats;
