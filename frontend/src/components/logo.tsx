"use client";

/**
 * 品牌 Logo —— 「爪痕 ClawMark」。
 * 设计语义：
 *   三道粗犷爪痕（左上→右下扇形展开）= Claw，代表 Agent 抓取任务、主动出击；
 *   中心菱形火花 = Agent / AI 智能核心，爪痕汇聚之处即智能迸发；
 *   两者合一 = 「智能体以爪痕之力抓取并汇聚智能」。
 * 单渐变 + 4 个图形元素，bold stroke 确保 16px favicon 到 48px 顶栏均清晰可辨。
 * 品牌色 indigo→violet 与全站渐变体系一致。
 */
export function LogoMark({ className = "h-8 w-8" }: { className?: string }) {
  return (
    <svg viewBox="0 0 48 48" fill="none" className={className} aria-hidden="true">
      <defs>
        <linearGradient id="claw-g" x1="4" y1="4" x2="44" y2="44" gradientUnits="userSpaceOnUse">
          <stop stopColor="#6366f1" />
          <stop offset="1" stopColor="#8b5cf6" />
        </linearGradient>
      </defs>
      {/* 三道爪痕：左上→右下，扇形展开，模拟抓取姿态 */}
      <path d="M10 6 L38 34" stroke="url(#claw-g)" strokeWidth="5.5" strokeLinecap="round" />
      <path d="M6 18 L26 38" stroke="url(#claw-g)" strokeWidth="5.5" strokeLinecap="round" />
      <path d="M18 4 L44 30" stroke="url(#claw-g)" strokeWidth="5.5" strokeLinecap="round" />
      {/* 中心火花：爪痕汇聚处的 AI 智能核心 */}
      <path d="M24 18 L27 24 L24 30 L21 24Z" fill="url(#claw-g)" />
    </svg>
  );
}

/** 品牌组合：Logo + 文字（用于顶栏 / 登录页头部）
 *  @param name 品牌名，默认读取 yml claw.version.name */
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
