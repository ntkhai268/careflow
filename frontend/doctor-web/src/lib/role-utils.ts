export function getDefaultRouteForRole(role?: string): string {
  const normalized = role?.toUpperCase() || "";
  if (normalized.includes("ADMIN")) {
    return "/admin";
  }
  if (normalized.includes("LAB")) {
    return "/lab/queue";
  }
  if (normalized.includes("STAFF")) {
    return "/staff/checkin";
  }
  return "/dashboard/general";
}
