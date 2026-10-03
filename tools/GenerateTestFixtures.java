// This generator and its synthetic outputs are dedicated to CC0-1.0.
// No photographs, personal metadata, network access or Android APIs are used.
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.zip.CRC32;

class GenerateTestFixtures {
    public static void main(String[] args) throws Exception {
        Path target = Path.of("app/src/test/resources/fixtures");
        Files.createDirectories(target);
        byte[] pixels = new byte[64 * (1 + 64 * 3)];
        int state = 0x50434149;
        for (int row = 0; row < 64; row++) {
            for (int col = 1; col < 193; col++) {
                state = state * 1664525 + 1013904223;
                pixels[row * 193 + col] = (byte)(state >>> 24);
            }
        }
        writeNew(target.resolve("base.png"), png(pixels));
        writeNew(target.resolve("exact-copy.png"), png(pixels));
        byte[] brighter = pixels.clone();
        for (int row = 0; row < 64; row++) {
            for (int col = 1; col < 193; col++) {
                int offset = row * 193 + col;
                brighter[offset] = (byte)Math.min(255, (pixels[offset] & 255) + 20);
            }
        }
        writeNew(target.resolve("brighter.png"), png(brighter));
        StringBuilder manifest = new StringBuilder();
        for (String name : new String[]{"base.png", "exact-copy.png", "brighter.png"}) {
            byte[] bytes = Files.readAllBytes(target.resolve(name));
            String hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            manifest.append(hash).append("  ").append(name).append('\n');
            System.out.println(name + " bytes=" + bytes.length + " sha256=" + hash);
        }
        writeNew(target.resolve("SHA256SUMS"), manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void writeNew(Path file, byte[] bytes) throws IOException {
        // Fail on existing output: never overwrite an existing user's fixture silently.
        Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
    }
    private static byte[] png(byte[] pixels) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte)137,80,78,71,13,10,26,10});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(header);
        data.writeInt(64); data.writeInt(64); data.write(new byte[]{8,2,0,0,0});
        chunk(out, "IHDR", header.toByteArray());
        // Uncompressed zlib block, deterministic across JDK/OS versions.
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        z.write(0x78); z.write(0x01); z.write(1);
        int n = pixels.length;
        z.write(n & 255); z.write(n >>> 8); z.write((~n) & 255); z.write(((~n) >>> 8) & 255);
        z.write(pixels);
        java.util.zip.Adler32 adler = new java.util.zip.Adler32(); adler.update(pixels);
        new DataOutputStream(z).writeInt((int)adler.getValue());
        chunk(out, "IDAT", z.toByteArray()); chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }
    private static void chunk(OutputStream out, String type, byte[] payload) throws IOException {
        DataOutputStream data = new DataOutputStream(out);
        byte[] label = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        data.writeInt(payload.length); data.write(label); data.write(payload);
        CRC32 crc = new CRC32(); crc.update(label); crc.update(payload); data.writeInt((int)crc.getValue());
    }
}
