"use client";

import { useEffect, useMemo, useState, type CSSProperties } from "react";
import { supabase, Sprint, Tarefa, Status, Dono } from "@/lib/supabase";
import styles from "./page.module.css";
import Mascote from "@/components/Mascote";

const OWNER_LABEL: Record<Dono, string> = {
  dallyla: "Dallyla",
  neci: "Neci",
  lean: "Lean",
  todos: "Todos",
};

const OWNER_VAR: Record<Dono, string> = {
  dallyla: "--owner-dallyla",
  neci: "--owner-neci",
  lean: "--owner-lean",
  todos: "--ink-faint",
};

const OWNER_BG_VAR: Record<Dono, string> = {
  dallyla: "--owner-dallyla-bg",
  neci: "--owner-neci-bg",
  lean: "--owner-lean-bg",
  todos: "--surface-alt",
};

function hoje(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function Page() {
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [tarefas, setTarefas] = useState<Tarefa[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [salvandoId, setSalvandoId] = useState<number | null>(null);
  const [aberta, setAberta] = useState<Tarefa | null>(null);
  const [tema, setTema] = useState<"light" | "dark">("light");
  const [quemSouEu, setQuemSouEu] = useState<Dono | null | undefined>(undefined);
  const [mascoteAtivo, setMascoteAtivo] = useState(true);
  const [despedindo, setDespedindo] = useState(false);

  useEffect(() => {
    const salvo = localStorage.getItem("sprint-tracker-tema") as "light" | "dark" | null;
    const preferido = salvo ?? (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    setTema(preferido);
    document.documentElement.setAttribute("data-theme", preferido);

    const usuarioSalvo = localStorage.getItem("sprint-tracker-usuario") as Dono | null;
    setQuemSouEu(usuarioSalvo);

    setMascoteAtivo(localStorage.getItem("sprint-tracker-mascote") !== "off");
  }, []);

  function despedirMascote() {
    setDespedindo(true);
  }

  function mascoteFoiEmbora() {
    setDespedindo(false);
    setMascoteAtivo(false);
    localStorage.setItem("sprint-tracker-mascote", "off");
  }

  function trazerMascoteDeVolta() {
    localStorage.setItem("sprint-tracker-mascote", "on");
    setMascoteAtivo(true);
  }

  function alternarTema() {
    const novo = tema === "light" ? "dark" : "light";
    setTema(novo);
    document.documentElement.setAttribute("data-theme", novo);
    localStorage.setItem("sprint-tracker-tema", novo);
  }

  function escolherIdentidade(dono: Dono) {
    localStorage.setItem("sprint-tracker-usuario", dono);
    setQuemSouEu(dono);
  }

  useEffect(() => {
    async function carregar() {
      const [{ data: s, error: e1 }, { data: t, error: e2 }] = await Promise.all([
        supabase.from("sprint_tracker_sprints").select("*").order("numero"),
        supabase.from("sprint_tracker_tarefas").select("*").order("id"),
      ]);
      if (e1 || e2) {
        setErro((e1 || e2)?.message ?? "Erro ao carregar dados.");
      } else {
        setSprints(s ?? []);
        setTarefas(t ?? []);
      }
      setCarregando(false);
    }
    carregar();
  }, []);

  const tarefaPorId = useMemo(() => {
    const mapa = new Map<number, Tarefa>();
    tarefas.forEach((t) => mapa.set(t.id, t));
    return mapa;
  }, [tarefas]);

  const sprintPorId = useMemo(() => {
    const mapa = new Map<number, Sprint>();
    sprints.forEach((s) => mapa.set(s.id, s));
    return mapa;
  }, [sprints]);

  function estaAtrasada(t: Tarefa): boolean {
    if (t.status === "concluido") return false;
    const sprint = sprintPorId.get(t.sprint_id);
    if (!sprint) return false;
    return hoje() > sprint.data_fim;
  }

  function bloqueio(t: Tarefa): { bloqueada: boolean; titulo?: string; dono?: Dono } {
    if (!t.depende_de) return { bloqueada: false };
    const dep = tarefaPorId.get(t.depende_de);
    if (!dep || dep.status === "concluido") return { bloqueada: false };
    return { bloqueada: true, titulo: dep.titulo, dono: dep.dono };
  }

  const atrasadasPorDono = useMemo(() => {
    const contagem: Partial<Record<Dono, number>> = {};
    tarefas.forEach((t) => {
      if (estaAtrasada(t)) {
        contagem[t.dono] = (contagem[t.dono] ?? 0) + 1;
      }
    });
    return contagem;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tarefas, sprints]);

  const temAtraso = Object.keys(atrasadasPorDono).length > 0;

  const sprintAtual = useMemo(() => {
    const h = hoje();
    const emAndamento = sprints.find((s) => h >= s.data_inicio && h <= s.data_fim);
    if (emAndamento) return emAndamento;
    return sprints.find((s) => h < s.data_inicio) ?? sprints[sprints.length - 1] ?? null;
  }, [sprints]);

  const minhasTarefasParaComecar = useMemo(() => {
    if (!quemSouEu || quemSouEu === "todos" || !sprintAtual) return [];
    return tarefas.filter(
      (t) =>
        t.dono === quemSouEu &&
        t.sprint_id === sprintAtual.id &&
        t.status === "a_fazer" &&
        !bloqueio(t).bloqueada
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tarefas, sprintAtual, quemSouEu]);

  async function atualizarStatus(t: Tarefa, novoStatus: Status) {
    setSalvandoId(t.id);
    const { error } = await supabase
      .from("sprint_tracker_tarefas")
      .update({ status: novoStatus })
      .eq("id", t.id);
    if (!error) {
      setTarefas((prev) => prev.map((x) => (x.id === t.id ? { ...x, status: novoStatus } : x)));
      if (aberta?.id === t.id) setAberta({ ...t, status: novoStatus });
    }
    setSalvandoId(null);
  }

  if (carregando) {
    return (
      <div className={styles.page}>
        <p className={styles.loading}>Carregando quadro...</p>
      </div>
    );
  }

  if (erro) {
    return (
      <div className={styles.page}>
        <p className={styles.error}>
          Erro ao carregar: {erro}. Confira se rodou o <span className="mono">supabase/schema.sql</span> e se as
          variáveis de ambiente estão certas.
        </p>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.ruleBanner}>🔒 REGRA: ABRA PR, SEMPRE. Sem exceção.</div>

      {sprintAtual && (
        <div className={styles.todayBanner}>
          <div className={styles.todayDate}>
            📅 Hoje é <strong>{formatarDataLonga(hoje())}</strong> — Sprint {sprintAtual.numero} vai de{" "}
            {formatarData(sprintAtual.data_inicio)} até {formatarData(sprintAtual.data_fim)}
          </div>
          {quemSouEu && quemSouEu !== "todos" && (
            <div className={styles.todayTasks}>
              {minhasTarefasParaComecar.length > 0 ? (
                <>
                  ⏰ Hora de começar, {OWNER_LABEL[quemSouEu]}:{" "}
                  {minhasTarefasParaComecar.map((t) => `"${t.titulo}"`).join(", ")}
                </>
              ) : (
                <>✅ {OWNER_LABEL[quemSouEu]}, você não tem tarefa pendente pra começar nessa sprint.</>
              )}
            </div>
          )}
        </div>
      )}

      {temAtraso && (
        <div className={styles.lateBanner}>
          <strong>⚠️ Tem tarefa atrasada</strong>
          {Object.entries(atrasadasPorDono)
            .map(([dono, n]) => `${OWNER_LABEL[dono as Dono]}: ${n} tarefa${n === 1 ? "" : "s"}`)
            .join(" · ")}
        </div>
      )}

      <header className={styles.header}>
        <div>
          <h1>BICentral — Sprint Tracker</h1>
          <p>Power BI hub + agente proIAp para a PROAP/UFT, dividido entre Dallyla, Neci e Lean.</p>
        </div>
        <div className={styles.headerActions}>
          {quemSouEu && quemSouEu !== "todos" && (
            <button className={styles.themeToggle} onClick={() => setQuemSouEu(null)}>
              👤 {OWNER_LABEL[quemSouEu]} — trocar
            </button>
          )}
          <button className={styles.themeToggle} onClick={alternarTema} data-theme-toggle>
            {tema === "light" ? "🌙 Escuro" : "☀️ Claro"}
          </button>
          {mascoteAtivo && !despedindo ? (
            <button className={styles.themeToggle} onClick={despedirMascote}>
              👋 Adeus, mascote
            </button>
          ) : !mascoteAtivo ? (
            <button className={styles.themeToggle} onClick={trazerMascoteDeVolta}>
              🦖 Trazer mascote de volta
            </button>
          ) : null}
        </div>
      </header>

      <div className={styles.legend}>
        <span className={styles.legendChip}>
          <span className={styles.legendDot} style={{ background: "var(--owner-dallyla)" }} />
          Dallyla — IA
        </span>
        <span className={styles.legendChip}>
          <span className={styles.legendDot} style={{ background: "var(--owner-neci)" }} />
          Neci — equipes/admin
        </span>
        <span className={styles.legendChip}>
          <span className={styles.legendDot} style={{ background: "var(--owner-lean)" }} />
          Lean — painéis/notificações
        </span>
      </div>

      <div className={styles.board}>
        {sprints.map((sprint) => {
          const tarefasDaSprint = tarefas.filter((t) => t.sprint_id === sprint.id);
          const isBuffer = sprint.numero === 4;
          return (
            <section key={sprint.id} className={styles.column}>
              <div className={styles.columnHead}>
                <div className={styles.columnHeadText}>
                  <h2>Sprint {sprint.numero}</h2>
                  <p className={styles.columnSub}>{sprint.nome}</p>
                </div>
                <span className="mono">
                  {formatarData(sprint.data_inicio)}–{formatarData(sprint.data_fim)}
                </span>
              </div>

              <div className={styles.cardRow}>
              {tarefasDaSprint.map((t) => {
                const b = bloqueio(t);
                const atrasada = estaAtrasada(t);
                const cardStyle: CSSProperties & Record<string, string> = {
                  "--ownerColor": `var(${OWNER_VAR[t.dono]})`,
                  "--chipColor": `var(${OWNER_VAR[t.dono]})`,
                  "--chipBg": `var(${OWNER_BG_VAR[t.dono]})`,
                };
                return (
                  <article
                    key={t.id}
                    data-tarefa-id={t.id}
                    className={`${styles.card} ${atrasada ? styles.cardLate : ""}`}
                    style={cardStyle}
                  >
                    <div className={styles.cardTitleRow} onClick={() => setAberta(t)}>
                      <p className={styles.cardTitle}>{t.titulo}</p>
                    </div>
                    {t.descricao && (
                      <p className={styles.cardDesc} onClick={() => setAberta(t)}>
                        {t.descricao}
                      </p>
                    )}
                    {b.bloqueada && (
                      <div className={styles.blockedBadge}>
                        🔒 Bloqueado até {OWNER_LABEL[b.dono!]} terminar &quot;{b.titulo}&quot;
                      </div>
                    )}
                    <div className={styles.cardFoot}>
                      <span className={styles.ownerChip}>{OWNER_LABEL[t.dono]}</span>
                      <select
                        className={styles.statusSelect}
                        value={t.status}
                        disabled={salvandoId === t.id}
                        onChange={(e) => atualizarStatus(t, e.target.value as Status)}
                      >
                        <option value="a_fazer">A Fazer</option>
                        <option value="fazendo">Fazendo</option>
                        <option value="concluido">Concluído</option>
                      </select>
                    </div>
                  </article>
                );
              })}
              </div>

              {isBuffer && (
                <p className={styles.bufferNote}>
                  Reservado como buffer — não empilhar feature nova aqui. Prazo apertado do estágio.
                </p>
              )}
            </section>
          );
        })}
      </div>

      {quemSouEu === null && (
        <div className={styles.scrim}>
          <div className={styles.identityModal}>
            <h2>Quem é você?</h2>
            <p>Isso só personaliza o que aparece pra você — todo mundo continua vendo o quadro inteiro.</p>
            <div className={styles.identityOptions}>
              {(["dallyla", "neci", "lean"] as Dono[]).map((d) => (
                <button key={d} className={styles.identityButton} onClick={() => escolherIdentidade(d)}>
                  <span
                    className={styles.legendDot}
                    style={{ background: `var(${OWNER_VAR[d]})`, width: 12, height: 12 }}
                  />
                  {OWNER_LABEL[d]}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {aberta && (
        <>
          <div className={styles.scrim} onClick={() => setAberta(null)} />
          <aside className={styles.drawer} role="dialog" aria-modal="true">
            <div className={styles.drawerTop}>
              <h3>{aberta.titulo}</h3>
              <button className={styles.drawerClose} onClick={() => setAberta(null)} aria-label="Fechar">
                ✕
              </button>
            </div>
            {aberta.descricao && <p className={styles.drawerDesc}>{aberta.descricao}</p>}

            {aberta.contexto && (
              <div className={styles.drawerSection}>
                <h4>O que isso significa</h4>
                <div
                  className={styles.contextBox}
                  dangerouslySetInnerHTML={{ __html: aberta.contexto }}
                />
              </div>
            )}

            {aberta.passos.length > 0 && (
              <div className={styles.drawerSection}>
                <h4>Passo a passo</h4>
                <ol className={styles.stepsList}>
                  {aberta.passos.map((p, i) => (
                    <li key={i} dangerouslySetInnerHTML={{ __html: p }} />
                  ))}
                </ol>
              </div>
            )}

            <div className={styles.drawerSection}>
              <h4>Arquivos pra abrir</h4>
              <ul className={styles.fileList}>
                {aberta.arquivos.length === 0 ? (
                  <li>Sem arquivo específico — é trabalho de time, não de código.</li>
                ) : (
                  aberta.arquivos.map((f, i) => (
                    <li key={i} className={f.novo ? styles.fileNew : ""}>
                      {f.novo ? "✨" : "📄"} {f.path}
                    </li>
                  ))
                )}
              </ul>
            </div>
          </aside>
        </>
      )}

      {(mascoteAtivo || despedindo) && (
        <Mascote
          onTrocarTema={alternarTema}
          tarefas={tarefas}
          despedindo={despedindo}
          onDespedida={mascoteFoiEmbora}
        />
      )}
    </div>
  );
}

function formatarData(iso: string): string {
  const [, mes, dia] = iso.split("-");
  return `${dia}/${mes}`;
}

const MESES = [
  "janeiro", "fevereiro", "março", "abril", "maio", "junho",
  "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
];

function formatarDataLonga(iso: string): string {
  const [ano, mes, dia] = iso.split("-").map(Number);
  return `${dia} de ${MESES[mes - 1]} de ${ano}`;
}
