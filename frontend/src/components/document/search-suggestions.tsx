import React, { useCallback, useRef, useState } from 'react';
import { Command } from "@/components/ui/command";
import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { debounce } from 'lodash';
import { searchService } from '@/services/search.service';

interface SearchSuggestionsProps {
  onSearch: (query: string) => void;
  placeholder?: string;
  className?: string;
  debounceMs?: number;
}

const SearchSuggestions = ({
                             onSearch,
                             placeholder = "Search documents...",
                             debounceMs = 350,
                             className = ""
                           }: SearchSuggestionsProps) => {
  const [inputValue, setInputValue] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

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
        console.error('Error fetching suggestions:', error);
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

    if (!value.trim()) {
      onSearch("");
      setSuggestions([]);
      return;
    }

    setLoading(true);
    fetchSuggestions(value);
  };

  // Handle suggestion click
  const handleSuggestionClick = (suggestion: string) => {
    setInputValue(suggestion);
    onSearch(suggestion);
    setSuggestions([]);
  };

  // Handle enter key to perform search with current input
  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      onSearch(inputValue);
      setSuggestions([]);
    }
  };

  return (
    <div ref={containerRef} className={cn("relative max-w-md", className)}>
      <div className="relative">
        <div className="relative rounded-lg border shadow-md">
          <div className="flex items-center px-3">
            <input
              value={inputValue}
              onChange={(e) => handleInputChange(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={placeholder}
              className="flex h-9 w-full rounded-md bg-transparent py-3 text-sm outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50"
            />
            {loading && <Loader2 className="h-4 w-4 animate-spin ml-2" />}
          </div>
          {suggestions.length > 0 && (
            <div className="absolute mt-2 w-full rounded-md border bg-popover text-popover-foreground shadow-md z-50">
              <div className="overflow-hidden p-1">
                {suggestions.map((suggestion) => (
                  <div
                    key={suggestion}
                    className="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none hover:bg-accent hover:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
                    onClick={() => handleSuggestionClick(suggestion)}
                  >
                    {suggestion}
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