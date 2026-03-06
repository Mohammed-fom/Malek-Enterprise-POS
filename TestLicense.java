import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class TestLicense {
    private static final String SECRET_SALT = "M4l3k_En73rpR1s3_P0S_S3cr3t!";

    public static void main(String[] args) {
        String encodedKey = "MjAyNi0xMi0zMTphMWNkODdkNDJkNDAzMzNlYTU1NTU4M2FjM2YyMTY5ODcwZmRlMzYxMGE2MjkzZDUzOWE2ZWU5OTgzOWZjZjY2ZmJlMmEz";
        try {
            String decodedString = new String(Base64.getDecoder().decode(encodedKey), StandardCharsets.UTF_8);
            System.out.println("Decoded: " + decodedString);
            String[] parts = decodedString.split(":");
            if (parts.length != 2) {
                System.out.println("Invalid format: " + decodedString);
                return;
            }

            String dateStr = parts[0];
            String providedHash = parts[1];

            String data = dateStr + SECRET_SALT;
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
            String computedHash = hexString.toString();

            System.out.println("Provided Hash: " + providedHash);
            System.out.println("Computed Hash: " + computedHash);

            if (!computedHash.equals(providedHash)) {
                System.out.println("Signature mismatch!");
                return;
            }

            LocalDate expiryDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            System.out.println("Expiry Date: " + expiryDate);
            System.out.println("Current Date: " + LocalDate.now());
            if (LocalDate.now().isAfter(expiryDate)) {
                System.out.println("Expired!");
                return;
            }

            System.out.println("Valid!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
