import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "观市 · 股票学习实验室",
  description: "以可视化课程、交互实验、带成本的 A 股模拟交易和有来源的市场解读，理解每一条价格曲线。",
  other: {
    "codex-preview": "development",
  },
  icons: {
    icon: "/tools/market-atlas/favicon.svg",
    shortcut: "/tools/market-atlas/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body className="antialiased">{children}</body>
    </html>
  );
}
