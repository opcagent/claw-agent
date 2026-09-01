/**
 * HTTP / SSE 请求封装（浏览器端）。
 * - 统一携带 JWT（Authorization: Bearer），401 清 token 并跳登录页；
 * - SSE 用 fetch + ReadableStream 解析（EventSource 无法携带自定义请求头）；
 * - 非流式接口统一解析 Result<T> 结构，code!=200 抛错。
 */
import type { ChatEvent, Result, UploadResponse } from "./types";

export const TOKEN_KEY = "claw_token";

/**
 * 后端直连地址（仅 SSE 流式请求使用）。
 * Next.js dev proxy（rewrites）内部 http-proxy 会缓冲 chunked/SSE 响应，
 * 导致流式事件非实时推送（等流结束才一次性到达）；
 * SSE 必须绕过代理直连后端。非流式请求走相对路径（经代理）无此问题。
 * 开发期在 .env.local 中配置：NEXT_PUBLIC_BACKEND_URL=http://localhost:8080
 */
const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL || "";

/**
 * 携带后端业务错误码的异常（对应 ResultCode 枚举）。
 * 页面可按 code 做针对性交互（如 4002 编码重复时高亮编码输入框）。
 */
export class ApiError extends Error {
  /** 后端业务错误码（网络层错误时为 0） */
  code: number;

  constructor(message: string, code = 0) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

/** 判断错误是否携带指定业务错误码（非 ApiError 一律 false） */
export function isErrorCode(e: unknown, code: number): boolean {
  return e instanceof ApiError && e.code === code;
}

/** 读取本地 token（仅浏览器环境） */
export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

/** 组装请求头（带 token；JSON 请求默认 Content-Type） */
function buildHeaders(withAuth: boolean): HeadersInit {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (withAuth) {
    const token = getToken();
    if (token) headers["Authorization"] = "Bearer " + token;
  }
  return headers;
}

/** 401 统一处理：清 token 跳登录页（已在登录页则不重复跳转，避免刷新循环） */
function handleUnauthorized(): never {
  localStorage.removeItem(TOKEN_KEY);
  if (typeof window !== "undefined" && window.location.pathname !== "/login") {
    window.location.href = "/login";
  }
  throw new Error("登录已过期");
}

/** 通用请求：解析 Result 结构，非 200 或 code!=200 抛错 */
async function request<T>(
  method: string,
  url: string,
  body?: unknown,
  withAuth = true
): Promise<Result<T>> {
  const resp = await fetch(url, {
    method,
    headers: buildHeaders(withAuth),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (resp.status === 401) handleUnauthorized();
  const json = (await resp.json().catch(() => {
    throw new ApiError(`响应解析失败（HTTP ${resp.status}）`);
  })) as Result<T>;
  if (json.code !== undefined && json.code !== 200) {
    throw new ApiError(json.message || "请求失败", json.code);
  }
  return json;
}

/**
 * SSE 流式请求：逐事件回调。
 * @param url     接口地址（POST）
 * @param body    请求体对象
 * @param onEvent 回调 (event)，event.type 区分事件种类
 */
export async function stream(
  url: string,
  body: unknown,
  onEvent: (event: ChatEvent) => void
): Promise<void> {
  // SSE 直连后端，绕过 Next.js rewrites 代理缓冲（http-proxy 默认缓冲 chunked 响应）
  const directUrl = BACKEND_URL ? `${BACKEND_URL}${url}` : url;
  const resp = await fetch(directUrl, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(body),
  });
  if (resp.status === 401) handleUnauthorized();
  // 后端准备段异常会经全局异常处理器返回 JSON Result（非 SSE），
  // 此处必须识别并抛出业务消息，否则前端按 SSE 解析将静默吞掉错误（界面无提示）
  const contentType = resp.headers.get("content-type") || "";
  if (!resp.ok || !contentType.includes("text/event-stream")) {
    let message = `流式请求失败（HTTP ${resp.status}）`;
    let code = 0;
    try {
      const json = (await resp.json()) as Result<unknown>;
      if (json && json.message) {
        message = json.message;
        code = json.code ?? 0;
      }
    } catch {
      // 非 JSON 响应（如网关错误页），保留默认提示
    }
    throw new ApiError(message, code);
  }
  if (!resp.body) {
    throw new Error("流式请求失败：响应体为空");
  }
  const reader = resp.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  // SSE 事件块以空行分隔；逐块读取防半包
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let idx: number;
    while ((idx = buffer.indexOf("\n\n")) !== -1) {
      const block = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      parseBlock(block, onEvent);
    }
  }
  if (buffer.trim()) parseBlock(buffer, onEvent);
}

/** 解析单个 SSE 事件块（event: xxx / data: {...}） */
function parseBlock(block: string, onEvent: (event: ChatEvent) => void): void {
  let type = "message";
  const dataLines: string[] = [];
  block.split("\n").forEach((line) => {
    if (line.startsWith("event:")) {
      type = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trim());
    }
  });
  if (!dataLines.length) return;
  try {
    const data = JSON.parse(dataLines.join("\n")) as ChatEvent;
    // event: 行为准，data 内无 type 时回填
    if (!data.type) data.type = type;
    onEvent(data);
  } catch {
    // 非法 JSON：以原始文本作为 error 事件透传
    onEvent({ type: "error", message: dataLines.join("\n") });
  }
}

/** 文件上传（multipart/form-data） */
export async function upload(file: File): Promise<UploadResponse> {
  const form = new FormData();
  form.append("file", file);
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers["Authorization"] = "Bearer " + token;
  const resp = await fetch("/api/upload", { method: "POST", headers, body: form });
  if (resp.status === 401) handleUnauthorized();
  const json = (await resp.json()) as Result<UploadResponse>;
  if (json.code !== 200) throw new ApiError(json.message || "上传失败", json.code);
  return json.data;
}

export const api = {
  get: <T>(url: string) => request<T>("GET", url),
  post: <T>(url: string, body?: unknown, withAuth = true) =>
    request<T>("POST", url, body, withAuth),
  put: <T>(url: string, body?: unknown) => request<T>("PUT", url, body),
  del: <T>(url: string) => request<T>("DELETE", url),
  stream,
  upload,
};
