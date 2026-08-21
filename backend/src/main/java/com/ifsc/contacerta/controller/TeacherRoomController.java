package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.room.CreateRoomRequest;
import com.ifsc.contacerta.dto.room.DuplicateRoomRequest;
import com.ifsc.contacerta.dto.room.RoomStudentResponse;
import com.ifsc.contacerta.dto.room.TeacherRoomDetailResponse;
import com.ifsc.contacerta.dto.room.TeacherRoomSummaryResponse;
import com.ifsc.contacerta.dto.room.UpdateRoomRequest;
import com.ifsc.contacerta.dto.shared.PageResponse;
import com.ifsc.contacerta.exception.ApiException;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.RoomMembershipService;
import com.ifsc.contacerta.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/teacher/rooms")
@RequiredArgsConstructor
public class TeacherRoomController {

	private static final Set<String> ROOM_SORT_FIELDS = Set.of("name", "createdAt", "updatedAt");

	private final RoomService roomService;
	private final RoomMembershipService membershipService;

	@GetMapping
	public PageResponse<TeacherRoomSummaryResponse> list(
			@AuthenticationPrincipal CurrentUser currentUser,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Boolean archived,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt,desc") String sort
	) {
		return roomService.list(currentUser.userId(), search, archived, roomPageable(page, size, sort));
	}

	@PostMapping
	public ResponseEntity<TeacherRoomDetailResponse> create(
			@AuthenticationPrincipal CurrentUser currentUser,
			@Valid @RequestBody CreateRoomRequest request
	) {
		TeacherRoomDetailResponse response = roomService.create(currentUser.userId(), request);
		return ResponseEntity.created(URI.create("/teacher/rooms/" + response.id())).body(response);
	}

	@GetMapping("/{roomId}")
	public TeacherRoomDetailResponse get(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId
	) {
		return roomService.get(currentUser.userId(), roomId);
	}

	@PatchMapping("/{roomId}")
	public TeacherRoomDetailResponse update(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@Valid @RequestBody UpdateRoomRequest request
	) {
		return roomService.update(currentUser.userId(), roomId, request);
	}

	@PostMapping("/{roomId}/archive")
	public TeacherRoomDetailResponse archive(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId
	) {
		return roomService.archive(currentUser.userId(), roomId);
	}

	@DeleteMapping("/{roomId}")
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId
	) {
		roomService.delete(currentUser.userId(), roomId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{roomId}/duplicate")
	public TeacherRoomDetailResponse duplicate(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@Valid @RequestBody(required = false) DuplicateRoomRequest request
	) {
		return roomService.duplicate(currentUser.userId(), roomId, request == null ? new DuplicateRoomRequest(null) : request);
	}

	@PostMapping("/{roomId}/regenerate-code")
	public TeacherRoomDetailResponse regenerateCode(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId
	) {
		return roomService.regenerateCode(currentUser.userId(), roomId);
	}

	@GetMapping("/{roomId}/students")
	public PageResponse<RoomStudentResponse> listStudents(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return membershipService.listRoomStudents(currentUser.userId(), roomId, search, PageRequest.of(page, size));
	}

	@DeleteMapping("/{roomId}/students/{studentId}")
	public ResponseEntity<Void> removeStudent(
			@AuthenticationPrincipal CurrentUser currentUser,
			@PathVariable UUID roomId,
			@PathVariable UUID studentId
	) {
		membershipService.remove(currentUser.userId(), roomId, studentId);
		return ResponseEntity.noContent().build();
	}

	private Pageable roomPageable(int page, int size, String sort) {
		if (page < 0 || size < 1 || size > 100) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_PAGE", "Pagination values are invalid.");
		}
		String[] parts = sort.split(",", -1);
		if (parts.length > 2 || parts[0].isBlank() || !ROOM_SORT_FIELDS.contains(parts[0])) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ROOM_SORT", "Room sort is invalid.");
		}
		Sort.Direction direction = parts.length == 1
				? Sort.Direction.ASC
				: Sort.Direction.fromOptionalString(parts[1]).orElseThrow(() ->
						new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ROOM_SORT", "Room sort is invalid.")
				);
		return PageRequest.of(page, size, Sort.by(direction, parts[0]));
	}
}
