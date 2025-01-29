import { FilesetResolver, LanguageDetector } from "@mediapipe/tasks-text";
import { useCallback, useEffect, useState } from "react";

const getBaseUrl = () => {
  const isDev = import.meta.env.DEV;
  return isDev ? window.location.origin :
    import.meta.env.BASE_URL ?
      new URL(import.meta.env.BASE_URL, window.location.origin).toString() :
      window.location.origin;
};

export function useLanguageDetection() {
  const [languageDetector, setLanguageDetector] = useState<LanguageDetector | null>(null);
  const [detectedLanguage, setDetectedLanguage] = useState<string>("");
  const [detectingLanguage, setDetectingLanguage] = useState(false);

  // Initialize MediaPipe Language Detector
  useEffect(() => {
    const baseUrl = getBaseUrl();

    const initializeLanguageDetector = async () => {
      const text = await FilesetResolver.forTextTasks(
        `${baseUrl}/node_modules/@mediapipe/tasks-text/wasm`
      );
      const detector = await LanguageDetector.createFromOptions(text, {
        baseOptions: {
          modelAssetPath: `${baseUrl}/models/language_detector.tflite`,
        },
        maxResults: 1
      });
      setLanguageDetector(detector);
    };

    initializeLanguageDetector();
  }, []);

  // Detect language function
  const detectLanguage = useCallback(async (text: string) => {
    if (!languageDetector || text.length < 10) {
      setDetectedLanguage("");
      return;
    }

    setDetectingLanguage(true);
    try {
      const result = await languageDetector.detect(text);
      if (result.languages.length > 0) {
        setDetectedLanguage(result.languages[0].languageCode);
      }
    } catch (error) {
      console.error("Language detection error:", error);
      setDetectedLanguage("");
    } finally {
      setDetectingLanguage(false);
    }
  }, [languageDetector]);

  return {
    detectLanguage,
    detectedLanguage,
    detectingLanguage
  };
}