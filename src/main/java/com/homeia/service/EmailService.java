package com.homeia.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final boolean smtpConfigured;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${homeia.mail.from}") String from,
                        @Value("${spring.mail.host:}") String host) {
        this.mailSender = mailSender;
        this.from = from;
        this.smtpConfigured = host != null && !host.isBlank();
    }

    public void sendPasswordReset(String to, String fullName, String link, long minutesValid) {
        String body = """
                Hola %s,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta de HomeIA.
                Para elegir una nueva contraseña, abre este enlace:

                %s

                El enlace caduca en %d minutos y solo puede usarse una vez.
                Si no fuiste tú, ignora este correo: tu contraseña no cambiará.
                """.formatted(fullName, link, minutesValid);
        send(to, "Restablecer tu contraseña de HomeIA", body);
    }

    private void send(String to, String subject, String body) {
        if (!smtpConfigured) {
            throw new IllegalStateException("SMTP no configurado (define SMTP_HOST en .env)");
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("No hay un servicio de correo disponible");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
        log.info("Correo '{}' enviado a {}", subject, to);
    }
}
