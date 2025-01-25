import { debounce } from "lodash";
import { Loader2, Search } from "lucide-react";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { cn } from "@/lib/utils";
import { searchService, SearchFilters } from "@/services/search.service";

interface SearchSuggestionsProps {
  onSearch: (query: string) => void;
  onInputChange?: (value: string) => void;
  placeholder?: string;
  className?: string;
  debounceMs?: number;
  filters?: Omit<SearchFilters, "search" | "sort">;
}

const SearchSuggestions = ({
                             onSearch,
                             onInputChange,
                             debounceMs = 350,
                             className = "",
                             placeholder,
                             filters
                           }: SearchSuggestionsProps) => {
  const [inputValue, setInputValue] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [showSuggestions, setShowSuggestions] = useState(false);

  // Fetch suggestions with debounce
  const fetchSuggestions = useCallback(
    debounce(async (query: string) => {
      if (!query.trim() || !showSuggestions) {
        setSuggestions([]);
        setLoading(false);
        return;
      }

      try {
        // Include filters in suggestions request
        const response = await searchService.suggestions(query, filters);
        setSuggestions(response.data || []);
      } catch (error) {
        console.error("Error fetching suggestions:", error);
        setSuggestions([]);
      } finally {
        setLoading(false);
      }
    }, debounceMs),
    [showSuggestions, filters] // Add filters to dependencies
  );

  // Refetch suggestions when filters change
  useEffect(() => {
    if (inputValue && showSuggestions) {
      fetchSuggestions(inputValue);
    }
  }, [filters, inputValue, showSuggestions]);

  // Handle input change
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setInputValue(value);
    if (onInputChange) {
      onInputChange(value);
    }
    setSelectedIndex(-1);
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
          onSearch(inputValue);
          setSuggestions([]);
          setSelectedIndex(-1);
        }
        break;
      case "Escape":
        setSuggestions([]);
        setSelectedIndex(-1);
        break;
    }
  };

  // Handle suggestion selection
  const handleSuggestionSelect = (suggestion: string) => {
    const cleanText = suggestion.replace(/<\/?[^>]+(>|$)/g, "")
      .replace(/[\n\t]+/g, " ").trim();

    setInputValue(cleanText);
    if (onInputChange) {
      onInputChange(cleanText);
    }
    onSearch(cleanText);
    setSuggestions([]);
    setShowSuggestions(false);
  };

  // Handle clicks outside of the component
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setSuggestions([]);
        setSelectedIndex(-1);
        setShowSuggestions(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);


  const cleanSuggestion = (suggestion: string) => {
    // Replace newlines and tabs with a single space and trim any excess whitespace
    return suggestion.replace(/[\n\t]+/g, " ").trim();
  };

  return (
    <div ref={containerRef} className={cn("w-full relative", className)}>
      <div className="relative rounded-lg border shadow-sm">
        <div className="flex items-center px-3 gap-2">
          <Search className="h-4 w-4 shrink-0 opacity-50" />
          <input
            ref={inputRef}
            value={inputValue}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            onFocus={() => setShowSuggestions(true)}
            placeholder={placeholder}
            className="flex h-9 w-full rounded-md bg-transparent py-3 text-sm outline-none"
          />
          {loading && <Loader2 className="h-4 w-4 animate-spin" />}
        </div>
      </div>

      {suggestions.length > 0 && showSuggestions && (
        <div className="absolute mt-2 w-full rounded-md border bg-popover text-popover-foreground shadow-md z-50">
          <div className="overflow-hidden p-1">
            {suggestions.map((suggestion, index) => (
              <div
                key={index}
                className={cn(
                  "w-full text-left px-2 py-1.5 text-sm rounded whitespace-pre-wrap break-words",
                  "hover:bg-accent hover:text-accent-foreground",
                  index === selectedIndex && "bg-accent text-accent-foreground"
                )}
                onClick={() => handleSuggestionSelect(suggestion)}
                dangerouslySetInnerHTML={{ __html: `...${cleanSuggestion(suggestion)}...` }}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default SearchSuggestions;