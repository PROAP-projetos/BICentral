import { campusDaCoordenacao, extrairSigla, nomeCurtoCoordenacao } from './sigla';

describe('sigla', () => {
  it('usa o nome curto das unidades cujo final do nome é só o local', () => {
    expect(extrairSigla('Clínica-escola de Especialidades em Saúde- CESAU - CUP')).toBe('CESAU');
    expect(extrairSigla('Ouvidoria - GAB')).toBe('Ouvidoria');
    expect(extrairSigla('Procuradoria Jurídica - PROJUR - GAB')).toBe('PROJUR');
    expect(extrairSigla('Setor de Integridade e Transparência - SITAI - Chefia de Gabinete - GAB')).toBe('SITAI');
    expect(extrairSigla('Pró-Reitoria de Extensão - PROEX')).toBe('PROEX');
  });

  it('identifica o campus depois do último hífen', () => {
    expect(campusDaCoordenacao('Coordenação do Curso de Direito - Palmas')).toBe('Palmas');
    expect(campusDaCoordenacao('Coordenação do Curso de Mestrado Acadêmico em Educação - PPGE -Palmas')).toBe('Palmas');
    expect(campusDaCoordenacao('Coordenação do Curso de Mestrado Acadêmico em Biotecnologia - PPGBiotec-  Gurupi')).toBe('Gurupi');
    expect(campusDaCoordenacao('Coordenação do curso de Doutorado Acadêmico em Letras - PPGL - CPN')).toBe('Porto Nacional');
    expect(campusDaCoordenacao('Coordenação de Processos Seletivos - COPESE - GAB')).toBe('Outras unidades');
  });

  it('encurta o nome da coordenação sem perder o curso', () => {
    expect(nomeCurtoCoordenacao('Coordenação do Curso de Direito - Palmas', true)).toBe('Direito (Palmas)');
    expect(nomeCurtoCoordenacao('Coordenação do Curso de Direito - Palmas', false)).toBe('Direito');
    expect(nomeCurtoCoordenacao('Coordenação de Processos Seletivos - COPESE - GAB', true)).toBe('Processos Seletivos - COPESE - GAB');
  });
});
