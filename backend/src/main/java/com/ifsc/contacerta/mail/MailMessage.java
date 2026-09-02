package com.ifsc.contacerta.mail;

public record MailMessage(String recipient, String subject, String textBody, String htmlBody) {}
