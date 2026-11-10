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
   // Dashboard administrativo: somente leitura, para quem responde pelo estabelecimento.
   .requestMatchers(HttpMethod.GET,"/api/dashboard/**","/dashboard").hasAnyRole("ADMIN","GERENTE")
   .requestMatchers("/api/dashboard/**").denyAll()
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
   // APIs respondem 401/403 em JSON (em vez de redirecionar para a tela de login), facilitando a integração.
   .exceptionHandling(e->e
    .defaultAuthenticationEntryPointFor((req,res,ex)->jsonErro(req,res,401,"Não autenticado. Faça login para acessar a API."),SecurityConfig::isApi)
    .defaultAccessDeniedHandlerFor((req,res,ex)->jsonErro(req,res,403,"Acesso negado para o seu perfil."),SecurityConfig::isApi))
   .formLogin(f->f.loginPage("/login").permitAll().defaultSuccessUrl("/",true))
   .logout(l->l.logoutUrl("/logout").logoutSuccessUrl("/login?logout").permitAll());
  return http.build();
 }
 private static boolean isApi(jakarta.servlet.http.HttpServletRequest req){return req.getRequestURI().startsWith(req.getContextPath()+"/api/");}
 private static void jsonErro(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res,int status,String msg)throws java.io.IOException{
  res.setStatus(status);res.setContentType("application/json;charset=UTF-8");
  String caminho=req.getRequestURI().replace("\\","\\\\").replace("\"","\\\"");
  res.getWriter().write("{\"erro\":\""+msg+"\",\"status\":"+status+",\"caminho\":\""+caminho+"\",\"timestamp\":\""+java.time.Instant.now()+"\"}");
 }
}
