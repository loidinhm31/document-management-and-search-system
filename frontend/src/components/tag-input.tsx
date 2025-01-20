import React, { KeyboardEvent, useRef, useState } from "react";
import { Input } from "@/components/ui/input";
import { X } from "lucide-react";

interface TagInputProps {
  value: string[];
  onChange: (tags: string[]) => void;
  placeholder?: string;
  disabled?: boolean;
  error?: boolean;
  className?: string;
}

export const TagInput = ({
                           value,
                           onChange,
                           placeholder = "Enter tags...",
                           disabled = false,
                           error = false,
                           className = ""
                         }: TagInputProps) => {
  const [inputValue, setInputValue] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const sanitizeTag = (tag: string): string => {
    return tag
      .trim()
      .replace(/[[\]"]/g, "")
      .replace(/\s+/g, " ")
      .trim();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Backspace" && inputValue === "" && value.length > 0) {
      onChange(value.slice(0, -1));
    }

    if (event.key === "," || event.key === "Enter") {
      event.preventDefault();
      const cleanedTag = sanitizeTag(inputValue);

      if (cleanedTag && !value.includes(cleanedTag)) {
        onChange([...value, cleanedTag]);
        setInputValue("");
      }
    }
  };

  const handleInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    if (!newValue.includes(",")) {
      setInputValue(newValue);
    }
  };

  const removeTag = (tagToRemove: string) => {
    onChange(value.filter(tag => tag !== tagToRemove));
  };

  const handleContainerClick = () => {
    inputRef.current?.focus();
  };

  const cleanedTags = value.map(tag => sanitizeTag(tag)).filter(Boolean);

  return (
    <div
      onClick={handleContainerClick}
      className={`min-h-10 flex flex-wrap gap-2 p-2 rounded-md border bg-background
        ${disabled ? "opacity-50 cursor-not-allowed" : "cursor-text"}
        ${error ? "border-destructive" : "border-input hover:border-primary focus-within:border-primary"}
        ${className}
      `}
    >
      {cleanedTags.map((tag) => (
        <span
          key={tag}
          className="inline-flex items-center gap-1.5 bg-primary/10 text-primary px-2.5 py-1 rounded-md text-sm font-medium"
        >
          {tag}
          {!disabled && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                removeTag(tag);
              }}
              className="rounded-full hover:bg-primary/20 transition-colors p-0.5"
            >
              <X className="h-3.5 w-3.5" />
              <span className="sr-only">Remove tag</span>
            </button>
          )}
        </span>
      ))}
      <Input
        ref={inputRef}
        type="text"
        value={inputValue}
        onChange={handleInput}
        onKeyDown={handleKeyDown}
        placeholder={value.length === 0 ? placeholder : ""}
        disabled={disabled}
        className="flex-1 !h-6 !min-h-6 !p-0 !border-0 !ring-0 !shadow-none focus-visible:ring-0 bg-transparent placeholder:text-muted-foreground"
      />
    </div>
  );
};

export default TagInput;