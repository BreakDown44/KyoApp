package de.scangolf.pc;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/** Fügt einer PNG-Datei einen pHYs-Block (Auflösung) hinzu. */
final class PngDpi {

    private PngDpi() {
    }

    static void set(java.io.File file, double dpi) throws IOException {
        Path p = file.toPath();
        byte[] png = Files.readAllBytes(p);
        // IHDR endet bei 8 (Signatur) + 4 + 4 + 13 + 4 = 33
        int insertAt = 33;
        int ppm = (int) Math.round(dpi / 0.0254);
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(chunk);
        byte[] type = {'p', 'H', 'Y', 's'};
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        DataOutputStream dd = new DataOutputStream(data);
        dd.writeInt(ppm);
        dd.writeInt(ppm);
        dd.writeByte(1);
        byte[] body = data.toByteArray();
        d.writeInt(body.length);
        d.write(type);
        d.write(body);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(body);
        d.writeInt((int) crc.getValue());
        ByteArrayOutputStream out = new ByteArrayOutputStream(png.length + 32);
        out.write(png, 0, insertAt);
        out.write(chunk.toByteArray());
        out.write(png, insertAt, png.length - insertAt);
        Files.write(p, out.toByteArray());
    }
}
