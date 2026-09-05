package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.model.FileDownload;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class FileDownloadResponseMapper {

	public ResponseEntity<byte[]> toResponse(FileDownload download) {
		String disposition = "application/pdf".equals(download.contentType()) ? "inline" : "attachment";
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(download.contentType()))
				.contentLength(download.sizeBytes())
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder(disposition)
						.filename(safeFileName(download), StandardCharsets.UTF_8).build().toString())
				.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
				.header("X-Content-Type-Options", "nosniff")
				.body(download.content());
	}

	private String safeFileName(FileDownload download) {
		String normalized = download.fileName().replace('\\', '/');
		String basename = normalized.substring(normalized.lastIndexOf('/') + 1);
		StringBuilder name = new StringBuilder();
		basename.codePoints().filter(codePoint -> !Character.isISOControl(codePoint)).forEach(name::appendCodePoint);
		String safeName = name.toString().strip();
		if (!safeName.isEmpty()) {
			return safeName;
		}
		return switch (download.contentType()) {
			case "application/pdf" -> "arquivo.pdf";
			case "application/vnd.ms-powerpoint" -> "arquivo.ppt";
			case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "arquivo.pptx";
			default -> throw new IllegalStateException("Stored file has an unsupported content type.");
		};
	}
}
