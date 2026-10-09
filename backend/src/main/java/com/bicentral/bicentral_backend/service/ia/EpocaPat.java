package com.bicentral.bicentral_backend.service.ia;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Bloco de contexto que diz ao agente em que fase do ano o PAT está, para ele calibrar a cobrança. */
public final class EpocaPat {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Referências mínimas de execução esperadas na reta final; ajustar aqui se o time combinar outros valores.
    private static final String REFERENCIA_FINAL = "perto de 75% em outubro, 90% em novembro e 100% em dezembro";

    private EpocaPat() {
    }

    public static String blocoParaHoje() {
        return bloco(LocalDate.now(FUSO));
    }

    static String bloco(LocalDate hoje) {
        int mes = hoje.getMonthValue();
        String cabecalho = "ÉPOCA DO ANO — hoje é " + hoje.format(DATA) + ". O PAT é do ano corrente, então use a fase abaixo para calibrar o tom ao falar de percentuais de execução. ";
        String regra;
        if (mes <= 6) {
            regra = "FASE: 1º SEMESTRE (janeiro a junho). É normal a execução estar baixa e ações zeradas não são alarme por si só: cobre com tom leve. "
                  + "Destaque como positivo as ações que já passaram de cerca de 50% (adiantadas para esta época). "
                  + "Para uma ação zerada, use o título da ação para levantar possíveis motivos (ex.: depende de etapa anterior, é entrega de fim de ciclo, exige processo demorado), "
                  + "sempre como hipótese e nunca como fato, pois o dado não informa o motivo.";
        } else if (mes <= 9) {
            regra = "FASE: MEIO DO ANO (julho a setembro). Espera-se pelo menos 50% de execução. "
                  + "Aponte as unidades e ações abaixo disso e, pelo texto da ação, levante possíveis motivos (como hipótese, não como fato). "
                  + "Reconheça quem já está acima do esperado.";
        } else {
            regra = "FASE: RETA FINAL (outubro a dezembro). Execução baixa é preocupante e o tom deve ser firme e direto. "
                  + "Referência de execução esperada: " + REFERENCIA_FINAL + ". "
                  + "Priorize ações paradas ou bem abaixo do esperado, diga o que está em risco de não ser concluído no ano e continue sem inventar motivos que o dado não traz.";
        }
        return cabecalho + regra + "\n\n";
    }
}
