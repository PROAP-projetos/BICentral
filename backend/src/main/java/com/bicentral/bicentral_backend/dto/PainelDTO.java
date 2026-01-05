package com.bicentral.bicentral_backend.dto;

import com.bicentral.bicentral_backend.model.AddPainel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PainelDTO {
    private String nome;
    private String linkPowerBi;
    private String imagemCapaUrl;
    private AddPainel.StatusCaptura statusCaptura;
}
