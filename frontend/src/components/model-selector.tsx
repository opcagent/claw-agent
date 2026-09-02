import { useState, useEffect, useCallback, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/skeleton";
import { api } from "@/lib/api";
import { 
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select";

import type { DictData } from "@/lib/types";

interface ModelCard {
  modelName: string;
  displayName: string;
  contextSize: number;
}

interface ListModelResponse {
  models: ModelCard[];
  provider: string;
}

interface ModelSelectorProps {
  provider: string;
  onModelChange: (model: ModelCard) => void;
  defaultValue?: string;
}

export function ModelSelector({ provider, onModelChange, defaultValue }: ModelSelectorProps) {
  const [models, setModels] = useState<ModelCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedModel, setSelectedModel] = useState<string>(defaultValue || "");
  const onModelChangeRef = useRef(onModelChange);
  onModelChangeRef.current = onModelChange;

  // provider 变化时重新加载模型列表，内联异步逻辑避免闭包陈旧引用
  useEffect(() => {
    if (!provider) return;
    let cancelled = false;
    setLoading(true);
    api.get<ListModelResponse>(`/api/models/list?provider=${encodeURIComponent(provider)}`)
      .then((response) => {
        if (cancelled) return;
        const modelsList = response.data?.models || [];
        setModels(modelsList);
        // 列表非空且当前无选中模型时，自动选中第一个
        if (modelsList.length > 0 && !selectedModel) {
          setSelectedModel(modelsList[0].modelName);
          onModelChangeRef.current(modelsList[0]);
        } else if (modelsList.length === 0) {
          setSelectedModel("");
        }
      })
      .catch(() => {
        if (!cancelled) setModels([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [provider]);

  const handleModelChange = useCallback((value: string | null) => {
    if (!value) return;
    setSelectedModel(value);
    const model = models.find(m => m.modelName === value);
    if (model) {
      onModelChangeRef.current(model);
    }
  }, [models]);

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-10 w-full" />
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          {[...Array(6)].map((_, i) => (
            <Skeleton key={i} className="h-24 w-full" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <Select value={selectedModel} onValueChange={handleModelChange}>
        <SelectTrigger className="w-full">
          <SelectValue placeholder={models.length > 0 ? "选择模型" : "暂无可用模型"} />
        </SelectTrigger>
        <SelectContent>
          {models.length > 0 ? (
            models.map((model) => (
              <SelectItem key={model.modelName} value={model.modelName}>
                {model.displayName} ({model.contextSize.toLocaleString()} tokens)
              </SelectItem>
            ))
          ) : (
            <SelectItem value="" disabled>
              暂无可用模型
            </SelectItem>
          )}
        </SelectContent>
      </Select>
    </div>
  );
}

interface ModelProviderSelectorProps {
  onProviderChange: (provider: string) => void;
  onModelChange: (model: ModelCard) => void;
  defaultProvider?: string;
  defaultModel?: string;
}

export function ModelProviderSelector({ 
  onProviderChange, 
  onModelChange, 
  defaultProvider, 
  defaultModel 
}: ModelProviderSelectorProps) {
  const [selectedProvider, setSelectedProvider] = useState(defaultProvider || "dashscope");
  const [providerOptions, setProviderOptions] = useState<{ value: string; label: string }[]>([]);

  // 从字典获取提供商列表，失败时回退硬编码兜底
  useEffect(() => {
    api.get<DictData[]>("/api/dict/data/agent_model_provider")
      .then((res) => {
        const list = (res.data || []).map((d) => ({ value: d.dictValue, label: d.dictLabel }));
        if (list.length > 0) setProviderOptions(list);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    onProviderChange(selectedProvider);
  }, [selectedProvider, onProviderChange]);

  const handleProviderChange = (value: string | null) => {
    if (value) setSelectedProvider(value);
  };

  const handleModelChangeCallback = (model: ModelCard) => {
    onModelChange(model);
  };

  return (
    <div className="space-y-6">
      <div>
        <label className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2 block">
          选择模型提供商
        </label>
        <Select value={selectedProvider} onValueChange={handleProviderChange}>
          <SelectTrigger className="w-full">
            <SelectValue placeholder="选择提供商" />
          </SelectTrigger>
          <SelectContent>
            {(providerOptions.length > 0 ? providerOptions : [
              { value: "dashscope", label: "阿里云通义千问" },
              { value: "openai", label: "OpenAI 及兼容协议" },
              { value: "deepseek", label: "DeepSeek" },
              { value: "ollama", label: "本地 Ollama" },
              { value: "anthropic", label: "Anthropic Claude" },
              { value: "gemini", label: "Google Gemini" },
              { value: "volcengine", label: "火山方舟（豆包）" },
            ]).map((p) => (
              <SelectItem key={p.value} value={p.value}>{p.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      
      <div>
        <label className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2 block">
          选择模型
        </label>
        <ModelSelector 
          provider={selectedProvider} 
          onModelChange={handleModelChangeCallback}
          defaultValue={defaultModel}
        />
      </div>
    </div>
  );
}