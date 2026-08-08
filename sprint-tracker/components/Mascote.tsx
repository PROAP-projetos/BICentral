"use client";

import { useEffect, useRef, useState } from "react";
import type { Sprint, Tarefa } from "@/lib/supabase";
import styles from "./Mascote.module.css";

interface Props {
  onTrocarTema: () => void;
  tarefas: Tarefa[];
  sprints: Sprint[];
  despedindo: boolean;
  onDespedida: () => void;
}

type Acao = "andando" | "apontando" | "trocando-tema" | "empolgado" | "comemorando" | "mega-comemorando" | "indo-embora";

const BICHINHOS = ["🐻", "🐼", "🐨"];

const FALAS = ["vamos lá!", "boa 👍", "olha essa aqui", "tá indo bem", "clica aí ↑"];

export default function Mascote({ onTrocarTema, tarefas, sprints, despedindo, onDespedida }: Props) {
  const [pos, setPos] = useState({ x: 80, y: 80 });
  const [virado, setVirado] = useState(false);
  const [acao, setAcao] = useState<Acao>("andando");
  const [fala, setFala] = useState<string | null>(null);
  const [bichinho] = useState(() => BICHINHOS[Math.floor(Math.random() * BICHINHOS.length)]);
  const [reduzMovimento, setReduzMovimento] = useState(false);
  const [sumindo, setSumindo] = useState(false);
  const timeoutRef = useRef<ReturnType<typeof setTimeout>>();
  const posAtualRef = useRef({ x: 80, y: 80 });
  const tarefasAnterioresRef = useRef<Map<number, string>>(new Map());
  const cicloRef = useRef<() => void>();

  function moverPara(alvo: { x: number; y: number }) {
    setVirado(alvo.x < posAtualRef.current.x);
    posAtualRef.current = alvo;
    setPos(alvo);
  }

  function reagirNoCard(tarefaId: number, acaoNova: Acao, texto: string, duracaoMs: number) {
    const card = document.querySelector<HTMLElement>(`[data-tarefa-id="${tarefaId}"]`);
    if (!card) return false;
    clearTimeout(timeoutRef.current);
    const rect = card.getBoundingClientRect();
    moverPara({ x: rect.left + rect.width / 2 - 20, y: rect.top - 40 });
    setAcao(acaoNova);
    setFala(texto);
    timeoutRef.current = setTimeout(() => {
      setFala(null);
      setAcao("andando");
      agendarProximoExterno();
    }, duracaoMs);
    return true;
  }

  // Sequência de despedida
  useEffect(() => {
    if (!despedindo) return;
    clearTimeout(timeoutRef.current);
    setFala("tchau... 😢");
    const indoEsquerda = posAtualRef.current.x > window.innerWidth / 2;
    moverPara({ x: indoEsquerda ? -80 : window.innerWidth + 20, y: posAtualRef.current.y });
    setAcao("indo-embora");
    timeoutRef.current = setTimeout(() => setFala(null), 1600);
    timeoutRef.current = setTimeout(() => {
      setSumindo(true);
      setTimeout(onDespedida, 700);
    }, 2200);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [despedindo]);

  // Reage a mudanças de status: comemora "concluído" (em dobro se fecha a sprint) e anima "fazendo"
  useEffect(() => {
    if (despedindo) return;
    const anteriores = tarefasAnterioresRef.current;
    if (anteriores.size > 0 && !reduzMovimento) {
      const concluidaAgora = tarefas.find(
        (t) => t.status === "concluido" && anteriores.get(t.id) && anteriores.get(t.id) !== "concluido"
      );
      const comecadaAgora = tarefas.find(
        (t) => t.status === "fazendo" && anteriores.get(t.id) && anteriores.get(t.id) !== "fazendo"
      );

      if (concluidaAgora) {
        const tarefasDoSprint = tarefas.filter((t) => t.sprint_id === concluidaAgora.sprint_id);
        const completaAgora = tarefasDoSprint.every((t) => t.status === "concluido");
        const completaAntes = tarefasDoSprint.every((t) => (anteriores.get(t.id) ?? t.status) === "concluido");

        if (completaAgora && !completaAntes) {
          const sprint = sprints.find((s) => s.id === concluidaAgora.sprint_id);
          clearTimeout(timeoutRef.current);
          moverPara({ x: window.innerWidth / 2 - 20, y: window.innerHeight / 2 - 60 });
          setAcao("mega-comemorando");
          setFala(
            sprint ? `SPRINT ${sprint.numero} COMPLETA! Time incrível! 🏆` : "SPRINT COMPLETA! Time incrível! 🏆"
          );
          timeoutRef.current = setTimeout(() => {
            setFala(null);
            setAcao("andando");
            agendarProximoExterno();
          }, 3600);
        } else {
          reagirNoCard(concluidaAgora.id, "comemorando", "mandou bem! 🎉", 2600);
        }
      } else if (comecadaAgora) {
        reagirNoCard(comecadaAgora.id, "empolgado", "vai que vai! 💪", 1800);
      }
    }
    tarefas.forEach((t) => anteriores.set(t.id, t.status));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tarefas]);

  function agendarProximoExterno() {
    timeoutRef.current = setTimeout(() => cicloRef.current?.(), 3500 + Math.random() * 3500);
  }

  useEffect(() => {
    const reduzir = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    setReduzMovimento(reduzir);
    if (reduzir) return;

    function encerrarAcao(proxima: () => void, ms: number) {
      timeoutRef.current = setTimeout(() => {
        setFala(null);
        setAcao("andando");
        proxima();
      }, ms);
    }

    function agendarProximo() {
      timeoutRef.current = setTimeout(proximoCiclo, 5000 + Math.random() * 4000);
    }

    function proximoCiclo() {
      const dado = Math.random();
      let alvo: { x: number; y: number } | null = null;
      let proximaAcao: Acao = "andando";

      if (dado < 0.3) {
        const cards = Array.from(document.querySelectorAll<HTMLElement>("[data-tarefa-id]"));
        if (cards.length > 0) {
          const card = cards[Math.floor(Math.random() * cards.length)];
          const rect = card.getBoundingClientRect();
          if (rect.top >= 0 && rect.top <= window.innerHeight - 60) {
            alvo = { x: rect.left + 12, y: rect.top - 30 };
            proximaAcao = "apontando";
          }
        }
      } else if (dado < 0.42) {
        const botao = document.querySelector<HTMLElement>("[data-theme-toggle]");
        if (botao) {
          const rect = botao.getBoundingClientRect();
          alvo = { x: rect.left - 36, y: rect.top };
          proximaAcao = "trocando-tema";
        }
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
        if (proximaAcao === "apontando") {
          setAcao("apontando");
          if (mostraFala) setFala(FALAS[Math.floor(Math.random() * FALAS.length)]);
          encerrarAcao(agendarProximo, 1700);
        } else if (proximaAcao === "trocando-tema") {
          setAcao("trocando-tema");
          onTrocarTema();
          encerrarAcao(agendarProximo, 1200);
        } else if (mostraFala) {
          setFala(FALAS[Math.floor(Math.random() * FALAS.length)]);
          encerrarAcao(agendarProximo, 1400);
        } else {
          agendarProximo();
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
  if (acao === "mega-comemorando") classeCorpo = styles.megaComemorando;
  else if (acao === "comemorando") classeCorpo = styles.comemorando;
  else if (acao === "empolgado") classeCorpo = styles.empolgado;
  else if (acao === "indo-embora") classeCorpo = styles.indoEmbora;
  else if (parado) classeCorpo = styles.parado;

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
      {acao === "apontando" && <span className={styles.aponta}>👉</span>}
      {acao === "trocando-tema" && <span className={styles.sparkle}>✨</span>}
      {acao === "empolgado" && <span className={styles.sparkle}>💫</span>}
      {acao === "comemorando" && (
        <>
          <span className={styles.confete1}>🎉</span>
          <span className={styles.confete2}>🎊</span>
        </>
      )}
      {acao === "mega-comemorando" && (
        <>
          <span className={styles.confeteMega1}>🎉</span>
          <span className={styles.confeteMega2}>🎊</span>
          <span className={styles.confeteMega3}>🏆</span>
          <span className={styles.confeteMega4}>🎉</span>
          <span className={styles.confeteMega5}>🎊</span>
          <span className={styles.confeteMega6}>⭐</span>
        </>
      )}
      {acao === "indo-embora" && <span className={styles.triste}>😢</span>}
    </div>
  );
}
