package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.entity.StoredFile;
import com.ifsc.contacerta.model.FileDownload;
import org.springframework.stereotype.Component;

@Component
public class FileDownloadMapper {

	public FileDownload toDownload(StoredFile file) {
		return new FileDownload(file.getFileName(), file.getContentType(), file.getSizeBytes(), file.getContent());
	}
}
