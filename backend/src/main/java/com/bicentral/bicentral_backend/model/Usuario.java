package com.bicentral.bicentral_backend.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String email;
    private String password;

    public static Usuario cadastrarUsuario(Long id, String username, String email, String password) {
        Usuario user = new Usuario();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(password);
        user.setPassword(password);
        return user;
    }
}
