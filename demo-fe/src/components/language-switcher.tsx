import { Globe } from "lucide-react";
import React from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

const LanguageSwitcher = () => {
  const { i18n } = useTranslation();

  const languages = [
    { code: "en", label: "English" },
    { code: "vi", label: "Tiếng Việt" },
  ];

  // Get current language label
  const getCurrentLanguageLabel = () => {
    return languages.find(lang => lang.code === i18n.language)?.label || "English";
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="flex items-center gap-2">
          <Globe className="h-4 w-4" />
          <span>{getCurrentLanguageLabel()}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {languages.map((lang) => (
          <DropdownMenuItem key={lang.code} onClick={() => i18n.changeLanguage(lang.code)}>
            {lang.label}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export default LanguageSwitcher;