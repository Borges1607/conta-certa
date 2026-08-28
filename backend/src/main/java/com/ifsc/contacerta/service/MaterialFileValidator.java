package com.ifsc.contacerta.service;

import com.ifsc.contacerta.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class MaterialFileValidator {

	public static final long MAX_BYTES = 10L * 1024 * 1024;
	private static final String PDF = "application/pdf";
	private static final String PPT = "application/vnd.ms-powerpoint";
	private static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
	private static final byte[] OLE_SIGNATURE = {
			(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
			(byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
	};

	public ValidatedMaterialFile validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw error(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_MEDIA", "A file is required.");
		}
		if (file.getSize() > MAX_BYTES) {
			throw error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds the 10 MiB limit.");
		}

		String fileName = safeFileName(file.getOriginalFilename());
		String extension = extension(fileName);
		String expectedContentType = expectedContentType(extension);
		if (!expectedContentType.equals(file.getContentType())) {
			throw unsupported();
		}

		byte[] content;
		try {
			content = file.getBytes();
		} catch (IOException exception) {
			throw error(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_MEDIA", "File could not be read.");
		}
		if (!hasExpectedSignature(extension, content)) {
			throw unsupported();
		}
		return new ValidatedMaterialFile(fileName, expectedContentType, content);
	}

	private String safeFileName(String original) {
		if (original == null) {
			throw unsupported();
		}
		String normalized = original.replace('\\', '/');
		normalized = normalized.substring(normalized.lastIndexOf('/') + 1)
				.replace("\r", "")
				.replace("\n", "")
				.trim();
		if (normalized.isEmpty() || normalized.length() > 255) {
			throw unsupported();
		}
		return normalized;
	}

	private String extension(String fileName) {
		int separator = fileName.lastIndexOf('.');
		return separator < 0 ? "" : fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
	}

	private String expectedContentType(String extension) {
		return switch (extension) {
			case "pdf" -> PDF;
			case "ppt" -> PPT;
			case "pptx" -> PPTX;
			default -> throw unsupported();
		};
	}

	private boolean hasExpectedSignature(String extension, byte[] content) {
		return switch (extension) {
			case "pdf" -> startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII));
			case "ppt" -> startsWith(content, OLE_SIGNATURE);
			case "pptx" -> isPresentationOoxml(content);
			default -> false;
		};
	}

	private boolean startsWith(byte[] content, byte[] signature) {
		return content.length >= signature.length
				&& Arrays.equals(Arrays.copyOf(content, signature.length), signature);
	}

	private boolean isPresentationOoxml(byte[] content) {
		Set<String> required = new java.util.HashSet<>(Set.of("[Content_Types].xml", "ppt/presentation.xml"));
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				required.remove(entry.getName());
				if (required.isEmpty()) {
					return true;
				}
			}
			return false;
		} catch (IOException exception) {
			return false;
		}
	}

	private ApiException unsupported() {
		return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Only PDF, PPT, and PPTX files are accepted.");
	}

	private ApiException error(HttpStatus status, String code, String message) {
		return new ApiException(status, code, message);
	}

	public record ValidatedMaterialFile(String fileName, String contentType, byte[] content) {
		public ValidatedMaterialFile {
			content = Arrays.copyOf(content, content.length);
		}

		@Override
		public byte[] content() {
			return Arrays.copyOf(content, content.length);
		}
	}
}
