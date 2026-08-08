"use client";

import { useEffect, useState } from "react";
import styles from "./BoasVindas.module.css";

export default function BoasVindas() {
  const [fase, setFase] = useState<"entrando" | "toque" | "saindo" | "sumiu">("entrando");
  const [pular, setPular] = useState(false);

  useEffect(() => {
    if (sessionStorage.getItem("sprint-tracker-boas-vindas") === "1") {
      setPular(true);
      return;
    }
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setPular(true);
      return;
    }

    const t1 = setTimeout(() => setFase("toque"), 900);
    const t2 = setTimeout(() => setFase("saindo"), 2200);
    const t3 = setTimeout(() => {
      setFase("sumiu");
      sessionStorage.setItem("sprint-tracker-boas-vindas", "1");
    }, 2900);
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      clearTimeout(t3);
    };
  }, []);

  if (pular || fase === "sumiu") return null;

  return (
    <div className={`${styles.overlay} ${fase === "saindo" ? styles.saindo : ""}`}>
      <div className={styles.cena}>
        <div className={`${styles.avatarWrap} ${styles.esquerda} ${fase !== "entrando" ? styles.chegou : ""}`}>
          <AvatarDallyla />
        </div>

        <div className={`${styles.explosao} ${fase === "toque" ? styles.explode : ""}`}>✨</div>

        <div className={`${styles.avatarWrap} ${styles.direita} ${fase !== "entrando" ? styles.chegou : ""}`}>
          <AvatarClaude />
        </div>
      </div>
      <p className={styles.legenda}>Dallyla &amp; Claude — parceria que fez esse tracker</p>
    </div>
  );
}

function AvatarDallyla() {
  return (
    <svg viewBox="0 0 120 120" width="88" height="88" role="img" aria-label="Avatar da Dallyla">
      <circle cx="60" cy="60" r="58" fill="#F3D9C4" />
      {/* cabelo: uma elipse só atrás do rosto, funcionando como moldura */}
      <ellipse cx="60" cy="58" rx="38" ry="46" fill="#241B15" />
      <ellipse cx="60" cy="66" rx="26" ry="28" fill="#E8B487" />
      <circle cx="49" cy="64" r="3.2" fill="#2A1D14" />
      <circle cx="71" cy="64" r="3.2" fill="#2A1D14" />
      <path d="M46 60c2-2 6-2 8 0" fill="none" stroke="#2A1D14" strokeWidth="2" strokeLinecap="round" />
      <path d="M66 60c2-2 6-2 8 0" fill="none" stroke="#2A1D14" strokeWidth="2" strokeLinecap="round" />
      <path
        d="M45 78c5 7 25 7 30 0"
        fill="none"
        stroke="#7A3B2E"
        strokeWidth="3.2"
        strokeLinecap="round"
      />
      <path d="M47 79c4 4 22 4 26 0" fill="#fff" />
      <path d="M40 96c8-6 32-6 40 0v18H40z" fill="#4E7CA6" />
      <circle cx="60" cy="100" r="2" fill="#2E5A80" />
    </svg>
  );
}

function AvatarClaude() {
  return (
    <svg viewBox="0 0 120 120" width="88" height="88" role="img" aria-label="Avatar do Claude">
      <circle cx="60" cy="60" r="58" fill="#F4ECE4" />
      <circle cx="60" cy="64" r="34" fill="#CC785C" />
      <circle cx="49" cy="60" r="4" fill="#2A1810" />
      <circle cx="71" cy="60" r="4" fill="#2A1810" />
      <path d="M48 76c5 6 19 6 24 0" fill="none" stroke="#2A1810" strokeWidth="3.2" strokeLinecap="round" />
      <path
        d="M60 8l3.6 10.8L74 22l-10.4 3.2L60 36l-3.6-10.8L46 22l10.4-3.2z"
        fill="#CC785C"
      />
      <path
        d="M18 60l3 8 8 3-8 3-3 8-3-8-8-3 8-3z"
        fill="#CC785C"
        opacity="0.85"
      />
      <path
        d="M102 60l3 8 8 3-8 3-3 8-3-8-8-3 8-3z"
        fill="#CC785C"
        opacity="0.85"
      />
    </svg>
  );
}
