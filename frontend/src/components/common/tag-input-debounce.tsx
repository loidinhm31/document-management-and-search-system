import { debounce } from "lodash";
import { X } from "lucide-react";
import React, { useCallback, useEffect, useRef, useState } from "react";

import { Input } from "@/components/ui/input";
import { documentService } from "@/services/document.service";

interface TagInputProps {
  value: string[];
  onChange: (tags: string[]) => void;
  placeholder?: string;
  disabled?: boolean;
  error?: boolean;
  className?: string;
}

const TagInputDebounce = ({
  value,
  onChange,
  placeholder = "Enter or select tags...",
  disabled = false,
  error = false,
  className = "",
}: TagInputProps) => {
  const [inputValue, setInputValue] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const [hasFetchedInitialSuggestions, setHasFetchedInitialSuggestions] = useState(false);
  const mouseIsDownRef = useRef(false);

  const fetchTags = async (prefix?: string) => {
    if (!showSuggestions) return; // Only fetch if suggestions are being shown

    try {
      setLoading(true);
      const response = await documentService.getTagSuggestions(prefix);
      setSuggestions(
        response.data
          .filter((tag) => !value.includes(tag)) // Filter out already selected tags
          .slice(0, 5), // Limit to 5 suggestions
      );
    } catch (error) {
      console.info("Error fetching tag suggestions:", error);
      setSuggestions([]);
    } finally {
      setLoading(false);
    }
  };

  const debouncedFetchTags = useCallback(
    debounce((prefix: string) => {
      fetchTags(prefix);
    }, 350),
    [value, showSuggestions],
  );

  useEffect(() => {
    if (inputValue.trim() && showSuggestions) {
      debouncedFetchTags(inputValue);
    }

    return () => {
      debouncedFetchTags.cancel();
    };
  }, [inputValue, showSuggestions, debouncedFetchTags]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    if (!newValue.includes(",")) {
      setInputValue(newValue);
    }
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Backspace" && inputValue === "" && value.length > 0) {
      onChange(value.slice(0, -1));
    }

    if ((event.key === "," || event.key === "Enter") && inputValue.trim()) {
      event.preventDefault();
      addTag(inputValue.trim());
    }

    if (showSuggestions && suggestions.length > 0) {
      if (event.key === "ArrowDown" && suggestions.length > 0) {
        event.preventDefault();
        addTag(suggestions[0]);
      }
    }
  };

  const addTag = (tag: string) => {
    const cleanedTag = tag.trim();
    if (cleanedTag && !value.includes(cleanedTag)) {
      onChange([...value, cleanedTag]);
    }
    setInputValue("");
    setShowSuggestions(false);
  };

  const removeTag = (tagToRemove: string) => {
    onChange(value.filter((tag) => tag !== tagToRemove));
  };

  const handleFocus = () => {
    setShowSuggestions(true);

    // Only fetch initial suggestions on first focus
    if (!hasFetchedInitialSuggestions) {
      fetchTags();
      setHasFetchedInitialSuggestions(true);
    }
  };

  // Add global mouse down/up event listeners
  useEffect(() => {
    const handleMouseDown = () => {
      mouseIsDownRef.current = true;
    };

    const handleMouseUp = () => {
      mouseIsDownRef.current = false;
    };

    document.addEventListener("mousedown", handleMouseDown);
    document.addEventListener("mouseup", handleMouseUp);

    return () => {
      document.removeEventListener("mousedown", handleMouseDown);
      document.removeEventListener("mouseup", handleMouseUp);
    };
  }, []);

  // Handle blur more carefully
  const handleBlur = () => {
    // Delay the closing of suggestions so click events can complete
    setTimeout(() => {
      if (!mouseIsDownRef.current) {
        setShowSuggestions(false);
      }
    }, 300);
  };

  return (
    <div ref={containerRef} className="relative w-full">
      <div
        className={`min-h-10 flex flex-wrap gap-2 p-2 rounded-md border bg-background
          ${disabled ? "opacity-50 cursor-not-allowed" : "cursor-text"}
          ${error ? "border-destructive" : "border-input hover:border-primary focus-within:border-primary"}
          ${className}
        `}
      >
        {value.map((tag) => (
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
          type="text"
          value={inputValue}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          onFocus={handleFocus}
          onBlur={handleBlur}
          placeholder={value.length === 0 ? placeholder : ""}
          disabled={disabled}
          className="flex-1 !h-6 !min-h-6 !p-0 !border-0 !ring-0 !shadow-none focus-visible:ring-0 bg-transparent placeholder:text-muted-foreground"
        />
      </div>

      {showSuggestions && suggestions.length > 0 && (
        <div
          className="absolute left-0 right-0 mt-1 bg-popover rounded-md border shadow-md z-50"
          style={{ minWidth: "100%" }}
        >
          <div className="p-1">
            {loading ? (
              <div className="text-sm text-muted-foreground px-2 py-1.5">Loading...</div>
            ) : (
              suggestions.map((suggestion) => (
                <button
                  key={suggestion}
                  className="w-full text-left px-2 py-1.5 text-sm rounded hover:bg-accent hover:text-accent-foreground"
                  // Handle mouse events more directly
                  onMouseDown={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    addTag(suggestion);
                  }}
                >
                  {suggestion}
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default TagInputDebounce;
