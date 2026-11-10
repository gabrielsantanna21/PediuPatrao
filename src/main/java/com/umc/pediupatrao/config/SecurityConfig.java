package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration @EnableWebSecurity
public class SecurityConfig {
 private final UsuarioDetailsService details;
 public SecurityConfig(UsuarioDetailsService details){this.details=details;}
 @Bean public PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean public AuthenticationProvider authenticationProvider(){DaoAuthenticationProvider p=new DaoAuthenticationProvider();p.setUserDetailsService(details);p.setPasswordEncoder(passwordEncoder());return p;}
 @Bean public AuthenticationManager authenticationManager(AuthenticationConfiguration c)throws Exception{return c.getAuthenticationManager();}
 @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http)throws Exception{
  http.authorizeHttpRequests(a->a
   .requestMatchers("/login","/css/**","/dist/**","/plugins/**","/js/**","/images/**").permitAll()
   .requestMatchers("/api/usuarios/**","/usuarios/**","/admin","/configuracoes").hasRole("ADMIN")
   .requestMatchers(HttpMethod.POST,"/api/auditoria/**").denyAll()
   .requestMatchers(HttpMethod.PUT,"/api/auditoria/**").denyAll()
   .requestMatchers(HttpMethod.PATCH,"/api/auditoria/**").denyAll()
   .requestMatchers(HttpMethod.DELETE,"/api/auditoria/**").denyAll()
   .requestMatchers("/api/auditoria/**","/auditoria/**").hasAnyRole("ADMIN","GERENTE")
   .requestMatchers(HttpMethod.POST,"/api/pedidos/*/desconto","/api/pedidos/*/cancelar").hasRole("GERENTE")
   .requestMatchers(HttpMethod.PUT,"/api/pedidos/*/desconto","/api/pedidos/*/cancelar").hasRole("GERENTE")
   .requestMatchers(HttpMethod.POST,"/api/pedidos/**").hasAnyRole("GERENTE","ATENDENTE")
   .requestMatchers(HttpMethod.PUT,"/api/pedidos/**").hasAnyRole("GERENTE","ATENDENTE")
   .requestMatchers(HttpMethod.PATCH,"/api/pedidos/**").hasAnyRole("GERENTE","ATENDENTE")
   .requestMatchers(HttpMethod.DELETE,"/api/pedidos/**").hasRole("GERENTE")
   .requestMatchers(HttpMethod.DELETE,"/api/clientes/**").denyAll()
   .requestMatchers(HttpMethod.POST,"/api/clientes/**").hasRole("ATENDENTE")
   .requestMatchers(HttpMethod.PUT,"/api/clientes/**").hasRole("ATENDENTE")
   .requestMatchers("/api/clientes/**").hasAnyRole("ADMIN","GERENTE","ATENDENTE")
   .requestMatchers("/api/pedidos/**","/pedidos").hasAnyRole("ADMIN","GERENTE","ATENDENTE")
   .requestMatchers(HttpMethod.POST,"/clientes/**").hasRole("ATENDENTE")
   .requestMatchers(HttpMethod.PUT,"/clientes/**").hasRole("ATENDENTE")
   .requestMatchers("/clientes/**").hasAnyRole("ADMIN","GERENTE","ATENDENTE")
   .anyRequest().authenticated())
   // CSRF permanece habilitado para formulários e sessões web; chamadas mutáveis devem enviar token CSRF.
   .formLogin(f->f.loginPage("/login").permitAll().defaultSuccessUrl("/",true))
   .logout(l->l.logoutUrl("/logout").logoutSuccessUrl("/login?logout").permitAll());
  return http.build();
 }
}
