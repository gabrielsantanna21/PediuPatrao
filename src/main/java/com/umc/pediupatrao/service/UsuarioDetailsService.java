package com.umc.pediupatrao.service;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
@Service public class UsuarioDetailsService implements UserDetailsService {
 private final UsuarioRepository repo; public UsuarioDetailsService(UsuarioRepository repo){this.repo=repo;}
 @Override public UserDetails loadUserByUsername(String username)throws UsernameNotFoundException{return repo.findByUsername(username).map(u->{String role=u.getRole()==null?"ATENDENTE":u.getRole().toUpperCase().replace("ROLE_",""); if(!java.util.Set.of("ADMIN","GERENTE","ATENDENTE").contains(role)) role="ATENDENTE"; return User.withUsername(u.getUsername()).password(u.getPassword()).roles(role).build();}).orElseThrow(()->new UsernameNotFoundException("Usuário não encontrado"));}
}
