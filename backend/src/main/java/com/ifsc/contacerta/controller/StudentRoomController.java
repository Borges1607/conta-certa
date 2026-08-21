package com.ifsc.contacerta.controller;

import com.ifsc.contacerta.dto.room.JoinRoomRequest;
import com.ifsc.contacerta.dto.room.StudentRoomResponse;
import com.ifsc.contacerta.security.CurrentUser;
import com.ifsc.contacerta.service.RoomMembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/student/rooms")
@RequiredArgsConstructor
public class StudentRoomController {

	private final RoomMembershipService membershipService;

	@GetMapping
	public List<StudentRoomResponse> list(@AuthenticationPrincipal CurrentUser currentUser) {
		return membershipService.listStudentRooms(currentUser.userId());
	}

	@PostMapping("/join")
	public StudentRoomResponse join(
			@AuthenticationPrincipal CurrentUser currentUser,
			@Valid @RequestBody JoinRoomRequest request
	) {
		return membershipService.join(currentUser.userId(), request.code());
	}
}
