package scheduler.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;

public class Util {

    // constants for handling password
    private static final int HASH_STRENGTH = 10;
    private static final int KEY_LENGTH = 16;

    public static byte[] generateSalt() {
        // Generate a random cryptographic salt
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return salt;
    }

    public static byte[] generateHash(String password, byte[] salt) {
        // Specify the hash parameters
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);

        // Generate the hash
        SecretKeyFactory factory = null;
        byte[] hash = null;
        try {
            factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            hash = factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException();
        }
        return hash;
    }

    public static byte[] trim(byte[] bytes)
    {
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 0)
        {
            --i;
        }

        return Arrays.copyOf(bytes, i + 1);
    }

    public static boolean strongPasswdChecker(String passwdInput) {
        if(passwdInput.length()>=8 && passwdInput.matches(".*[a-z].*") && passwdInput.matches(".*[A-Z].*") && passwdInput.matches(".*[1-9].*") && passwdInput.matches(".*[!@#?].*")) {
            return true;
        } else {
            System.out.println("Weak password, try again.");
            System.out.println("Strong password is the password having:\n" +
                    "a.\tAt least 8 characters.\n" +
                    "b.\tA mixture of both uppercase and lowercase letters.\n" +
                    "c.\tA mixture of letters and numbers.\n" +
                    "d.\tInclusion of at least one special character, from “!”, “@”, “#”, “?”.\n");
            return false;
        }
    }
}
