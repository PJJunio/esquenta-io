package com.pjjunio.esquentaio.service;

import com.pjjunio.esquentaio.entity.Admin;
import com.pjjunio.esquentaio.repository.AdminRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializerService {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializerService.class);

    private final AdminRepository adminRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${admin.username}")
    private String username;

    @Value("${admin.password}")
    private String password;

    public AdminInitializerService(AdminRepository adminRepository, BCryptPasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        var existing = adminRepository.findByUsername(username);
        if (existing.isEmpty()) {
            Admin admin = new Admin(username, passwordEncoder.encode(password));
            adminRepository.save(admin);
            log.info("Admin '{}' criado com sucesso.", username);
        } else {
            Admin admin = existing.get();
            if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
                admin.setPasswordHash(passwordEncoder.encode(password));
                adminRepository.save(admin);
                log.info("Senha do admin '{}' atualizada.", username);
            } else {
                log.info("Admin '{}' já existe e senha está correta.", username);
            }
        }
    }
}
