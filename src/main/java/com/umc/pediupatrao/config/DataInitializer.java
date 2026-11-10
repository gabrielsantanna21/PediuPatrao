package com.umc.pediupatrao.config;
import com.umc.pediupatrao.entity.Usuario; import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner; import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration; import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration public class DataInitializer {
 @Bean CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder){return args->{if(repo.findByUsername("admin").isEmpty()){Usuario u=new Usuario();u.setUsername("admin");u.setPassword(encoder.encode(System.getenv().getOrDefault("PEDIUPATRAO_ADMIN_PASSWORD","trocar123")));u.setRole("ADMIN");repo.save(u);System.out.println("Usuário inicial admin criado. Altere PEDIUPATRAO_ADMIN_PASSWORD em produção.");}else{Usuario u=repo.findByUsername("admin").orElseThrow();if(u.getRole()==null||u.getRole().isBlank()){u.setRole("ADMIN");repo.save(u);}}};}
}
