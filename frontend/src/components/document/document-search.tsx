import { debounce } from "lodash";
import { Search } from "lucide-react";
import React, { useCallback, useState } from "react";

import { Input } from "@/components/ui/input";

interface DocumentSearchProps {
  onSearch: (query: string) => void;
  placeholder?: string;
  debounceMs?: number;
  className?: string;
}

const DocumentSearch = ({
                          onSearch,
                          placeholder = "Search documents...",
                          debounceMs = 350,
                          className = ""
                        }: DocumentSearchProps) => {
  const [searchTerm, setSearchTerm] = useState("");

  const debouncedSearch = useCallback(
    debounce((query: string) => {
      onSearch(query);
    }, debounceMs),
    [onSearch, debounceMs]
  );

  // Handle input change
  const handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = event.target.value;
    setSearchTerm(newValue);
    debouncedSearch(newValue);
  };

  return (
    <div className={`relative flex items-center ${className}`}>
      <Search className="absolute left-2.5 h-4 w-4 text-muted-foreground" />
      <Input
        type="text"
        placeholder={placeholder}
        value={searchTerm}
        onChange={handleSearchChange}
        className="pl-8"
      />
    </div>
  );
};

export default DocumentSearch;