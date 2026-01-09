package com.bicentral.bicentral_backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome não pode estar em branco")
    @Size(min = 3, max = 20, message = "Nome deve ter entre 3 e 20 caracteres")
    @Column(name = "username", unique = true) // O ARROCHO: No banco continua 'username', no Java vira 'nome'
    private String nome;

    @NotBlank(message = "Email não pode estar em branco")
    @Email(message = "Formato de email inválido")
    @Column(unique = true)
    private String email;

    @NotBlank(message = "Password não pode estar em branco")
    @Size(min = 8, message = "Password deve ter no mínimo 8 caracteres")
    private String password;

    private String verificationToken;

    private boolean enabled;

    public Usuario(String nome, String email, String password) {
        this.nome = nome;
        this.email = email;
        this.password = password;
        this.enabled = false;
    }

    // --- MÉTODOS OBRIGATÓRIOS DO USERDETAILS ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getUsername() {
        // O Spring Security exige que este método retorne o LOGIN (email)
        return this.email;
    }

    // Criamos um getter explícito para o nome de exibição para não confundir com o de segurança
    public String getNomeExibicao() {
        return this.nome;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return this.enabled; }
}