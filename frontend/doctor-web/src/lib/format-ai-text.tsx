import React from "react";

export function renderFormattedAiText(text: string) {
  if (!text) return null;

  const lines = text.split("\n");

  return (
    <div className="space-y-1.5 leading-relaxed">
      {lines.map((line, lineIdx) => {
        const trimmed = line.trim();
        if (!trimmed) return <div key={lineIdx} className="h-1" />;

        // Check if bullet point (*, -, •, 1., 2.)
        const isBullet = /^[*\-•]\s/.test(trimmed) || /^\d+\.\s/.test(trimmed);
        const bulletMatch = trimmed.match(/^([*\-•]|\d+\.)\s/);
        const bulletSymbol = bulletMatch ? bulletMatch[1] : "•";
        const content = bulletMatch ? trimmed.substring(bulletMatch[0].length) : trimmed;

        // Parse **bold** parts
        const parts = content.split(/(\*\*.*?\*\*)/g);

        const renderedContent = parts.map((part, pIdx) => {
          if (part.startsWith("**") && part.endsWith("**") && part.length > 4) {
            return (
              <strong key={pIdx} className="font-bold text-[#2B1D30]">
                {part.slice(2, -2)}
              </strong>
            );
          }
          return part;
        });

        if (isBullet) {
          return (
            <div key={lineIdx} className="flex items-start gap-1.5 ml-1">
              <span className="text-purple-700 font-bold flex-shrink-0 font-mono text-[11px]">{bulletSymbol}</span>
              <span className="flex-1">{renderedContent}</span>
            </div>
          );
        }

        return <div key={lineIdx}>{renderedContent}</div>;
      })}
    </div>
  );
}
