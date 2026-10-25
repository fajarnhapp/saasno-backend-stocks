package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class EmailTemplateTest {

	private static final String LINK = "https://app.example.com/reset-password?token=abc_DEF-123";

	private static EmailTemplate.Mail mail(String appName, String link, List<EmailTemplate.Detail> details) {
		return EmailTemplate.build(appName, "#7c3aed", "Subject", "Heading", "Intro text", details, "Open it", link, "A note");
	}

	@Test
	void htmlHasTheLinkInButtonAndFallbackText() {
		String html = mail("Saasno", LINK, List.of()).html();
		assertTrue(html.split(Pattern.quote(LINK), -1).length - 1 >= 3, "button, fallback text and link colour");
		assertTrue(html.contains("Open it"));
		assertTrue(html.startsWith("<!DOCTYPE html>"));
		assertTrue(html.trim().endsWith("</html>"));
	}

	@Test
	void everythingFromOutsideIsEscaped() {
		String html = mail("<b>Evil</b>", "https://x.test/?a=1&b=\"><script>alert(1)</script>",
				List.of(new EmailTemplate.Detail("Device", "Mozilla <script>alert(2)</script>"))).html();
		assertFalse(html.contains("<script>"));
		assertFalse(html.contains("<b>Evil</b>"));
		assertTrue(html.contains("&amp;b=&quot;&gt;&lt;script&gt;"));
	}

	@Test
	void noLinkMeansNoButton() {
		EmailTemplate.Mail mail = mail("Saasno", null, List.of(new EmailTemplate.Detail("IP address", "203.0.113.7")));
		assertFalse(mail.html().contains("<a href"));
		assertTrue(mail.html().contains("203.0.113.7"));
		assertTrue(mail.text().contains("IP address: 203.0.113.7"));
	}

	@Test
	void noLeftoverFormatPlaceholders() {
		assertFalse(Pattern.compile("%\\d\\$|%[sd]").matcher(mail("Saasno", LINK, List.of()).html()).find());
	}

	@Test
	void plainTextHasTheEssentials() {
		String text = mail("Saasno", LINK, List.of()).text();
		assertTrue(text.contains(LINK));
		assertTrue(text.contains("Heading"));
		assertTrue(text.contains("A note"));
		assertTrue(text.contains("Saasno"));
	}

}
