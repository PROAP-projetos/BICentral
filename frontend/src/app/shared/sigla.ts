// Unidades cujo último trecho do nome é só o local (CUP, GAB) e não identificaria a unidade na tela.
const SIGLAS_FIXAS: [RegExp, string][] = [
  [/CESAU/i, 'CESAU'],
  [/Ouvidoria/i, 'Ouvidoria'],
  [/PROJUR/i, 'PROJUR'],
  [/SITAI/i, 'SITAI']
];

export function extrairSigla(departamento: string): string {
  const fixa = SIGLAS_FIXAS.find(([padrao]) => padrao.test(departamento));
  if (fixa) return fixa[1];
  const partes = departamento.split(' - ');
  return partes.length > 1 ? partes[partes.length - 1].trim() : departamento;
}

const CAMPI: [RegExp, string][] = [
  [/porto nacional|\bcpn\b/i, 'Porto Nacional'],
  [/palmas|\bcup\b/i, 'Palmas'],
  [/gurupi|\bcaug\b/i, 'Gurupi'],
  [/arraias|\bcuar\b/i, 'Arraias'],
  [/miracema|\bcaum\b/i, 'Miracema']
];

export const CAMPUS_OUTROS = 'Outras unidades';

// O campus vem sempre depois do último hífen do nome da coordenação.
export function campusDaCoordenacao(nome: string): string {
  const final = nome.slice(nome.lastIndexOf('-') + 1);
  return CAMPI.find(([padrao]) => padrao.test(final))?.[1] ?? CAMPUS_OUTROS;
}

export function nomeCurtoCoordenacao(nome: string, comCampus: boolean): string {
  let curto = nome.replace(/^Coordena[çc][ãa]o\s+(do|da|de)\s+(curso\s+(de|do|da)\s+)?/i, '').replace(/\s{2,}/g, ' ').trim();
  const campus = campusDaCoordenacao(nome);
  if (campus === CAMPUS_OUTROS) return curto;
  curto = curto.replace(/\s*-\s*[^-]*$/, '').trim();
  return comCampus ? `${curto} (${campus})` : curto;
}
