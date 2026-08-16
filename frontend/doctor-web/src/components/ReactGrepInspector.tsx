"use client";

import { useEffect } from "react";

export default function ReactGrepInspector() {
  useEffect(() => {
    if (process.env.NODE_ENV === "development") {
      void import("react-grep");
    }
  }, []);

  return null;
}
