"use client";
import { useEffect, useRef, useState } from "react";
export default function HandoverPicker({ label, options, value, onChange }: {
  label: string; options: { value: string; label: string }[]; value: string; onChange: (value: string) => void;
}) {
  const root = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false), [query, setQuery] = useState("");
  useEffect(() => {
    const dismiss = (event: MouseEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", dismiss); return () => document.removeEventListener("mousedown", dismiss);
  }, []);
  return <div ref={root} className="relative space-y-1">
    <span className="block text-sm font-medium">{label}</span>
    <button type="button" role="combobox" aria-label={label} aria-expanded={open} onClick={() => { setOpen(!open); setQuery(""); }} className="w-full border rounded-lg bg-white p-3 text-left">
      {options.find(option => option.value === value)?.label || "Chọn từ danh sách"}
    </button>
    {open && <div className="absolute z-30 w-full bg-white border rounded-xl shadow-lg p-2 space-y-1">
      <input autoFocus aria-label={`Tìm ${label.toLowerCase()}`} value={query} onChange={event => setQuery(event.target.value)} className="w-full border rounded-lg p-2" placeholder="Gõ để tìm trong danh sách" />
      <div role="listbox" aria-label={label} className="max-h-60 overflow-auto">
        {options.filter(option => option.label.toLocaleLowerCase("vi").includes(query.toLocaleLowerCase("vi"))).map(option => <button key={option.value} type="button" role="option" aria-selected={option.value === value} onClick={() => { onChange(option.value); setOpen(false); }} className="block w-full p-2 text-left rounded-lg hover:bg-purple-50">{option.label}</button>)}
        {options.length === 0 && <p className="p-2 text-sm text-gray-500">Chưa có dữ liệu đủ điều kiện.</p>}
      </div>
    </div>}
  </div>;
}
