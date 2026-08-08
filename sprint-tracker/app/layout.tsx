import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "BICentral — Sprint Tracker",
  description: "Quadro de sprints do estágio, com status, bloqueio e atraso ao vivo.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
