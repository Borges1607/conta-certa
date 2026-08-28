package com.ifsc.contacerta.service;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest;

@Component
public class IdempotencyHasher {
	public String hashStartScope() { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest("POST\n/student/room-lessons/{assignmentId}/attempts\n".getBytes(StandardCharsets.UTF_8)); StringBuilder result = new StringBuilder(); for (byte value : bytes) result.append(String.format("%02x", value)); return result.toString(); } catch (Exception exception) { throw new IllegalStateException(exception); } }
}
