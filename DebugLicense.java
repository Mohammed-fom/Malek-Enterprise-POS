import java.nio.charset.StandardCharsets;
import java.io.FileWriter;
import java.util.Base64;

public class DebugLicense {
    public static void main(String[] args) throws Exception {
        String encodedKey = "MjAyNi0xMi0zMTphMWNkODdkNDJkNDAzMzNlYTU1NTU4M2FjM2YyMTY5ODcwZmRlMzYxMGE2MjkzZDUzOWE2ZWU5OTgzOWZjZjY2ZmJlMmEz";
        String decodedString = new String(Base64.getDecoder().decode(encodedKey), StandardCharsets.UTF_8);

        try (FileWriter fw = new FileWriter("debug_output.txt")) {
            fw.write("Decoded: " + decodedString + "\n");

            String[] parts = decodedString.split(":");
            fw.write("Parts length: " + parts.length + "\n");

            String dateStr = parts[0];
            String providedHash = parts[1];

            fw.write("Date: " + dateStr + "\n");
            fw.write("Provided Hash: " + providedHash + "\n");
            fw.write("Provided Hash Length: " + providedHash.length() + "\n");

            String data = dateStr + "M4l3k_En73rpR1s3_P0S_S3cr3t!";
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (int i = 0; i < encodedhash.length; i++) {
                String hex = Integer.toHexString(0xff & encodedhash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            String computedHash = hexString.toString();

            fw.write("Computed Hash: " + computedHash + "\n");
            fw.write("Computed Hash Length: " + computedHash.length() + "\n");
            fw.write("Equals: " + computedHash.equals(providedHash) + "\n");
        }
    }
}
