package com.homeia.web;

import com.homeia.domain.UserRole;
import com.homeia.security.AuthPrincipal;
import com.homeia.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public record CreateUserBody(@NotBlank String email, @NotBlank String password, @NotBlank String fullName,
                                 UserRole role) {
    }

    public record UpdateUserBody(String fullName, UserRole role, Boolean active, String password) {
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthPrincipal principal) {
        return UserDto.from(userService.get(principal.homeId(), principal.userId()));
    }

    public record PasswordBody(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    @PutMapping("/me/password")
    public java.util.Map<String, Object> changeMyPassword(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @Valid @RequestBody PasswordBody body) {
        userService.changeOwnPassword(principal, body.currentPassword(), body.newPassword());
        return java.util.Map.of("success", true);
    }

    @GetMapping
    public List<UserDto> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.list(principal.homeId()).stream().map(UserDto::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto create(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody CreateUserBody body) {
        return UserDto.from(userService.create(principal, body.email(), body.password(), body.fullName(), body.role()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id,
                          @RequestBody UpdateUserBody body) {
        return UserDto.from(userService.update(principal, id, body.fullName(), body.role(), body.active(), body.password()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        userService.delete(principal, id);
    }
}
