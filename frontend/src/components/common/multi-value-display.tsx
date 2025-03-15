import React from "react";

import { cn, getMasterDataTranslation, MasterDataMap } from "@/lib/utils";
import { MasterDataType } from "@/types/master-data";

interface MultiValueDisplayProps {
  value: string[];
  type?: MasterDataType;
  masterData?: MasterDataMap;
  className?: string;
  pillClassName?: string;
}

export const MultiValueDisplay = ({
  value,
  type,
  masterData,
  className = "",
  pillClassName = "inline-flex items-center rounded-md bg-secondary/10 px-2 py-1 text-xs font-medium text-secondary",
}: MultiValueDisplayProps) => {
  // Convert single value to array for consistent processing
  const values = Array.isArray(value) ? value : [value];

  // Display values as pills/tags
  return (
    <div className={`flex flex-wrap gap-1 ${className}`}>
      {masterData ? (
        <>
          {values.map((val, index) => (
            <span key={index} className={cn(pillClassName, "font-semibold")}>
              {type ? getMasterDataTranslation(val, type, masterData) : val}
            </span>
          ))}
        </>
      ) : (
        <>
          {values.map((val, index) => (
            <span key={index} className={cn(pillClassName, "font-semibold")}>
              {val}
            </span>
          ))}
        </>
      )}
    </div>
  );
};

export default MultiValueDisplay;
