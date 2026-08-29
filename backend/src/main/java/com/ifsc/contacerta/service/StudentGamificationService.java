package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.gamification.AchievementCollectionResponse;
import com.ifsc.contacerta.dto.gamification.AchievementResponse;
import com.ifsc.contacerta.dto.gamification.RankingEntryResponse;
import com.ifsc.contacerta.dto.gamification.RankingResponse;
import com.ifsc.contacerta.entity.AchievementUnlock;
import com.ifsc.contacerta.entity.RoomMembership;
import com.ifsc.contacerta.entity.RoomStudentProgress;
import com.ifsc.contacerta.entity.User;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.model.AccountStatus;
import com.ifsc.contacerta.model.AchievementCode;
import com.ifsc.contacerta.model.MembershipStatus;
import com.ifsc.contacerta.model.Role;
import com.ifsc.contacerta.repository.AchievementUnlockRepository;
import com.ifsc.contacerta.repository.RankingRepository;
import com.ifsc.contacerta.repository.RankingRowProjection;
import com.ifsc.contacerta.repository.RoomMembershipRepository;
import com.ifsc.contacerta.repository.RoomStudentProgressRepository;
import com.ifsc.contacerta.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ifsc.contacerta.model.AchievementCode.FIRST_PASS;
import static com.ifsc.contacerta.model.AchievementCode.PASSED_10;
import static com.ifsc.contacerta.model.AchievementCode.PASSED_5;
import static com.ifsc.contacerta.model.AchievementCode.PERFECT_SCORE;
import static com.ifsc.contacerta.model.AchievementCode.XP_100;
import static com.ifsc.contacerta.model.AchievementCode.XP_1000;
import static com.ifsc.contacerta.model.AchievementCode.XP_500;

@Service
@RequiredArgsConstructor
public class StudentGamificationService {

	private static final List<AchievementDefinition> DEFINITIONS = List.of(
			new AchievementDefinition(FIRST_PASS, "Primeira aprovação", "Aprove uma lição nesta sala.", 1, ProgressKind.PASSES),
			new AchievementDefinition(PERFECT_SCORE, "Nota perfeita", "Obtenha 100% em uma tentativa.", 1, ProgressKind.UNLOCK),
			new AchievementDefinition(XP_100, "100 XP", "Conquiste 100 XP nesta sala.", 100, ProgressKind.XP),
			new AchievementDefinition(XP_500, "500 XP", "Conquiste 500 XP nesta sala.", 500, ProgressKind.XP),
			new AchievementDefinition(XP_1000, "1.000 XP", "Conquiste 1.000 XP nesta sala.", 1_000, ProgressKind.XP),
			new AchievementDefinition(PASSED_5, "Cinco aprovações", "Aprove cinco lições nesta sala.", 5, ProgressKind.PASSES),
			new AchievementDefinition(PASSED_10, "Dez aprovações", "Aprove dez lições nesta sala.", 10, ProgressKind.PASSES)
	);

	private final UserRepository userRepository;
	private final RoomMembershipRepository membershipRepository;
	private final RoomStudentProgressRepository progressRepository;
	private final RankingRepository rankingRepository;
	private final AchievementUnlockRepository unlockRepository;

	@Transactional(readOnly = true)
	public RankingResponse ranking(UUID studentId, UUID roomId, int page, int size) {
		requireAccess(studentId, roomId);
		Page<RankingRowProjection> ranking = rankingRepository.findPage(roomId, PageRequest.of(page, size));
		List<RankingEntryResponse> content = ranking.getContent().stream()
				.map(row -> toEntry(row, studentId))
				.toList();
		RankingEntryResponse self = rankingRepository.findStudent(roomId, studentId)
				.map(row -> toEntry(row, studentId))
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room was not found."));
		return new RankingResponse(
				content, self, ranking.getNumber(), ranking.getSize(), ranking.getTotalElements(), ranking.getTotalPages()
		);
	}

	@Transactional(readOnly = true)
	public AchievementCollectionResponse achievements(UUID studentId, UUID roomId) {
		requireAccess(studentId, roomId);
		RoomStudentProgress progress = progressRepository.findByRoomIdAndStudentId(roomId, studentId).orElse(null);
		Map<AchievementCode, Instant> unlockedAt = new EnumMap<>(AchievementCode.class);
		for (AchievementUnlock unlock : unlockRepository.findByRoomIdAndStudentId(roomId, studentId)) {
			unlockedAt.put(unlock.getCode(), unlock.getUnlockedAt());
		}
		List<AchievementResponse> content = DEFINITIONS.stream()
				.map(definition -> toAchievement(definition, progress, unlockedAt))
				.toList();
		return new AchievementCollectionResponse(content);
	}

	private void requireAccess(UUID studentId, UUID roomId) {
		User student = userRepository.findById(studentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student was not found."));
		if (student.getRole() != Role.STUDENT) {
			throw new ApiException(HttpStatus.FORBIDDEN, "STUDENT_REQUIRED", "A student account is required.");
		}
		if (student.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Student account is inactive.");
		}
		RoomMembership membership = membershipRepository.findByRoomIdAndStudentId(roomId, studentId)
				.filter(candidate -> candidate.getStatus() == MembershipStatus.ACTIVE)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room was not found."));
		if (!membership.getRoom().getInstitution().getId().equals(student.getInstitution().getId())) {
			throw new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room was not found.");
		}
	}

	private RankingEntryResponse toEntry(RankingRowProjection row, UUID studentId) {
		return new RankingEntryResponse(
				row.getPosition(), row.getStudentId(), anonymize(row.getFullName()), row.getTotalXp(),
				row.getTotalStars(), row.getLevel(), row.getStudentId().equals(studentId)
		);
	}

	private AchievementResponse toAchievement(
			AchievementDefinition definition,
			RoomStudentProgress progress,
			Map<AchievementCode, Instant> unlockedAt
	) {
		Instant instant = unlockedAt.get(definition.code());
		int source = switch (definition.progressKind()) {
			case XP -> progress == null ? 0 : progress.getTotalXp();
			case PASSES -> progress == null ? 0 : progress.getPassedAssignmentCount();
			case UNLOCK -> instant == null ? 0 : 1;
		};
		return new AchievementResponse(
				definition.code(), definition.title(), definition.description(), Math.min(source, definition.target()),
				definition.target(), instant != null, instant
		);
	}

	private String anonymize(String fullName) {
		String[] parts = Arrays.stream(fullName.trim().split("\\s+"))
				.filter(part -> !part.isBlank())
				.toArray(String[]::new);
		if (parts.length <= 1) {
			return parts.length == 0 ? "" : parts[0];
		}
		return parts[0] + " " + parts[parts.length - 1].substring(0, 1) + ".";
	}

	private enum ProgressKind { XP, PASSES, UNLOCK }

	private record AchievementDefinition(
			AchievementCode code,
			String title,
			String description,
			int target,
			ProgressKind progressKind
	) {}
}
