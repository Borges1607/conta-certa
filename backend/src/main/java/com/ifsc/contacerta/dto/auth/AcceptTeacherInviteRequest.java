package com.ifsc.contacerta.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptTeacherInviteRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 72) String password) {}
