package DIRBulkUpdate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Decryptor {
    private static final String ALGORITHM = "AES";
    private static SecretKey key;
    static {
        try {
            String salt = "SECRET";
            byte[] tempkey = salt.getBytes("UTF-8");
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            tempkey = sha.digest(tempkey);
            tempkey = Arrays.copyOf(tempkey, 32);
            key = new SecretKeySpec(tempkey, ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException("Error initializing Decryptor", e);
        }
    }
    

    protected String encrypt(String input) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedValue = cipher.doFinal(input.getBytes());
        return Base64.getEncoder().encodeToString(encryptedValue);
    }

    protected String decrypt(String encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decryptedValue = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decryptedValue);
    }
    
    public static void main(String args[]) throws Exception
    {
    	Decryptor obj = new Decryptor();
    	
//   	    System.out.println(obj.decrypt("Ckf8DMakEo/PLp2WVYiu2RpqHDyTojpwlcwxrobkRburH2CkhL2dAudYhtg/lYjb"));
    	System.out.println(obj.encrypt("E97WBUfQnNo7uORnC5IXyQ=="));
//    	System.out.println(URLEncoder.encode("Ve@@tr33VELM0R0G&nu66arogar@@", StandardCharsets.UTF_8));
 
    }
    
}