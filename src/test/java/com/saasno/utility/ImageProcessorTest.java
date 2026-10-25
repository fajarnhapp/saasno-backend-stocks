package com.saasno.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.saasno.utility.exception.ApiException;

class ImageProcessorTest {

	private static byte[] image(int width, int height, String format) throws Exception {
		BufferedImage image = new BufferedImage(width, height, format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	private static BufferedImage decode(byte[] png) throws Exception {
		return ImageIO.read(new ByteArrayInputStream(png));
	}

	@Test
	void squareCropsToTheCentreAndScales() throws Exception {
		BufferedImage result = decode(ImageProcessor.square(image(600, 300, "png"), 256));
		assertEquals(256, result.getWidth());
		assertEquals(256, result.getHeight());
	}

	@Test
	void acceptsJpegAndAlwaysReturnsPng() throws Exception {
		byte[] out = ImageProcessor.square(image(100, 100, "jpg"), 64);
		assertEquals((byte) 0x89, out[0]);
		assertEquals('P', out[1]);
		assertEquals(64, decode(out).getWidth());
	}

	@Test
	void fitKeepsTheAspectRatioAndNeverEnlarges() throws Exception {
		BufferedImage big = decode(ImageProcessor.fit(image(1000, 500, "png"), 512));
		assertEquals(512, big.getWidth());
		assertEquals(256, big.getHeight());

		BufferedImage small = decode(ImageProcessor.fit(image(40, 20, "png"), 512));
		assertEquals(40, small.getWidth());
		assertEquals(20, small.getHeight());
	}

	@Test
	void refusesSvgAndOtherTypes() {
		byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
		assertThrows(ApiException.class, () -> ImageProcessor.square(svg, 64));
		byte[] html = "<html><body>hi there, not a picture</body></html>".getBytes(StandardCharsets.UTF_8);
		assertThrows(ApiException.class, () -> ImageProcessor.fit(html, 64));
		assertThrows(ApiException.class, () -> ImageProcessor.fit(null, 64));
		assertThrows(ApiException.class, () -> ImageProcessor.fit(new byte[3], 64));
	}

	@Test
	void refusesAFileThatOnlyPretendsToBeAPicture() {
		// PNG signature followed by garbage
		byte[] fake = new byte[64];
		fake[0] = (byte) 0x89;
		fake[1] = 'P';
		fake[2] = 'N';
		fake[3] = 'G';
		assertThrows(ApiException.class, () -> ImageProcessor.square(fake, 64));
	}

	@Test
	void theOutputCarriesNoTrailingPayloadFromTheUpload() throws Exception {
		byte[] png = image(20, 20, "png");
		byte[] polyglot = new byte[png.length + 20];
		System.arraycopy(png, 0, polyglot, 0, png.length);
		byte[] payload = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
		System.arraycopy(payload, 0, polyglot, png.length, Math.min(payload.length, 20));
		byte[] out = ImageProcessor.fit(polyglot, 64);
		assertTrue(!new String(out, StandardCharsets.ISO_8859_1).contains("<script"), "re-encoded output has no hidden payload");
	}

}
