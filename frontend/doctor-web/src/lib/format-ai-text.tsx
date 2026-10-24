import React from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

interface FormattedAiTextProps {
  content: string;
  className?: string;
}

export function FormattedAiText({ content, className = "" }: FormattedAiTextProps) {
  if (!content) return null;

  return (
    <div className={`prose-sm max-w-none text-xs leading-relaxed text-slate-800 space-y-1.5 ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children }) => (
            <h1 className="text-sm font-bold text-[#2B1D30] mt-2.5 mb-1 tracking-tight border-b border-slate-200 pb-1">
              {children}
            </h1>
          ),
          h2: ({ children }) => (
            <h2 className="text-xs font-bold text-[#2B1D30] mt-2 mb-1 tracking-tight">
              {children}
            </h2>
          ),
          h3: ({ children }) => (
            <h3 className="text-xs font-bold text-[#2B1D30] mt-2 mb-1 tracking-tight">
              {children}
            </h3>
          ),
          h4: ({ children }) => (
            <h4 className="text-xs font-bold text-[#2B1D30] mt-1.5 mb-0.5 tracking-tight text-[#6E2582]">
              {children}
            </h4>
          ),
          p: ({ children }) => (
            <p className="text-xs text-slate-700 leading-relaxed mb-1.5 last:mb-0">
              {children}
            </p>
          ),
          ul: ({ children }) => (
            <ul className="list-disc list-outside pl-4 space-y-1 my-1.5 text-xs text-slate-700">
              {children}
            </ul>
          ),
          ol: ({ children }) => (
            <ol className="list-decimal list-outside pl-4 space-y-1 my-1.5 text-xs text-slate-700">
              {children}
            </ol>
          ),
          li: ({ children }) => (
            <li className="text-xs text-slate-700 leading-relaxed pl-0.5">
              {children}
            </li>
          ),
          strong: ({ children }) => (
            <strong className="font-bold text-[#2B1D30]">
              {children}
            </strong>
          ),
          blockquote: ({ children }) => (
            <blockquote className="border-l-3 border-[#6E2582] pl-2.5 py-0.5 my-1.5 text-slate-600 bg-purple-50/40 rounded-r text-[11px] italic">
              {children}
            </blockquote>
          ),
          table: ({ children }) => (
            <div className="overflow-x-auto my-2 border border-slate-200 rounded-md">
              <table className="w-full text-xs text-left border-collapse">
                {children}
              </table>
            </div>
          ),
          thead: ({ children }) => (
            <thead className="bg-slate-50 text-slate-800 font-semibold border-b border-slate-200 text-[11px]">
              {children}
            </thead>
          ),
          th: ({ children }) => (
            <th className="px-2.5 py-1.5 border border-slate-200">
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td className="px-2.5 py-1.5 border border-slate-200 text-slate-700">
              {children}
            </td>
          ),
          code: ({ children }) => (
            <code className="font-mono text-[11px] bg-slate-100 text-purple-900 px-1 py-0.5 rounded border border-slate-200">
              {children}
            </code>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}

// Backward compatible helper function
export function renderFormattedAiText(text: string) {
  if (!text) return null;
  return <FormattedAiText content={text} />;
}
