import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class GenerateLicense {
    private static final String SECRET_SALT = "M4l3k_En73rpR1s3_P0S_S3cr3t!";

    public static void main(String[] args) throws Exception {
        String expiryDate = "2026-12-31";

        String data = expiryDate + SECRET_SALT;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
        for (int i = 0; i < encodedhash.length; i++) {
            String hex = Integer.toHexString(0xff & encodedhash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        String hash = hexString.toString();

        String raw = expiryDate + ":" + hash;
        System.out.println(Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
