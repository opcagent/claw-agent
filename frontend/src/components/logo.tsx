"use client";

/**
 * Opc Agent 品牌 Logo（SVG 矢量，任意尺寸清晰）。
 * 设计语义：外环「O」= 开放协同（Open Collaboration），
 * 内部三道渐变弧爪环抱火花 = Agent 抓取任务、汇聚智能。
 */
export function LogoMark({ className = "h-8 w-8" }: { className?: string }) {
  return (
    <svg viewBox="0 0 48 48" fill="none" className={className} aria-hidden="true">
      <defs>
        <linearGradient id="opc-ring" x1="6" y1="6" x2="42" y2="42" gradientUnits="userSpaceOnUse">
          <stop stopColor="#6366f1" />
          <stop offset="1" stopColor="#8b5cf6" />
        </linearGradient>
        <linearGradient id="opc-claw" x1="14" y1="14" x2="34" y2="34" gradientUnits="userSpaceOnUse">
          <stop stopColor="#818cf8" />
          <stop offset="1" stopColor="#a78bfa" />
        </linearGradient>
      </defs>
      {/* 外环 O */}
      <circle cx="24" cy="24" r="20" stroke="url(#opc-ring)" strokeWidth="3.5" />
      {/* 三道环抱弧爪（顺时针错位，形成抓取姿态） */}
      <path d="M24 11.5 A12.5 12.5 0 0 1 35.4 18.9" stroke="url(#opc-claw)" strokeWidth="3.5" strokeLinecap="round" />
      <path d="M33.6 31.8 A12.5 12.5 0 0 1 15.2 33.5" stroke="url(#opc-claw)" strokeWidth="3.5" strokeLinecap="round" />
      <path d="M12.7 20.5 A12.5 12.5 0 0 1 19.6 12.6" stroke="url(#opc-claw)" strokeWidth="3.5" strokeLinecap="round" />
      {/* 中心火花 */}
      <circle cx="24" cy="24" r="4" fill="url(#opc-ring)" />
    </svg>
  );
}

/** 品牌组合：Logo + 文字（用于顶栏 / 登录页头部）
 *  @param name 品牌名，默认读取 yml claw.version.name（"Opc Agent"） */
export function LogoBrand({
  name = "Opc Agent",
  className = "",
  textClassName = "text-base font-semibold text-slate-800",
}: {
  name?: string;
  className?: string;
  textClassName?: string;
}) {
  return (
    <span className={"flex items-center gap-2 " + className}>
      <LogoMark className="h-8 w-8" />
      <span className={textClassName}>{name}</span>
    </span>
  );
}
