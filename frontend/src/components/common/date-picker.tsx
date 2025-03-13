import { Calendar as CalendarIcon, X } from "lucide-react";
import moment from "moment-timezone";
import React, { useState } from "react";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

interface DatePickerProps {
  value?: Date;
  onChange: (date?: Date) => void;
  placeholder: string;
  clearAriaLabel?: string;
  disabled?: boolean;
}

const DatePicker = ({
  value,
  onChange,
  placeholder,
  clearAriaLabel = "Clear date",
  disabled = false,
}: DatePickerProps) => {
  const [open, setOpen] = useState(false);
  const formattedDate = value ? moment(value).format("DD/MM/YYYY") : placeholder;

  // Handle clear button - explicitly stop propagation
  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation();
    e.preventDefault();
    onChange(undefined);
  };

  // Handle date selection
  const handleSelect = (date: Date | undefined) => {
    onChange(date);
    // Close popover after selecting a date
    if (date) {
      setOpen(false);
    }
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="outline" className="flex flex-1 justify-start text-left" disabled={disabled}>
          <CalendarIcon className="mr-2 h-4 w-4" />
          <span className={cn("flex-grow", !value && "text-muted-foreground")}>{formattedDate}</span>
          {value && (
            <div
              role="button"
              onClick={handleClear}
              className="ml-auto rounded-full p-0.5 hover:bg-muted"
              aria-label={clearAriaLabel}
            >
              <X className="h-4 w-4 cursor-pointer" />
            </div>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar mode="single" selected={value} onSelect={handleSelect} initialFocus />
      </PopoverContent>
    </Popover>
  );
};

export default DatePicker;
