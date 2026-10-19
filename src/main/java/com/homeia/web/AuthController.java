package com.homeia.web;

import com.homeia.persistence.UserEntity;
import com.homeia.service.ApiException;
import com.homeia.service.AuthService;
import com.homeia.service.PasswordPolicy;
import com.homeia.service.RegistrationRateLimiter;
import com.homeia.service.PasswordResetService;
import com.homeia.service.SetupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final SetupService setupService;
    private final RegistrationRateLimiter registrationLimiter;

    public AuthController(AuthService authService, PasswordResetService passwordResetService, SetupService setupService,
                          RegistrationRateLimiter registrationLimiter) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.setupService = setupService;
        this.registrationLimiter = registrationLimiter;
    }

    public record PasswordRuleDto(String id, String description) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RegisterBody(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 120, message = "El nombre admite hasta 120 caracteres") String fullName,
            @NotBlank(message = "El correo es obligatorio") @Size(max = 254, message = "El correo admite hasta 254 caracteres") String email,
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

    /** Reglas de contraseña vigentes, para mostrarlas en los formularios. */
    @GetMapping("/password-policy")
    public Map<String, Object> passwordPolicy() {
        List<PasswordRuleDto> rules = Arrays.stream(PasswordPolicy.Rule.values())
                .map(r -> new PasswordRuleDto(r.name(), r.description()))
                .toList();
        return Map.of("minLength", PasswordPolicy.MIN_LENGTH, "maxLength", PasswordPolicy.MAX_LENGTH, "rules", rules);
    }

    /**
     * Crea la cuenta con su propia casa. No inicia sesión: el usuario debe ingresar desde el login.
     * Limitado por IP para frenar la creación masiva de cuentas.
     */
    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterBody body, HttpServletRequest request) {
        if (!registrationLimiter.tryAcquire(request.getRemoteAddr())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Se hicieron demasiados intentos de registro desde esta red. Espera una hora e inténtalo de nuevo");
        }
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
