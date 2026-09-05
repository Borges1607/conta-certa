package com.ifsc.contacerta.model;

public record FileDownload(String fileName, String contentType, long sizeBytes, byte[] content) { }
