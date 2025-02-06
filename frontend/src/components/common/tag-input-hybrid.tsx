import { debounce } from "lodash";
import { X } from "lucide-react";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

interface TagInputHybridProps {
  value: string[];
  onChange: (tags: string[]) => void;
  recommendedTags?: string[];
  onSearch?: (query: string) => Promise<string[]>;
  placeholder?: string;
  disabled?: boolean;
  error?: boolean;
  className?: string;
  getTagDisplay?: (tag: string) => string; // New prop for custom tag display
}

const TagInputHybrid = ({
                          value,
                          onChange,
                          recommendedTags = [],
                          onSearch,
                          placeholder = "Enter tags...",
                          disabled = false,
                          error = false,
                          className = "",
                          getTagDisplay = (tag) => tag
                        }: TagInputHybridProps) => {
  const [inputValue, setInputValue] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const [dropdownStyle, setDropdownStyle] = useState({ top: 0, left: 0, width: 0 });

  const updateDropdownPosition = () => {
    if (containerRef.current) {
      const rect = containerRef.current.getBoundingClientRect();
      setDropdownStyle({
        top: rect.bottom + window.scrollY,
        left: rect.left + window.scrollX,
        width: rect.width
      });
    }
  };

  useEffect(() => {
    window.addEventListener("scroll", updateDropdownPosition);
    window.addEventListener("resize", updateDropdownPosition);
    return () => {
      window.removeEventListener("scroll", updateDropdownPosition);
      window.removeEventListener("resize", updateDropdownPosition);
    };
  }, []);

  const fetchSuggestions = async (prefix?: string) => {
    if (!showSuggestions || !onSearch) return;

    try {
      setLoading(true);
      const results = await onSearch(prefix || "");
      setSuggestions(results.filter(tag => !value.includes(tag)));
    } catch (error) {
      console.error("Error fetching tag suggestions:", error);
      setSuggestions([]);
    } finally {
      setLoading(false);
    }
  };

  const debouncedFetchSuggestions = useCallback(
    debounce((prefix: string) => {
      fetchSuggestions(prefix);
    }, 350),
    [value, showSuggestions]
  );

  useEffect(() => {
    if (inputValue.trim() && showSuggestions) {
      debouncedFetchSuggestions(inputValue);
    }

    return () => {
      debouncedFetchSuggestions.cancel();
    };
  }, [inputValue, showSuggestions, debouncedFetchSuggestions]);

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

    // Prevent comma and Enter from adding manual tags
    if (event.key === "," || event.key === "Enter") {
      event.preventDefault();
    }
  };

  const addTag = (tag: string) => {
    // Verify the tag exists in either recommendations or search results
    const isValidTag = recommendedTags.includes(tag) || suggestions.includes(tag);
    if (isValidTag && !value.includes(tag)) {
      onChange([...value, tag]);
    }
    setInputValue("");
    setShowSuggestions(false);
  };

  const removeTag = (tagToRemove: string) => {
    onChange(value.filter(tag => tag !== tagToRemove));
  };

  const handleFocus = () => {
    setShowSuggestions(true);
    updateDropdownPosition();
  };

  const SuggestionsDropdown = () => {
    // Filter out already selected tags from both recommended tags and search results
    const filteredRecommendedTags = recommendedTags.filter(tag => !value.includes(tag));
    const filteredSuggestions = suggestions.filter(tag =>
      !value.includes(tag) && !recommendedTags.includes(tag)
    );

    // Combine filtered results
    const allSuggestions = [...filteredRecommendedTags, ...filteredSuggestions];

    if (!showSuggestions || !allSuggestions.length) return null;

    return createPortal(
      <div
        style={{
          position: "absolute",
          top: `${dropdownStyle.top}px`,
          left: `${dropdownStyle.left}px`,
          width: `${dropdownStyle.width}px`,
          zIndex: 9999
        }}
        className="bg-popover rounded-md border shadow-md"
      >
        <div className="p-1">
          {loading ? (
            <div className="text-sm text-muted-foreground px-2 py-1.5">
              Loading...
            </div>
          ) : (
            <>
              {filteredRecommendedTags.length > 0 && (
                <div className="px-2 py-1 text-xs text-muted-foreground font-medium">
                  Recommended Tags
                </div>
              )}
              {filteredRecommendedTags.map((tag) => (
                <button
                  key={`recommended-${tag}`}
                  className="w-full text-left px-2 py-1.5 text-sm rounded hover:bg-accent hover:text-accent-foreground flex items-center gap-2"
                  onClick={() => addTag(tag)}
                >
                  {getTagDisplay(tag)}
                </button>
              ))}
              {filteredSuggestions.length > 0 && filteredRecommendedTags.length > 0 && (
                <div className="border-t my-1" />
              )}
              {filteredSuggestions.map((suggestion) => (
                <button
                  key={suggestion}
                  className="w-full text-left px-2 py-1.5 text-sm rounded hover:bg-accent hover:text-accent-foreground"
                  onClick={() => addTag(suggestion)}
                >
                  {getTagDisplay(suggestion)}
                </button>
              ))}
            </>
          )}
        </div>
      </div>,
      document.body
    );
  };

  return (
    <div ref={containerRef} className="relative">
      <div
        className={cn(
          "min-h-10 flex flex-wrap gap-2 p-2 rounded-md border bg-background",
          disabled ? "opacity-50 cursor-not-allowed" : "cursor-text",
          error ? "border-destructive" : "border-input hover:border-primary focus-within:border-primary",
          className
        )}
      >
        {value.map((tag) => (
          <span
            key={tag}
            className="inline-flex items-center gap-1.5 bg-primary/10 text-primary px-2.5 py-1 rounded-md text-sm font-medium"
          >
            {getTagDisplay(tag)}
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
          onBlur={() => {
            setTimeout(() => setShowSuggestions(false), 200);
          }}
          placeholder={value.length === 0 ? placeholder : ""}
          disabled={disabled}
          className="flex-1 !h-6 !min-h-6 !p-0 !border-0 !ring-0 !shadow-none focus-visible:ring-0 bg-transparent placeholder:text-muted-foreground"
        />
      </div>
      <SuggestionsDropdown />
    </div>
  );
};

export default TagInputHybrid;