package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.gamification.AchievementCollectionResponse;
import com.ifsc.contacerta.dto.gamification.RankingResponse;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.StudentGamificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/student/rooms/{roomId}")
@RequiredArgsConstructor
@Validated
public class StudentGamificationController {

	private final StudentGamificationService gamificationService;

	@GetMapping("/ranking")
	public RankingResponse ranking(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return gamificationService.ranking(currentUser.userId(), roomId, page, size);
	}

	@GetMapping("/achievements")
	public AchievementCollectionResponse achievements(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId
	) {
		return gamificationService.achievements(currentUser.userId(), roomId);
	}
}
