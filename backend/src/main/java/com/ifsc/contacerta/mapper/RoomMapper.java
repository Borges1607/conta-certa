package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.dto.room.RoomResponse;
import com.ifsc.contacerta.entity.Room;

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
				room.getJoinCode(),
				room.getArchivedAt(),
				room.getCreatedAt(),
				room.getUpdatedAt(),
				room.getVersion()
		);
	}
}
