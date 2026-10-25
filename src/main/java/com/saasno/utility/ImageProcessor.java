package com.saasno.utility;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.saasno.utility.exception.ApiException;

// Turns an uploaded picture into a safe PNG: the type is decided from the file's own bytes (never from its name or declared type), the
// picture is decoded and drawn again (which drops metadata and anything hidden in the file), and its size is limited.
public final class ImageProcessor {

	// Pixels (width x height) accepted before decoding, so a small file cannot expand into gigabytes of memory.
	private static final long MAX_PIXELS = 25_000_000L;

	private ImageProcessor() {
	}

	// Square picture, cropped from the centre (profile photos).
	public static byte[] square(byte[] upload, int size) {
		BufferedImage source = decode(upload);
		int side = Math.min(source.getWidth(), source.getHeight());
		BufferedImage cropped = source.getSubimage((source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);
		return png(scale(cropped, size, size));
	}

	// Whole picture, shrunk to fit inside maxSide x maxSide, transparency kept (logos).
	public static byte[] fit(byte[] upload, int maxSide) {
		BufferedImage source = decode(upload);
		double ratio = Math.min(1.0, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
		int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
		int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
		return png(scale(source, width, height));
	}

	// PNG and JPEG only. SVG is refused on purpose: it can carry scripts.
	static boolean isSupported(byte[] bytes) {
		if (bytes == null || bytes.length < 12) {
			return false;
		}
		boolean png = (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
		boolean jpeg = (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
		return png || jpeg;
	}

	private static BufferedImage decode(byte[] upload) {
		if (!isSupported(upload)) {
			throw new ApiException("400", "Only PNG and JPEG pictures are accepted");
		}
		try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if (!readers.hasNext()) {
				throw new ApiException("400", "The file is not a valid picture");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(stream, true, true);
				if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
					throw new ApiException("400", "The picture is too large (over 25 megapixels)");
				}
				BufferedImage image = reader.read(0);
				if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
					throw new ApiException("400", "The file is not a valid picture");
				}
				return image;
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException e) {
			if (e instanceof ApiException api) {
				throw api;
			}
			throw new ApiException("400", "The file is not a valid picture");
		}
	}

	private static BufferedImage scale(BufferedImage source, int width, int height) {
		BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(source, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}
		return target;
	}

	private static byte[] png(BufferedImage image) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new ApiException("500", "Failed to process the picture");
		}
	}

}
