package com.ifsc.contacerta.service;

import com.ifsc.contacerta.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialFileValidatorTest {

	private final MaterialFileValidator validator = new MaterialFileValidator();

	@Test
	void deveAceitarPdfPelaAssinaturaReal() {
		byte[] content = "%PDF-1.7\nconteudo".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

		MaterialFileValidator.ValidatedMaterialFile result = validator.validate(file(
				"aula.pdf", "application/pdf", content
		));

		assertThat(result.fileName()).isEqualTo("aula.pdf");
		assertThat(result.contentType()).isEqualTo("application/pdf");
		assertThat(result.content()).containsExactly(content);
	}

	@Test
	void deveAceitarPptPelaAssinaturaOle() {
		byte[] content = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

		assertThat(validator.validate(file("aula.ppt", "application/vnd.ms-powerpoint", content)).contentType())
				.isEqualTo("application/vnd.ms-powerpoint");
	}

	@Test
	void deveAceitarPptxComEstruturaOoxml() throws IOException {
		byte[] content = pptx();

		assertThat(validator.validate(file(
				"aula.pptx",
				"application/vnd.openxmlformats-officedocument.presentationml.presentation",
				content
		)).contentType()).isEqualTo(
				"application/vnd.openxmlformats-officedocument.presentationml.presentation"
		);
	}

	@Test
	void deveRejeitarArquivoAcimaDeDezMib() {
		byte[] oversized = new byte[(10 * 1024 * 1024) + 1];

		assertThatThrownBy(() -> validator.validate(file("aula.pdf", "application/pdf", oversized)))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
					assertThat(error.getCode()).isEqualTo("FILE_TOO_LARGE");
				});
	}

	@Test
	void deveRejeitarExtensaoMimeOuAssinaturaIncompativeis() {
		assertThatThrownBy(() -> validator.validate(file(
				"aula.pdf", "application/pdf", "nao-e-pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8)
		)))
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
					assertThat(error.getCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
				});
	}

	private MockMultipartFile file(String name, String contentType, byte[] content) {
		return new MockMultipartFile("file", name, contentType, content);
	}

	private byte[] pptx() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
			zip.write("<Types/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("ppt/presentation.xml"));
			zip.write("<presentation/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return bytes.toByteArray();
	}
}
