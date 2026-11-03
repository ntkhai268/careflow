"use client";

import { useEffect, useRef, useState } from "react";

export default function HandoverPicker({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: { value: string; label: string }[];
  value: string;
  onChange: (value: string) => void;
}) {
  const root = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");

  useEffect(() => {
    const dismiss = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", dismiss);
    return () => document.removeEventListener("mousedown", dismiss);
  }, []);

  const selectedLabel = options.find((option) => option.value === value)?.label;

  return (
    <div ref={root} className="relative space-y-1.5">
      <span className="block text-[11px] font-bold uppercase tracking-wider text-[#4D626E]">
        {label}
      </span>
      <button
        type="button"
        role="combobox"
        aria-label={label}
        aria-expanded={open}
        onClick={() => {
          setOpen(!open);
          setQuery("");
        }}
        className="flex h-11 w-full items-center justify-between rounded-xl border border-[#D5E0E6] bg-white px-3.5 text-left text-xs font-semibold text-[#17242D] shadow-xs transition-all hover:border-[#0277A8] focus:border-[#0277A8] focus:ring-2 focus:ring-[#0277A8]/15"
      >
        <span className={selectedLabel ? "text-[#17242D]" : "text-[#667985]"}>
          {selectedLabel || "Chọn từ danh sách"}
        </span>
        <svg
          className={`h-4 w-4 shrink-0 text-[#667985] transition-transform ${
            open ? "rotate-180 text-[#0277A8]" : ""
          }`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {open && (
        <div className="absolute z-30 mt-1 w-full space-y-1.5 rounded-xl border border-[#D5E0E6] bg-white p-2 shadow-lg">
          <input
            autoFocus
            aria-label={`Tìm ${label.toLowerCase()}`}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            className="h-9 w-full rounded-lg border border-[#D5E0E6] bg-[#F5F8FA] px-3 text-xs text-[#17242D] outline-none placeholder:text-[#667985] focus:border-[#0277A8] focus:bg-white"
            placeholder="Gõ để tìm trong danh sách..."
          />
          <div role="listbox" aria-label={label} className="max-h-60 overflow-auto">
            {options
              .filter((option) =>
                option.label.toLocaleLowerCase("vi").includes(query.toLocaleLowerCase("vi"))
              )
              .map((option) => (
                <button
                  key={option.value}
                  type="button"
                  role="option"
                  aria-selected={option.value === value}
                  onClick={() => {
                    onChange(option.value);
                    setOpen(false);
                  }}
                  className={`block w-full rounded-lg px-3 py-2 text-left text-xs transition-colors ${
                    option.value === value
                      ? "bg-[#DDF3FC] font-bold text-[#00577E]"
                      : "text-[#17242D] hover:bg-[#F5F8FA]"
                  }`}
                >
                  {option.label}
                </button>
              ))}
            {options.length === 0 && (
              <p className="p-2 text-xs text-[#667985]">Chưa có dữ liệu đủ điều kiện.</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
