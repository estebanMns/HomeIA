package com.homeia.web;

import com.homeia.persistence.UserEntity;
import com.homeia.service.AuthService;
import com.homeia.service.PasswordResetService;
import com.homeia.service.SetupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final SetupService setupService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService, SetupService setupService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.setupService = setupService;
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RegisterBody(
            @NotBlank(message = "El nombre es obligatorio") String fullName,
            @NotBlank(message = "El correo es obligatorio") String email,
            @NotBlank(message = "La contraseña es obligatoria") String password) {
    }

    public record TokenRequest(@NotBlank String refreshToken) {
    }

    public record ForgotBody(@NotBlank String email) {
    }

    public record ResetBody(@NotBlank String token, @NotBlank String newPassword) {
    }

    /** Siempre responde igual, exista o no el correo, para no revelar qué cuentas hay. */
    @PostMapping("/password/forgot")
    public Map<String, Object> forgot(@RequestBody ForgotBody body) {
        passwordResetService.requestReset(body.email());
        return Map.of("message", "Si el correo está registrado, recibirás un enlace para restablecer la contraseña");
    }

    @PostMapping("/password/reset")
    public Map<String, Object> reset(@RequestBody ResetBody body) {
        passwordResetService.confirmReset(body.token(), body.newPassword());
        return Map.of("success", true);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        return session(authService.login(request.email(), request.password()));
    }

    /** Crea la cuenta con su propia casa. No inicia sesión: el usuario debe ingresar desde el login. */
    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterBody body) {
        setupService.register(body.fullName(), body.email(), body.password());
        return Map.of("message", "Cuenta creada. Ya puedes ingresar con tu correo y contraseña.");
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(@RequestBody TokenRequest request) {
        return session(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestBody TokenRequest request) {
        authService.logout(request.refreshToken());
        return Map.of("success", true);
    }

    private static Map<String, Object> session(AuthService.Session session) {
        UserEntity user = session.user();
        return Map.of(
                "accessToken", session.accessToken(),
                "refreshToken", session.refreshToken(),
                "expiresIn", session.expiresInSeconds(),
                "user", UserDto.from(user));
    }
}
