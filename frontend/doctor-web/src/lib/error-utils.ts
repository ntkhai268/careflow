export function getErrorMessage(error: unknown, fallback = "Đã xảy ra lỗi."): string {
  if (error instanceof Error) return error.message;

  if (typeof error === "object" && error !== null && "message" in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === "string") return message;
  }

  return fallback;
}
