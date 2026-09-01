"use client";

import React, { Component, ErrorInfo, ReactNode } from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

/**
 * 错误边界组件 - 捕获子组件树中的 JavaScript 错误
 * 
 * 使用方式:
 * ```tsx
 * <ErrorBoundary fallback={<CustomFallback />}>
 *   <ChatView />
 * </ErrorBoundary>
 * ```
 */
export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("ErrorBoundary caught an error:", error, errorInfo);
    
    // 调用自定义错误处理回调
    this.props.onError?.(error, errorInfo);
    
    // 上报到监控系统 (可选)
    // reportErrorToSentry(error, errorInfo);
  }

  private handleRetry = () => {
    this.setState({ hasError: false, error: null });
  };

  private handleReload = () => {
    window.location.reload();
  };

  public render() {
    if (this.state.hasError) {
      // 如果提供了自定义 fallback,优先使用
      if (this.props.fallback) {
        return this.props.fallback;
      }

      // 默认错误 UI
      return (
        <div className="min-h-[400px] flex items-center justify-center p-8">
          <div className="max-w-md w-full space-y-4 text-center">
            {/* 错误图标 */}
            <div className="flex justify-center">
              <div className="h-16 w-16 rounded-full bg-red-100 flex items-center justify-center">
                <AlertTriangle className="h-8 w-8 text-red-600" />
              </div>
            </div>

            {/* 错误标题 */}
            <h2 className="text-xl font-semibold text-foreground">
              组件渲染出错
            </h2>

            {/* 错误详情 */}
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-left">
              <p className="text-sm text-red-800 font-mono break-all">
                {this.state.error?.message || "未知错误"}
              </p>
            </div>

            {/* 操作按钮 */}
            <div className="flex gap-3 justify-center">
              <Button
                variant="outline"
                onClick={this.handleRetry}
                className="gap-2"
              >
                <RefreshCw className="h-4 w-4" />
                重试
              </Button>
              <Button
                onClick={this.handleReload}
                className="gap-2"
              >
                <RefreshCw className="h-4 w-4" />
                刷新页面
              </Button>
            </div>

            {/* 提示文本 */}
            <p className="text-xs text-muted-foreground">
              如果问题持续存在,请检查浏览器控制台错误或联系技术支持
            </p>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

/**
 * 异步错误边界 - 用于捕获 async/await 中的错误
 * 
 * 使用方式:
 * ```tsx
 * const [data, setData] = useState(null);
 * 
 * useAsyncErrorBoundary(
 *   async () => {
 *     const result = await fetchData();
 *     setData(result);
 *   },
 *   (error) => {
 *     console.error("加载失败:", error);
 *   }
 * );
 * ```
 */
export function useAsyncErrorBoundary(
  asyncFn: () => Promise<void>,
  onError?: (error: Error) => void
) {
  const [error, setError] = React.useState<Error | null>(null);
  const [loading, setLoading] = React.useState(true);

  React.useEffect(() => {
    let mounted = true;

    const execute = async () => {
      try {
        setLoading(true);
        await asyncFn();
        if (mounted) {
          setLoading(false);
        }
      } catch (err) {
        if (mounted) {
          const error = err instanceof Error ? err : new Error(String(err));
          setError(error);
          setLoading(false);
          onError?.(error);
        }
      }
    };

    execute();

    return () => {
      mounted = false;
    };
  }, [asyncFn, onError]);

  return { error, loading };
}
