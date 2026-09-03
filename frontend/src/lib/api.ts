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

/** 401 统一处理：清 token 跳登录页（已在登录页则不重复跳，避免刷新循环） */
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
 * @param signal  可选 AbortSignal，用于组件卸载时取消请求
 */
/**
 * SSE 流式请求：逐块读取并解析事件，调用 onEvent 回调。
 * <p>
 * 收到 end 事件时主动关闭连接，避免后端 Flux 未完成导致 reader 永久挂起。
 */
export async function stream(
  url: string,
  body: unknown,
  onEvent: (event: ChatEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  // SSE 直连后端，绕过 Next.js rewrites 代理缓冲（http-proxy 默认缓冲 chunked 响应）
  const directUrl = BACKEND_URL ? `${BACKEND_URL}${url}` : url;
  const resp = await fetch(directUrl, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(body),
    signal,
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
  /**
   * 流超时保护：
   * - 空闲超时 600s：后端 keepalive 每 15s 发心跳，正常情况下空闲超时不会触发。
   *   保留此超时作为安全网，防止后端完全卡死时连接永不断开。
   *   流水线场景（25 轮迭代 + 多工具调用）可能持续 10-15 分钟，600s 留足余量。
   * - 硬超时 1200s（20 分钟）→ 无论任何情况，强制断开（兆底）。
   *   此前 600s 对复杂流水线偏紧，提升至 1200s 覆盖研究报告/故障排查等长链路场景。
   */
  const IDLE_TIMEOUT = 600_000;
  const MAX_STREAM_DURATION = 1200_000;
  let idleTimer: ReturnType<typeof setTimeout> | null = null;
  let hardTimer: ReturnType<typeof setTimeout> | null = null;

  function resetIdleTimer() {
    if (idleTimer) clearTimeout(idleTimer);
    idleTimer = setTimeout(() => {
      reader.cancel().catch(() => {});
    }, IDLE_TIMEOUT);
  }
  resetIdleTimer();
  hardTimer = setTimeout(() => {
    reader.cancel().catch(() => {});
  }, MAX_STREAM_DURATION);

  // 外部 abort（用户点「取消」/切换会话）时显式 cancel reader，
  // 确保 pending 的 reader.read() 立即返回 {done:true}（fetch abort 不一定能立即中断 reader）
  const onAbort = () => { reader.cancel().catch(() => {}); };
  signal?.addEventListener("abort", onAbort, { once: true });

  try {
    // SSE 事件块以空行分隔；逐块读取防半包
    for (;;) {
      resetIdleTimer();
      // abort 信号检查：用户点「取消」或切换会话时立即退出，不等 reader 自然结束
      if (signal?.aborted) break;
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let idx: number;
      while ((idx = buffer.indexOf("\n\n")) !== -1) {
        const block = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 2);
        const event = parseBlock(block);
        if (event) {
          onEvent(event);
        }
        // 收到 end 事件：立即关闭连接，不等后端 Flux 完成
        if (event?.type === "end") {
          await reader.cancel().catch(() => {});
        }
      }
    }
  } finally {
    signal?.removeEventListener("abort", onAbort);
    if (idleTimer) clearTimeout(idleTimer);
    if (hardTimer) clearTimeout(hardTimer);
    reader.releaseLock();
  }
  if (buffer.trim()) {
    const event = parseBlock(buffer);
    if (event) onEvent(event);
  }
}

/** 解析单个 SSE 事件块（event: xxx / data: {...}） */
function parseBlock(block: string): ChatEvent | null {
  let type = "message";
  const dataLines: string[] = [];
  block.split("\n").forEach((line) => {
    if (line.startsWith("event:")) {
      type = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trim());
    }
  });
  if (!dataLines.length) return null;
  try {
    const data = JSON.parse(dataLines.join("\n")) as ChatEvent;
    // event: 行为准，data 内无 type 时回填
    if (!data.type) data.type = type;
    return data;
  } catch {
    // 非法 JSON：以 error 事件透传
    return { type: "error", message: dataLines.join("\n") };
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
  const json = (await resp.json().catch(() => {
    throw new ApiError(`上传响应解析失败（HTTP ${resp.status}）`);
  })) as Result<UploadResponse>;
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

// 模型相关接口类型定义
export interface ModelCard {
  modelName: string;
  displayName: string;
  contextSize: number;
}

export interface ListModelResponse {
  models: ModelCard[];
  provider: string;
}

// 模型相关API
export const modelApi = {
  /**
   * 获取指定提供商支持的模型列表
   * @param provider 模型提供商
   */
  listModels: (provider: string): Promise<Result<ListModelResponse>> => {
    return api.get<ListModelResponse>(`/api/models/list?provider=${encodeURIComponent(provider)}`);
  },
};

