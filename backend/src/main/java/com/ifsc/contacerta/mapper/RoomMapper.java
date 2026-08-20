package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.dto.room.RoomResponse;
import com.ifsc.contacerta.dto.room.StudentRoomResponse;
import com.ifsc.contacerta.dto.room.TeacherRoomDetailResponse;
import com.ifsc.contacerta.dto.room.TeacherRoomSummaryResponse;
import com.ifsc.contacerta.entity.Room;
import com.ifsc.contacerta.model.MembershipStatus;

public final class RoomMapper {

	private RoomMapper() {
	}

	public static RoomResponse toResponse(Room room) {
		return new RoomResponse(
				room.getId(),
				room.getTeacher().getId(),
				room.getInstitution().getId(),
				room.getName(),
				room.getDescription(),
				room.getGrade(),
				room.getContentTopics(),
				room.getPassingScorePercent(),
				room.getJoinCodeDisplay(),
				room.getArchivedAt(),
				room.getCreatedAt(),
				room.getUpdatedAt(),
				room.getVersion()
		);
	}

	public static TeacherRoomSummaryResponse toTeacherSummaryResponse(Room room, long studentCount) {
		return new TeacherRoomSummaryResponse(
				room.getId(),
				room.getName(),
				room.getDescription(),
				room.getGrade(),
				room.getContentTopics(),
				room.getJoinCodeDisplay(),
				room.getPassingScorePercent(),
				room.getArchivedAt() != null,
				studentCount,
				0,
				room.getCreatedAt(),
				room.getUpdatedAt(),
				room.getVersion()
		);
	}

	public static TeacherRoomDetailResponse toTeacherDetailResponse(
			Room room,
			long studentCount,
			long membershipCount
	) {
		return new TeacherRoomDetailResponse(
				room.getId(),
				room.getName(),
				room.getDescription(),
				room.getGrade(),
				room.getContentTopics(),
				room.getJoinCodeDisplay(),
				room.getPassingScorePercent(),
				room.getArchivedAt() != null,
				studentCount,
				0,
				room.getCreatedAt(),
				room.getUpdatedAt(),
				room.getVersion(),
				InstitutionMapper.toSummaryResponse(room.getInstitution()),
				new TeacherRoomDetailResponse.TeacherReferenceResponse(
						room.getTeacher().getId(), room.getTeacher().getFullName()
				),
				membershipCount == 0
		);
	}

	public static StudentRoomResponse toStudentResponse(Room room, MembershipStatus membershipStatus) {
		return new StudentRoomResponse(
				room.getId(),
				room.getName(),
				room.getDescription(),
				room.getGrade(),
				room.getContentTopics(),
				new TeacherRoomDetailResponse.TeacherReferenceResponse(
						room.getTeacher().getId(), room.getTeacher().getFullName()
				),
				InstitutionMapper.toSummaryResponse(room.getInstitution()),
				membershipStatus,
				room.getArchivedAt() != null,
				0
		);
	}
}
