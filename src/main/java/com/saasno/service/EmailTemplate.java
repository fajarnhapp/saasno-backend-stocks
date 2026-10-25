package com.saasno.service;

import org.springframework.web.util.HtmlUtils;

import java.util.List;

// Shared look of the emails the app sends: HTML built with inline styles and tables only (mail clients ignore <style> and modern
// layout), plus a plain-text twin for clients that do not show HTML.
final class EmailTemplate {

	// Subject and the two bodies of one email.
	record Mail(String subject, String text, String html) {
	}

	// A label/value row shown under the intro (for example "Time", "IP address").
	record Detail(String label, String value) {
	}

	private EmailTemplate() {
	}

	static Mail build(String appName, String color, String subject, String heading, String intro, List<Detail> details,
			String buttonLabel, String link, String note) {
		return new Mail(subject, text(appName, heading, intro, details, buttonLabel, link, note),
				html(appName, color, subject, heading, intro, details, buttonLabel, link, note));
	}

	static String text(String appName, String heading, String intro, List<Detail> details, String buttonLabel, String link, String note) {
		StringBuilder text = new StringBuilder(heading).append("\n\n").append(intro).append("\n\n");
		for (Detail detail : details) {
			text.append(detail.label()).append(": ").append(detail.value()).append('\n');
		}
		if (!details.isEmpty()) {
			text.append('\n');
		}
		if (link != null) {
			text.append(buttonLabel).append(":\n").append(link).append("\n\n");
		}
		return text.append(note).append("\n\n— ").append(appName).append('\n').toString();
	}

	static String html(String appName, String color, String title, String heading, String intro, List<Detail> details, String buttonLabel,
			String link, String note) {
		StringBuilder rows = new StringBuilder();
		for (Detail detail : details) {
			rows.append("<tr><td style=\"padding:6px 12px 6px 0;font-size:13px;color:#6b7280;white-space:nowrap;vertical-align:top;\">")
					.append(esc(detail.label()))
					.append("</td><td style=\"padding:6px 0;font-size:13px;color:#111827;word-break:break-word;\">")
					.append(esc(detail.value())).append("</td></tr>");
		}
		String detailTable = details.isEmpty() ? ""
				: "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 24px;\">" + rows + "</table>";

		String button = link == null ? "" : """
				<table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto 24px;"><tr><td align="center" style="border-radius:8px;background-color:%1$s;">
				<a href="%2$s" target="_blank" style="display:inline-block;padding:14px 32px;font-size:16px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:8px;">%3$s</a>
				</td></tr></table>
				<p style="margin:0 0 8px;font-size:13px;line-height:20px;color:#6b7280;">Button not working? Copy this address into your browser:</p>
				<p style="margin:0 0 20px;font-size:12px;line-height:18px;word-break:break-all;"><a href="%2$s" target="_blank" style="color:%1$s;">%2$s</a></p>
				""".formatted(color, esc(link), esc(buttonLabel));

		return """
				<!DOCTYPE html>
				<html lang="en">
				<head>
				<meta charset="UTF-8">
				<meta name="viewport" content="width=device-width, initial-scale=1.0">
				<title>%1$s</title>
				</head>
				<body style="margin:0;padding:0;background-color:#f3f4f6;">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f3f4f6;padding:32px 16px;">
				<tr><td align="center">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background-color:#ffffff;border-radius:12px;overflow:hidden;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
				<tr><td style="background-color:%2$s;padding:20px 32px;color:#ffffff;font-size:20px;font-weight:700;letter-spacing:0.3px;">%3$s</td></tr>
				<tr><td style="padding:32px;color:#111827;">
				<h1 style="margin:0 0 12px;font-size:22px;line-height:28px;color:#111827;">%4$s</h1>
				<p style="margin:0 0 24px;font-size:15px;line-height:24px;color:#4b5563;">%5$s</p>
				%6$s%7$s<hr style="border:0;border-top:1px solid #e5e7eb;margin:0 0 20px;">
				<p style="margin:0;font-size:13px;line-height:20px;color:#6b7280;">%8$s</p>
				</td></tr>
				<tr><td style="background-color:#f9fafb;padding:16px 32px;font-size:12px;line-height:18px;color:#9ca3af;text-align:center;">This is an automatic message, please do not reply.</td></tr>
				</table>
				</td></tr>
				</table>
				</body>
				</html>
				""".formatted(esc(title), color, esc(appName), esc(heading), esc(intro), detailTable, button, esc(note));
	}

	private static String esc(String value) {
		return HtmlUtils.htmlEscape(value == null ? "" : value);
	}

}
