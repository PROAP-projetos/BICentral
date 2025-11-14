package com.bicentral.bicentral_backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;



//estrutura do user

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Username não pode estar em branco")
    @Size(min = 3, max = 20, message = "Username deve ter entre 3 e 20 caracteres")
    private String username;

    @NotBlank(message = "Email não pode estar em branco")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "Password não pode estar em branco")
    @Size(min = 8, message = "Password deve ter no mínimo 8 caracteres")
    private String password;

    private String verificationToken;

    private boolean enabled;

    public Usuario(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.enabled = false;
    }
}