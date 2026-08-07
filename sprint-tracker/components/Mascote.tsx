"use client";

import { useEffect, useRef, useState } from "react";
import type { Tarefa } from "@/lib/supabase";
import styles from "./Mascote.module.css";

interface Props {
  onTrocarTema: () => void;
  tarefas: Tarefa[];
  despedindo: boolean;
  onDespedida: () => void;
}

type Acao =
  | "andando"
  | "apontando"
  | "trocando-tema"
  | "comemorando"
  | "tropecando"
  | "dancando"
  | "soneca"
  | "assustado"
  | "indo-embora";

const BICHINHOS = ["🦖", "🤖", "👻"];

const FALAS = [
  "boa, hein",
  "vamo que vamo 🚀",
  "clica aí ↑",
  "não esquece a PR",
  "ó, tem tarefa ali",
  "tá indo bem",
  "hmm, deixa eu ver...",
  "ninguém tá vendo, né?",
];

export default function Mascote({ onTrocarTema, tarefas, despedindo, onDespedida }: Props) {
  const [pos, setPos] = useState({ x: 80, y: 80 });
  const [virado, setVirado] = useState(false);
  const [moonwalk, setMoonwalk] = useState(false);
  const [acao, setAcao] = useState<Acao>("andando");
  const [fala, setFala] = useState<string | null>(null);
  const [bichinho] = useState(() => BICHINHOS[Math.floor(Math.random() * BICHINHOS.length)]);
  const [reduzMovimento, setReduzMovimento] = useState(false);
  const [sumindo, setSumindo] = useState(false);
  const timeoutRef = useRef<ReturnType<typeof setTimeout>>();
  const posAtualRef = useRef({ x: 80, y: 80 });
  const tarefasAnterioresRef = useRef<Map<number, string>>(new Map());
  const cicloRef = useRef<() => void>();

  function moverPara(alvo: { x: number; y: number }, opts?: { semMoonwalk?: boolean }) {
    const indoPraEsquerda = alvo.x < posAtualRef.current.x;
    const fazMoonwalk = !opts?.semMoonwalk && Math.random() < 0.3;
    setVirado(fazMoonwalk ? !indoPraEsquerda : indoPraEsquerda);
    setMoonwalk(fazMoonwalk);
    posAtualRef.current = alvo;
    setPos(alvo);
  }

  // Sequência de despedida
  useEffect(() => {
    if (!despedindo) return;
    clearTimeout(timeoutRef.current);
    setFala("tchau... 😢");
    const indoEsquerda = posAtualRef.current.x > window.innerWidth / 2;
    moverPara({ x: indoEsquerda ? -80 : window.innerWidth + 20, y: posAtualRef.current.y }, { semMoonwalk: true });
    setAcao("indo-embora");
    timeoutRef.current = setTimeout(() => setFala(null), 1600);
    timeoutRef.current = setTimeout(() => {
      setSumindo(true);
      setTimeout(onDespedida, 700);
    }, 2200);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [despedindo]);

  // Comemora sempre que alguma tarefa vira "concluido"
  useEffect(() => {
    if (despedindo) return;
    const anteriores = tarefasAnterioresRef.current;
    if (anteriores.size > 0) {
      const concluidaAgora = tarefas.find(
        (t) => t.status === "concluido" && anteriores.get(t.id) && anteriores.get(t.id) !== "concluido"
      );
      if (concluidaAgora && !reduzMovimento) {
        const card = document.querySelector<HTMLElement>(`[data-tarefa-id="${concluidaAgora.id}"]`);
        if (card) {
          clearTimeout(timeoutRef.current);
          const rect = card.getBoundingClientRect();
          moverPara({ x: rect.left + rect.width / 2 - 20, y: rect.top - 40 });
          setAcao("comemorando");
          setFala("mandou bem! 🎉");
          timeoutRef.current = setTimeout(() => {
            setFala(null);
            setAcao("andando");
            agendarProximoExterno();
          }, 2600);
        }
      }
    }
    tarefas.forEach((t) => anteriores.set(t.id, t.status));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tarefas]);

  function agendarProximoExterno() {
    timeoutRef.current = setTimeout(() => cicloRef.current?.(), 3000 + Math.random() * 3000);
  }

  useEffect(() => {
    const reduzir = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    setReduzMovimento(reduzir);
    if (reduzir) return;

    function encerrarAcao(proximaAcao: () => void, ms: number) {
      timeoutRef.current = setTimeout(() => {
        setFala(null);
        setAcao("andando");
        proximaAcao();
      }, ms);
    }

    function agendarProximo() {
      timeoutRef.current = setTimeout(proximoCiclo, 4000 + Math.random() * 4000);
    }

    function proximoCiclo() {
      const dado = Math.random();
      let alvo: { x: number; y: number } | null = null;
      let proximaAcao: Acao = "andando";

      if (dado < 0.16) {
        const cards = Array.from(document.querySelectorAll<HTMLElement>("[data-tarefa-id]"));
        if (cards.length > 0) {
          const card = cards[Math.floor(Math.random() * cards.length)];
          const rect = card.getBoundingClientRect();
          if (rect.top >= 0 && rect.top <= window.innerHeight - 60) {
            alvo = { x: rect.left + 12, y: rect.top - 30 };
            proximaAcao = "apontando";
          }
        }
      } else if (dado < 0.27) {
        const botao = document.querySelector<HTMLElement>("[data-theme-toggle]");
        if (botao) {
          const rect = botao.getBoundingClientRect();
          alvo = { x: rect.left - 36, y: rect.top };
          proximaAcao = "trocando-tema";
        }
      } else if (dado < 0.42) {
        proximaAcao = "tropecando";
      } else if (dado < 0.55) {
        proximaAcao = "dancando";
      } else if (dado < 0.66) {
        proximaAcao = "soneca";
      } else if (dado < 0.76) {
        proximaAcao = "assustado";
      }

      if (!alvo) {
        alvo = {
          x: 40 + Math.random() * (window.innerWidth - 120),
          y: 100 + Math.random() * Math.min(400, window.innerHeight - 200),
        };
      }

      moverPara(alvo);
      setAcao("andando");
      const mostraFala = Math.random() < 0.25;

      timeoutRef.current = setTimeout(() => {
        switch (proximaAcao) {
          case "apontando":
            setAcao("apontando");
            if (mostraFala) setFala(FALAS[Math.floor(Math.random() * FALAS.length)]);
            encerrarAcao(agendarProximo, 1800);
            break;
          case "trocando-tema":
            setAcao("trocando-tema");
            onTrocarTema();
            encerrarAcao(agendarProximo, 1200);
            break;
          case "tropecando":
            setAcao("tropecando");
            setFala("aaaai 😵");
            encerrarAcao(agendarProximo, 1400);
            break;
          case "dancando":
            setAcao("dancando");
            setFala("🎶🕺🎶");
            encerrarAcao(agendarProximo, 2000);
            break;
          case "soneca":
            setAcao("soneca");
            setFala("💤 zzz...");
            encerrarAcao(agendarProximo, 2200);
            break;
          case "assustado":
            setAcao("assustado");
            setFala("💦 nossa, que susto");
            encerrarAcao(agendarProximo, 1300);
            break;
          default:
            if (mostraFala) {
              setFala(FALAS[Math.floor(Math.random() * FALAS.length)]);
              encerrarAcao(agendarProximo, 1600);
            } else {
              agendarProximo();
            }
        }
      }, 1500);
    }

    cicloRef.current = proximoCiclo;
    timeoutRef.current = setTimeout(proximoCiclo, 3000);
    return () => clearTimeout(timeoutRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (reduzMovimento) return null;

  const parado = acao !== "andando" && acao !== "indo-embora";

  let classeCorpo = styles.andando;
  if (acao === "comemorando") classeCorpo = styles.comemorando;
  else if (acao === "tropecando") classeCorpo = styles.tropecando;
  else if (acao === "dancando") classeCorpo = styles.dancando;
  else if (acao === "soneca") classeCorpo = styles.soneca;
  else if (acao === "assustado") classeCorpo = styles.assustado;
  else if (acao === "indo-embora") classeCorpo = styles.indoEmbora;
  else if (parado) classeCorpo = styles.parado;
  else if (moonwalk) classeCorpo = styles.moonwalking;

  return (
    <div
      className={styles.mascote}
      style={{
        left: pos.x,
        top: pos.y,
        transform: `scaleX(${virado ? -1 : 1})`,
        opacity: sumindo ? 0 : 1,
        transition: sumindo
          ? "opacity 0.6s ease-in, left 2s ease-in-out, top 1.5s ease-in-out"
          : "left 1.5s ease-in-out, top 1.5s ease-in-out",
      }}
      aria-hidden="true"
    >
      {fala && (
        <span className={styles.bolha} style={{ transform: `scaleX(${virado ? -1 : 1})` }}>
          {fala}
        </span>
      )}
      <span className={classeCorpo}>{bichinho}</span>
      <span className={styles.sombra} />
      {acao === "andando" && moonwalk && (
        <>
          <span className={styles.nota}>🎵</span>
          <span className={styles.oculos}>🕶️</span>
        </>
      )}
      {acao === "apontando" && <span className={styles.aponta}>👉</span>}
      {acao === "trocando-tema" && <span className={styles.sparkle}>✨</span>}
      {acao === "comemorando" && (
        <>
          <span className={styles.confete1}>🎉</span>
          <span className={styles.confete2}>🎊</span>
        </>
      )}
      {acao === "indo-embora" && <span className={styles.triste}>😢</span>}
    </div>
  );
}
