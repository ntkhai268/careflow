import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
const paths: Record<string, string> = {
  login: "/api/auth/login",
  passkey: "/api/auth/passkeys/login/finish",
  refresh: "/api/auth/refresh",
  logout: "/api/auth/logout",
};

export async function POST(request: NextRequest, context: { params: Promise<{ action: string }> }) {
  const { action } = await context.params;
  if (!Object.hasOwn(paths, action)) return NextResponse.json({ message: "Not found" }, { status: 404 });
  const production = process.env.NODE_ENV === "production";
  const expectedOrigin = process.env.CAREFLOW_WEB_ORIGIN || (production ? "" : request.nextUrl.origin);
  if (!expectedOrigin || request.headers.get("origin") !== expectedOrigin || request.headers.get("sec-fetch-site") === "cross-site") {
    return NextResponse.json({ message: "Yêu cầu không hợp lệ." }, { status: 403 });
  }
  const cookieName = production ? "__Host-careflow_refresh" : "careflow_refresh";
  const cookieOptions = { httpOnly: true, secure: production, sameSite: "strict" as const, path: "/" };
  const proof = request.headers.get("dpop");
  if (!proof || proof.length > 8192 || !request.headers.get("content-type")?.startsWith("application/json")) {
    return NextResponse.json({ message: "Yêu cầu xác thực thiết bị." }, { status: 400 });
  }
  try {
    const text = await request.text();
    if (Buffer.byteLength(text) > 40_000) return NextResponse.json({ message: "Yêu cầu quá lớn." }, { status: 413 });
    let body = JSON.parse(text);
    if (action === "refresh" || action === "logout") {
      const token = request.cookies.get(cookieName)?.value;
      if (!token) return NextResponse.json({ message: "Cần đăng nhập lại." }, { status: 401 });
      body = { refreshToken: token };
    }
    const gateway = process.env.CAREFLOW_GATEWAY_URL || process.env.NEXT_PUBLIC_API_URL;
    if (!gateway || (production && !gateway.startsWith("https://"))) return NextResponse.json({ message: "Dịch vụ chưa sẵn sàng." }, { status: 503 });
    const upstream = await fetch(`${gateway.replace(/\/$/, "")}${paths[action]}`, {
      method: "POST", headers: { "Content-Type": "application/json", DPoP: proof },
      body: JSON.stringify(body), cache: "no-store", redirect: "error", signal: AbortSignal.timeout(20_000),
    });
    const data = await upstream.json();
    const refresh = data.data?.refreshToken;
    const lifetime = Number(data.data?.refreshTokenExpiresInSeconds);
    if (data.data) delete data.data.refreshToken;
    const response = NextResponse.json(data, { status: upstream.status, headers: { "Cache-Control": "no-store" } });
    if (upstream.ok && typeof refresh === "string" && /^[A-Za-z0-9_-]{43}$/.test(refresh)) {
      response.cookies.set(cookieName, refresh, { ...cookieOptions, maxAge: Math.min(2_592_000, Number.isFinite(lifetime) ? Math.max(0, Math.floor(lifetime)) : 0) });
    } else if (action === "logout" || (action === "refresh" && upstream.status === 401)) {
      response.cookies.set(cookieName, "", { ...cookieOptions, maxAge: 0 });
    }
    return response;
  } catch {
    const response = NextResponse.json({ message: "Không thể kết nối dịch vụ xác thực. Vui lòng thử lại." }, { status: 503, headers: { "Cache-Control": "no-store" } });
    if (action === "logout") response.cookies.set(cookieName, "", { ...cookieOptions, maxAge: 0 });
    return response;
  }
}
