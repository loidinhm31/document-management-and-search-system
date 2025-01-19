import { Download } from "lucide-react";
import React, { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";

interface PowerPointViewerProps {
  content: string[];
  onDownload: () => void;
}

export const PowerPointViewer: React.FC<PowerPointViewerProps> = ({ content, onDownload }) => {
  const [currentSlide, setCurrentSlide] = useState(0);

  const handlePrevSlide = () => {
    setCurrentSlide((prev) => (prev > 0 ? prev - 1 : prev));
  };

  const handleNextSlide = () => {
    setCurrentSlide((prev) => (prev < content.length - 1 ? prev + 1 : prev));
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "ArrowLeft") {
        handlePrevSlide();
      } else if (e.key === "ArrowRight") {
        handleNextSlide();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  return (
    <div className="h-full flex flex-col">
      <div className="flex justify-between p-2 bg-muted">
        <div className="flex items-center gap-2">
          <Button
            onClick={handlePrevSlide}
            disabled={currentSlide === 0}
            variant="outline"
            size="sm"
          >
            Previous
          </Button>
          <Button
            onClick={handleNextSlide}
            disabled={currentSlide === content.length - 1}
            variant="outline"
            size="sm"
          >
            Next
          </Button>
          <span className="text-sm">
            Slide {currentSlide + 1} of {content.length}
          </span>
        </div>
        <Button onClick={onDownload} variant="outline" size="sm">
          <Download className="h-4 w-4 mr-2" />
          Download
        </Button>
      </div>
      <div className="flex-1 overflow-auto bg-white p-4">
        <div
          className="w-full h-full"
          dangerouslySetInnerHTML={{ __html: content[currentSlide] || "" }}
        />
      </div>
    </div>
  );
};