import { Download } from "lucide-react";
import React from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";

interface XmlViewerProps {
  content: string;
  onDownload: () => void;
  isDownloading?: boolean;
  loading?: boolean;
}

interface FormattedXmlPart {
  type: "tag" | "attribute" | "attributeValue" | "text" | "comment" | "cdata";
  content: string;
}

export const XmlViewer: React.FC<XmlViewerProps> = ({ content, onDownload, isDownloading, loading }) => {
  const { t } = useTranslation();

  const formatXml = (xmlString: string): string => {
    try {
      const parser = new DOMParser();
      const xmlDoc = parser.parseFromString(xmlString, "text/xml");

      const parseError = xmlDoc.getElementsByTagName("parsererror");
      if (parseError.length > 0) {
        return xmlString;
      }

      const format = (node: Node, level: number): string => {
        const indent = "  ".repeat(level);

        if (node.nodeType === Node.TEXT_NODE) {
          const text = node.textContent?.trim() || "";
          return text ? `${indent}${text}\n` : "";
        }

        if (node.nodeType === Node.COMMENT_NODE) {
          const comment = node.textContent?.trim() || "";
          return `${indent}<!--${comment}-->\n`;
        }

        if (node.nodeType === Node.CDATA_SECTION_NODE) {
          const cdata = node.textContent || "";
          return `${indent}<![CDATA[${cdata}]]>\n`;
        }

        if (node.nodeType === Node.ELEMENT_NODE) {
          const element = node as Element;
          let result = indent + "<" + element.tagName;

          const attributes = element.attributes;
          for (let i = 0; i < attributes.length; i++) {
            const attr = attributes[i];
            result += ` ${attr.name}="${attr.value}"`;
          }

          const childNodes = element.childNodes;
          if (childNodes.length === 0) {
            return result + "/>\n";
          }

          result += ">";

          if (childNodes.length === 1 && childNodes[0].nodeType === Node.TEXT_NODE) {
            const text = childNodes[0].textContent?.trim() || "";
            if (text) {
              return result + text + "</" + element.tagName + ">\n";
            }
          }

          result += "\n";

          for (let i = 0; i < childNodes.length; i++) {
            result += format(childNodes[i], level + 1);
          }

          return result + indent + "</" + element.tagName + ">\n";
        }

        return "";
      };

      let formatted = '<?xml version="1.0" encoding="UTF-8"?>\n';
      const rootNode = xmlDoc.documentElement;
      formatted += format(rootNode, 0);

      return formatted;
    } catch (e) {
      console.error("Error formatting XML:", e);
      return xmlString;
    }
  };

  const highlightXml = (xmlContent: string): FormattedXmlPart[] => {
    const parts: FormattedXmlPart[] = [];
    let currentPart = "";
    let inTag = false;
    let inAttribute = false;
    let inAttributeValue = false;
    let inComment = false;
    let inCDATA = false;
    let quoteChar = "";

    const pushCurrentPart = (type: FormattedXmlPart["type"]) => {
      if (currentPart) {
        parts.push({ type, content: currentPart });
        currentPart = "";
      }
    };

    for (let i = 0; i < xmlContent.length; i++) {
      const char = xmlContent[i];
      const nextChar = xmlContent[i + 1];

      if (inComment) {
        currentPart += char;
        if (char === "-" && nextChar === "-" && xmlContent[i + 2] === ">") {
          currentPart += "-->";
          pushCurrentPart("comment");
          inComment = false;
          i += 2;
        }
      } else if (inCDATA) {
        currentPart += char;
        if (char === "]" && nextChar === "]" && xmlContent[i + 2] === ">") {
          currentPart += "]]>";
          pushCurrentPart("cdata");
          inCDATA = false;
          i += 2;
        }
      } else if (char === "<") {
        if (nextChar === "!") {
          if (xmlContent.substring(i, 4) === "<!--") {
            pushCurrentPart("text");
            currentPart = "<!--";
            inComment = true;
            i += 3;
            continue;
          } else if (xmlContent.substring(i, 9) === "<![CDATA[") {
            pushCurrentPart("text");
            currentPart = "<![CDATA[";
            inCDATA = true;
            i += 8;
            continue;
          }
        }
        pushCurrentPart("text");
        currentPart = char;
        inTag = true;
      } else if (char === ">") {
        currentPart += char;
        pushCurrentPart("tag");
        inTag = false;
        inAttribute = false;
        inAttributeValue = false;
      } else if (inTag && /\s/.test(char)) {
        if (!inAttributeValue) {
          pushCurrentPart("tag");
          currentPart = char;
          inAttribute = true;
        } else {
          currentPart += char;
        }
      } else if (inAttribute && (char === '"' || char === "'")) {
        if (!inAttributeValue) {
          pushCurrentPart("attribute");
          quoteChar = char;
          inAttributeValue = true;
        } else if (char === quoteChar) {
          currentPart += char;
          pushCurrentPart("attributeValue");
          inAttributeValue = false;
          inAttribute = false;
        }
        currentPart += char;
      } else {
        currentPart += char;
      }
    }

    pushCurrentPart("text");
    return parts;
  };

  const formattedContent = formatXml(content);
  const highlightedParts = highlightXml(formattedContent);

  return (
    <div className="h-full flex flex-col">
      <div className="flex justify-end p-2 bg-muted">
        <Button onClick={onDownload} variant="outline" size="sm" disabled={isDownloading || loading}>
          <Download className="h-4 w-4 mr-2" />
          {!isDownloading ? t("document.viewer.buttons.download") : t("document.viewer.buttons.downloading")}
        </Button>
      </div>
      <ScrollArea className="flex-1">
        <pre className="p-4 text-sm font-mono whitespace-pre bg-white overflow-x-auto">
          <code className="text-foreground">
            {highlightedParts.map((part, index) => {
              let className: string;
              switch (part.type) {
                case "tag":
                  className = "text-blue-600";
                  break;
                case "attribute":
                  className = "text-purple-600";
                  break;
                case "attributeValue":
                  className = "text-green-600";
                  break;
                case "comment":
                  className = "text-gray-500 italic";
                  break;
                case "cdata":
                  className = "text-orange-600";
                  break;
                default:
                  className = "text-foreground";
              }
              return (
                <span key={index} className={className}>
                  {part.content}
                </span>
              );
            })}
          </code>
        </pre>
      </ScrollArea>
    </div>
  );
};
