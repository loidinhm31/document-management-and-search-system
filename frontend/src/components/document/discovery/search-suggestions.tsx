import { debounce } from "lodash";
import { Loader2, Search } from "lucide-react";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { cn } from "@/lib/utils";
import { searchService } from "@/services/search.service";

interface SearchSuggestionsProps {
  onSearch: (query: string) => void;
  placeholder?: string;
  className?: string;
  debounceMs?: number;
}

const SearchSuggestions = ({
                             onSearch,
                             debounceMs = 350,
                             className = ""
                           }: SearchSuggestionsProps) => {
  const { t } = useTranslation();

  const [inputValue, setInputValue] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Fetch suggestions with debounce
  const fetchSuggestions = useCallback(
    debounce(async (query: string) => {
      if (!query.trim()) {
        setSuggestions([]);
        setLoading(false);
        return;
      }

      try {
        const response = await searchService.suggestions(query);
        setSuggestions(response.data || []);
      } catch (error) {
        console.error("Error fetching suggestions:", error);
        setSuggestions([]);
      } finally {
        setLoading(false);
      }
    }, debounceMs),
    []
  );

  // Handle input change
  const handleInputChange = (value: string) => {
    setInputValue(value);
    setSelectedIndex(-1);

    if (!value.trim()) {
      onSearch("");
      setSuggestions([]);
      return;
    }

    setLoading(true);
    fetchSuggestions(value);
  };

  // Handle keyboard navigation
  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        if (suggestions.length > 0) {
          setSelectedIndex(prev =>
            prev < suggestions.length - 1 ? prev + 1 : prev
          );
        }
        break;
      case "ArrowUp":
        event.preventDefault();
        if (suggestions.length > 0) {
          setSelectedIndex(prev => prev > -1 ? prev - 1 : prev);
        }
        break;
      case "Enter":
        event.preventDefault();
        if (selectedIndex >= 0 && suggestions[selectedIndex]) {
          handleSuggestionSelect(suggestions[selectedIndex]);
        } else {
          // If no suggestion is selected, search with current input
          onSearch(inputValue);
          setSuggestions([]); // Clear suggestions
          setSelectedIndex(-1);
        }
        break;
      case "Escape":
        setSuggestions([]);
        setSelectedIndex(-1);
        break;
    }
  };

  // Handle suggestion click or selection
  const handleSuggestionSelect = (suggestion: string) => {
    // Strip HTML tags to get clean text
    const cleanText = suggestion.replace(/<\/?[^>]+(>|$)/g, "");
    setInputValue(cleanText);
    onSearch(cleanText);
    setSuggestions([]);
    setSelectedIndex(-1);
  };

  // Handle clicks outside of the component
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setSuggestions([]);
        setSelectedIndex(-1);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div ref={containerRef} className={cn("w-full", className)}>
      <div className="relative">
        <div className="relative rounded-lg border shadow-md">
          <div className="flex items-center px-3 gap-2">
            <Search className="h-4 w-4 shrink-0 opacity-50" />
            <input
              ref={inputRef}
              value={inputValue}
              onChange={(e) => handleInputChange(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={t("document.search.placeholder")}
              className="flex h-9 w-full rounded-md bg-transparent py-3 text-sm outline-none"
            />
            {loading && <Loader2 className="h-4 w-4 animate-spin" />}
          </div>
          {suggestions.length > 0 && (
            <div className="absolute mt-2 w-full rounded-md border bg-popover text-popover-foreground shadow-md z-50">
              <div className="overflow-hidden p-1">
                {suggestions.map((suggestion, index) => (
                  <div
                    key={index}
                    className={cn(
                      "relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none whitespace-nowrap overflow-hidden",
                      "hover:bg-accent hover:text-accent-foreground",
                      "data-[disabled]:pointer-events-none data-[disabled]:opacity-50",
                      index === selectedIndex && "bg-accent text-accent-foreground"
                    )}
                    onClick={() => handleSuggestionSelect(suggestion)}
                  >
                    <div
                      className="truncate"
                      dangerouslySetInnerHTML={{ __html: suggestion }}
                    />
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default SearchSuggestions;