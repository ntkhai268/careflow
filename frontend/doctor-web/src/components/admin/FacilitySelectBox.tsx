"use client";

import { useEffect, useMemo, useRef, useState } from "react";

export interface SelectOption {
  value: string;
  label: string;
  subLabel?: string;
}

interface FacilitySelectBoxProps {
  label: string;
  value: string;
  options: SelectOption[];
  onChange: (value: string) => void;
  placeholder?: string;
  searchable?: boolean;
}

export default function FacilitySelectBox({
  label,
  value,
  options,
  onChange,
  placeholder = "-- Chọn giá trị --",
  searchable = true,
}: FacilitySelectBoxProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleMouseDown = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleMouseDown);
    return () => document.removeEventListener("mousedown", handleMouseDown);
  }, []);

  const selectedOption = useMemo(
    () => options.find((opt) => opt.value === value) ?? null,
    [options, value]
  );

  const filteredOptions = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return options;
    return options.filter(
      (opt) =>
        opt.label.toLowerCase().includes(normalized) ||
        opt.value.toLowerCase().includes(normalized) ||
        (opt.subLabel && opt.subLabel.toLowerCase().includes(normalized))
    );
  }, [options, query]);

  return (
    <div ref={containerRef} className="relative text-xs">
      <label className="block font-bold text-[#2B1D30] mb-1">{label}</label>
      <button
        type="button"
        onClick={() => {
          setOpen((prev) => !prev);
          setQuery("");
        }}
        className="w-full p-2.5 border border-card-border rounded-lg bg-card-bg text-left flex items-center justify-between gap-2 focus:outline-none focus:ring-2 focus:ring-purple-500 cursor-pointer"
      >
        <span className={selectedOption ? "text-[#2B1D30] font-medium truncate" : "text-gray-400 truncate"}>
          {selectedOption ? selectedOption.label : placeholder}
        </span>
        <span className="text-[10px] font-bold text-purple-700 shrink-0">{open ? "Đóng" : "Chọn"}</span>
      </button>

      {open && (
        <div className="absolute z-50 mt-1 w-full rounded-xl border border-card-border bg-white shadow-lg overflow-hidden">
          {searchable && options.length > 4 && (
            <div className="p-2 border-b border-card-border bg-purple-50/40">
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Gõ để lọc nhanh..."
                className="w-full px-2.5 py-1.5 rounded-lg border border-purple-200 bg-white text-xs text-[#2B1D30] focus:outline-none focus:border-purple-500"
              />
            </div>
          )}
          <div className="max-h-48 overflow-y-auto divide-y divide-gray-50">
            {filteredOptions.length === 0 ? (
              <div className="px-3 py-2.5 text-gray-400 italic">Không tìm thấy kết quả phù hợp</div>
            ) : (
              filteredOptions.map((opt) => {
                const isSelected = opt.value === value;
                return (
                  <button
                    key={opt.value || "__empty"}
                    type="button"
                    onClick={() => {
                      onChange(opt.value);
                      setOpen(false);
                    }}
                    className={`w-full px-3 py-2 text-left flex items-center justify-between gap-2 transition-colors cursor-pointer ${
                      isSelected
                        ? "bg-purple-100 text-purple-900 font-bold"
                        : "text-[#2B1D30] hover:bg-purple-50"
                    }`}
                  >
                    <span className="truncate">{opt.label}</span>
                    {opt.subLabel && (
                      <span className="font-mono text-[10px] text-purple-700 shrink-0">{opt.subLabel}</span>
                    )}
                  </button>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}
