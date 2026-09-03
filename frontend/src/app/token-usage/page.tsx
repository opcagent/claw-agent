"use client";

/**
 * Token 使用统计页面
 * 
 * 功能:
 * 1. 本月 Token 使用汇总卡片
 * 2. 最近 6 个月趋势图表
 * 3. Token 使用流水列表 (最近 50 条)
 * 4. 管理员视图: 租户用户排行
 */

import { useEffect, useState } from "react";
import AppShell from "@/components/app-shell";
import { api } from "@/lib/api";
import { useAuthStore } from "@/store/auth";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from "recharts";
import { Loader2, TrendingUp, MessageSquare, Calendar, Clock, Database, Users } from "lucide-react";
import type { TokenUsageLog, TokenUsageSummary } from "@/lib/types";

export default function TokenUsagePage() {
  const [loading, setLoading] = useState(true);
  const [currentMonth, setCurrentMonth] = useState<TokenUsageSummary | null>(null);
  const [recentMonths, setRecentMonths] = useState<TokenUsageSummary[]>([]);
  const [logs, setLogs] = useState<TokenUsageLog[]>([]);
  const [tenantUsers, setTenantUsers] = useState<TokenUsageSummary[]>([]);
  const isAdmin = useAuthStore((s) => s.isAdmin)();
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear());
  const [selectedMonth, setSelectedMonth] = useState(new Date().getMonth() + 1);

  useEffect(() => {
    loadTokenUsageData();
  }, []);

  useEffect(() => {
    if (isAdmin) {
      loadTenantUsersSummary();
    }
  }, [isAdmin, selectedYear, selectedMonth]);

  const loadTokenUsageData = async () => {
    try {
      setLoading(true);

      // 并行加载三个接口
      const [currentRes, recentRes, logsRes] = await Promise.all([
        api.get<TokenUsageSummary>("/api/tokenUsage/currentMonth"),
        api.get<TokenUsageSummary[]>("/api/tokenUsage/recentMonths?months=6"),
        api.get<TokenUsageLog[]>("/api/tokenUsage/logs?limit=50"),
      ]);

      setCurrentMonth(currentRes.data || null);
      setRecentMonths(recentRes.data || []);
      setLogs(logsRes.data || []);
    } catch (error) {
      console.error("加载 Token 使用数据失败:", error);
      // 显示友好的错误提示
      if (error instanceof Error) {
        console.error("错误详情:", error.message);
      }
    } finally {
      setLoading(false);
    }
  };

  const loadTenantUsersSummary = async () => {
    try {
      const res = await api.get<TokenUsageSummary[]>(
        `/api/tokenUsage/admin/tenantUsers?year=${selectedYear}&month=${selectedMonth}`
      );
      setTenantUsers(res.data || []);
    } catch (error) {
      console.error("加载租户用户排行失败:", error);
    }
  };

  // 格式化数字
  const formatNumber = (num: number) => {
    return num.toLocaleString("zh-CN");
  };

  // 格式化时间
  const formatTime = (isoString?: string) => {
    if (!isoString) return "-";
    try {
      const date = new Date(isoString);
      return date.toLocaleString("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return "-";
    }
  };

  // 准备趋势图数据
  const trendChartData = recentMonths
    .slice()
    .reverse()
    .map((item) => ({
      month: `${item.periodStart?.substring(5, 7)}月`,
      totalTokens: item.totalTokens || 0,
      requestCount: item.requestCount || 0,
    }));

  // 准备提供商分布数据 (从流水记录统计)
  const providerDistribution = logs.reduce((acc, log) => {
    const provider = log.provider || "unknown";
    acc[provider] = (acc[provider] || 0) + (log.totalTokens || 0);
    return acc;
  }, {} as Record<string, number>);

  const pieChartData = Object.entries(providerDistribution).map(([name, value]) => ({
    name: name.toUpperCase(),
    value,
  }));

  const COLORS = ["#0088FE", "#00C49F", "#FFBB28", "#FF8042", "#8884D8"];

  if (loading) {
    return (
      <AppShell>
        <div className="flex items-center justify-center h-96">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="container mx-auto p-6 space-y-6">
      {/* 页面标题 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-800">Token 使用统计</h1>
          <p className="text-muted-foreground mt-1">查看您的模型调用消耗和趋势分析</p>
        </div>
        <Button onClick={loadTokenUsageData} variant="outline">
          <TrendingUp className="mr-2 h-4 w-4" />
          刷新数据
        </Button>
      </div>

      {/* 本月汇总卡片 */}
      {currentMonth && (
        <div className="grid gap-4 md:grid-cols-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">总 Token 数</CardTitle>
              <Database className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-semibold text-slate-800">{formatNumber(currentMonth.totalTokens || 0)}</div>
              <p className="text-xs text-muted-foreground">
                输入: {formatNumber(currentMonth.totalPromptTokens || 0)} | 
                输出: {formatNumber(currentMonth.totalCompletionTokens || 0)}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">请求次数</CardTitle>
              <MessageSquare className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-semibold text-slate-800">{formatNumber(currentMonth.requestCount || 0)}</div>
              <p className="text-xs text-muted-foreground">本月累计调用</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">统计周期</CardTitle>
              <Calendar className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-semibold text-slate-800">
                {currentMonth.periodStart?.substring(5, 10)} ~ {currentMonth.periodEnd?.substring(5, 10)}
              </div>
              <p className="text-xs text-muted-foreground">月度统计</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">最后更新</CardTitle>
              <Clock className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-semibold text-slate-800">
                {currentMonth.lastUpdateTime ? formatTime(currentMonth.lastUpdateTime).split(" ")[1] : "-"}
              </div>
              <p className="text-xs text-muted-foreground">
                {currentMonth.lastUpdateTime ? formatTime(currentMonth.lastUpdateTime).split(" ")[0] : "-"}
              </p>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Tab 切换 */}
      <Tabs defaultValue="trend" className="space-y-4">
        <TabsList>
          <TabsTrigger value="trend">📊 使用趋势</TabsTrigger>
          <TabsTrigger value="logs">📝 使用流水</TabsTrigger>
          {isAdmin && <TabsTrigger value="admin">👥 租户排行</TabsTrigger>}
        </TabsList>

        {/* 趋势分析 */}
        <TabsContent value="trend" className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            {/* 月度趋势图 */}
            <Card>
              <CardHeader>
                <CardTitle>近 6 个月 Token 使用趋势</CardTitle>
                <CardDescription>每月总 Token 消耗量</CardDescription>
              </CardHeader>
              <CardContent>
                {trendChartData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={300}>
                    <BarChart data={trendChartData}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="month" />
                      <YAxis />
                      <Tooltip formatter={(value) => formatNumber(value as number)} />
                      <Bar dataKey="totalTokens" fill="#0088FE" name="Token 数" />
                    </BarChart>
                  </ResponsiveContainer>
                ) : (
                  <div className="flex items-center justify-center h-[300px] text-muted-foreground">
                    暂无数据
                  </div>
                )}
              </CardContent>
            </Card>

            {/* 提供商分布 */}
            <Card>
              <CardHeader>
                <CardTitle>模型提供商分布</CardTitle>
                <CardDescription>各提供商 Token 占比</CardDescription>
              </CardHeader>
              <CardContent>
                {pieChartData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={300}>
                    <PieChart>
                      <Pie
                        data={pieChartData}
                        cx="50%"
                        cy="50%"
                        labelLine={false}
                        label={({ name, percent }) => `${name} ${percent ? (percent * 100).toFixed(0) : 0}%`}
                        outerRadius={100}
                        fill="#8884d8"
                        dataKey="value"
                      >
                        {pieChartData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(value) => formatNumber(value as number)} />
                    </PieChart>
                  </ResponsiveContainer>
                ) : (
                  <div className="flex items-center justify-center h-[300px] text-muted-foreground">
                    暂无数据
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        {/* 使用流水 */}
        <TabsContent value="logs">
          <Card>
            <CardHeader>
              <CardTitle>最近 50 条使用记录</CardTitle>
              <CardDescription>详细的模型调用流水</CardDescription>
            </CardHeader>
            <CardContent>
              <ScrollArea className="h-[500px]">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>时间</TableHead>
                      <TableHead>会话 ID</TableHead>
                      <TableHead>提供商</TableHead>
                      <TableHead>模型</TableHead>
                      <TableHead>输入</TableHead>
                      <TableHead>输出</TableHead>
                      <TableHead>总计</TableHead>
                      <TableHead>工具</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {logs.length > 0 ? (
                      logs.map((log) => (
                        <TableRow key={log.id}>
                          <TableCell className="font-mono text-xs">
                            {formatTime(log.usageTime)}
                          </TableCell>
                          <TableCell className="font-mono text-xs truncate max-w-[100px]">
                            {log.sessionId || "-"}
                          </TableCell>
                          <TableCell>
                            <Badge variant="outline">{log.provider?.toUpperCase()}</Badge>
                          </TableCell>
                          <TableCell className="text-sm">{log.modelName || "-"}</TableCell>
                          <TableCell className="text-right font-mono">
                            {formatNumber(log.promptTokens || 0)}
                          </TableCell>
                          <TableCell className="text-right font-mono">
                            {formatNumber(log.completionTokens || 0)}
                          </TableCell>
                          <TableCell className="text-right font-mono font-semibold">
                            {formatNumber(log.totalTokens || 0)}
                          </TableCell>
                          <TableCell>
                            {log.toolName ? (
                              <Badge variant="secondary">{log.toolName}</Badge>
                            ) : (
                              "-"
                            )}
                          </TableCell>
                        </TableRow>
                      ))
                    ) : (
                      <TableRow>
                        <TableCell colSpan={8} className="text-center text-muted-foreground py-8">
                          暂无使用记录
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </ScrollArea>
            </CardContent>
          </Card>
        </TabsContent>

        {/* 管理员视图: 租户用户排行 */}
        {isAdmin && (
          <TabsContent value="admin">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between">
                <div>
                  <CardTitle>租户用户 Token 使用排行</CardTitle>
                  <CardDescription>{selectedYear} 年 {selectedMonth} 月</CardDescription>
                </div>
                <div className="flex gap-2">
                  <Select
                    value={selectedYear.toString()}
                    onValueChange={(v) => { if (v != null) setSelectedYear(Number(v)); }}
                  >
                    <SelectTrigger className="w-[100px]">
                      <span className="truncate">{selectedYear} 年</span>
                    </SelectTrigger>
                    <SelectContent>
                      {[2024, 2025, 2026].map((year) => (
                        <SelectItem key={year} value={year.toString()}>
                          {year} 年
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Select
                    value={selectedMonth.toString()}
                    onValueChange={(v) => { if (v != null) setSelectedMonth(Number(v)); }}
                  >
                    <SelectTrigger className="w-[100px]">
                      <span className="truncate">{selectedMonth} 月</span>
                    </SelectTrigger>
                    <SelectContent>
                      {Array.from({ length: 12 }, (_, i) => i + 1).map((month) => (
                        <SelectItem key={month} value={month.toString()}>
                          {month} 月
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>排名</TableHead>
                      <TableHead>用户名</TableHead>
                      <TableHead className="text-right">总 Token</TableHead>
                      <TableHead className="text-right">请求次数</TableHead>
                      <TableHead className="text-right">占比</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {tenantUsers.length > 0 ? (
                      tenantUsers.map((user, index) => {
                        const totalTokens = tenantUsers.reduce(
                          (sum, u) => sum + (u.totalTokens || 0),
                          0
                        );
                        const percentage = totalTokens > 0
                          ? ((user.totalTokens || 0) / totalTokens * 100).toFixed(2)
                          : "0.00";

                        return (
                          <TableRow key={user.userId}>
                            <TableCell>
                              {index === 0 && "🥇"}
                              {index === 1 && "🥈"}
                              {index === 2 && "🥉"}
                              {index > 2 && `#${index + 1}`}
                            </TableCell>
                            <TableCell className="font-medium">{user.username}</TableCell>
                            <TableCell className="text-right font-mono">
                              {formatNumber(user.totalTokens || 0)}
                            </TableCell>
                            <TableCell className="text-right font-mono">
                              {formatNumber(user.requestCount || 0)}
                            </TableCell>
                            <TableCell className="text-right">
                              <Badge variant={index < 3 ? "default" : "outline"}>
                                {percentage}%
                              </Badge>
                            </TableCell>
                          </TableRow>
                        );
                      })
                    ) : (
                      <TableRow>
                        <TableCell colSpan={5} className="text-center text-muted-foreground py-8">
                          暂无数据
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </TabsContent>
        )}
      </Tabs>
    </div>
  </AppShell>
);
}
