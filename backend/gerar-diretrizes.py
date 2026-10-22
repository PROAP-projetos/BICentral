"""Gera src/main/resources/diretrizes/acoes-diretrizes.json a partir da base do painel-rastreabilidade (versão lite).

Uso (da pasta backend):
    python gerar-diretrizes.py <caminho>/painel-rastreabilidade/docs/lite/json_teste/base_painel_atualizada.json

Mantém só o que o agente usa para comparar tarefa com diretriz; as propostas da comunidade ficam de fora.
"""
import json
import sys
from pathlib import Path

CAMPOS = ["numero_acao", "acao", "inep", "iesgo", "inep_detalhes", "iesgo_detalhes"]
SAIDA = Path("src/main/resources/diretrizes/acoes-diretrizes.json")

origem = Path(sys.argv[1])
base = json.loads(origem.read_text(encoding="utf-8"))

acoes = [{campo: acao.get(campo) for campo in CAMPOS} for acao in base["acoes"]]

codigos = [a["numero_acao"] for a in acoes]
if len(codigos) != len(set(codigos)):
    sys.exit("numero_acao repetido na base: a busca por código ficaria ambígua")

SAIDA.parent.mkdir(parents=True, exist_ok=True)
SAIDA.write_text(
    json.dumps({"versao": base["meta"]["versao"], "acoes": acoes}, ensure_ascii=False),
    encoding="utf-8",
)
print(f"{len(acoes)} ações ({base['meta']['versao']}) gravadas em {SAIDA}")
