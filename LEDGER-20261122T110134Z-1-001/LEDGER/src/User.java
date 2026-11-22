    public class User {
        String username;
        String password;

        User(String username, String password) {
            this.username = username;
            this.password = password;
        }

        /** Previous editions stored PBKDF2 hashes; do not mistake a hash for a password. */
        boolean verify(String candidate) {
            if (!password.startsWith("pbkdf2$")) return password.equals(candidate);
            try {
                String[] parts = password.split("\\$", -1);
                if (parts.length != 4) return false;
                int rounds = Integer.parseInt(parts[1]);
                if (rounds < 100000 || rounds > 2000000) return false;
                byte[] salt = java.util.Base64.getDecoder().decode(parts[2]);
                byte[] expected = java.util.Base64.getDecoder().decode(parts[3]);
                if (salt.length < 8 || expected.length != 32) return false;
                javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(candidate.toCharArray(), salt, rounds, 256);
                try {
                    byte[] actual = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
                    return java.security.MessageDigest.isEqual(expected, actual);
                } finally { spec.clearPassword(); }
            } catch (java.security.GeneralSecurityException | RuntimeException e) { return false; }
        }
    }
