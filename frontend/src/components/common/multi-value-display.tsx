import React from "react";

import { cn, getMasterDataTranslation } from "@/lib/utils";

export const MultiValueDisplay = ({
  value,
  type,
  masterData,
  className = "",
  pillClassName = "inline-flex items-center rounded-md bg-secondary/10 px-2 py-1 text-xs font-medium text-secondary",
}) => {
  // Convert single value to array for consistent processing
  const values = Array.isArray(value) ? value : [value];

  // Display values as pills/tags
  return (
    <div className={`flex flex-wrap gap-1 ${className}`}>
      {values.map((val) => (
        <span key={val} className={cn(pillClassName, 'font-semibold')}>
          {type ? getMasterDataTranslation(val, type, masterData) : val}
        </span>
      ))}
    </div>
  );
};

export default MultiValueDisplay;
