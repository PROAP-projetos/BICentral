import { createClient } from "@supabase/supabase-js";

const url = process.env.NEXT_PUBLIC_SUPABASE_URL!;
const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!;

export const supabase = createClient(url, anonKey);

export type Dono = "dallyla" | "neci" | "lean" | "todos";
export type Status = "a_fazer" | "fazendo" | "concluido";

export interface Arquivo {
  path: string;
  novo: boolean;
}

export interface Sprint {
  id: number;
  numero: number;
  nome: string;
  data_inicio: string;
  data_fim: string;
}

export interface Tarefa {
  id: number;
  sprint_id: number;
  titulo: string;
  descricao: string | null;
  contexto: string | null;
  passos: string[];
  arquivos: Arquivo[];
  dono: Dono;
  status: Status;
  depende_de: number | null;
}
