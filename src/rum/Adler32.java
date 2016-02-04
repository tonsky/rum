package rum;

// Algorithm taken from React.js

public class Adler32 {
    public static int MOD = 65521;

    public static int calc(String data) {
        long a = 1;
        long b = 0;
        int i = 0;
        int l = data.length();
        long m = l & ~0x3;
        long n;
        while (i < m) {
            n = Math.min(i + 4096, m);
            for (; i < n; i += 4) {
                b += (a += data.charAt(i)) +
                     (a += data.charAt(i + 1)) +
                     (a += data.charAt(i + 2)) +
                     (a += data.charAt(i + 3));
            }
            a %= MOD;
            b %= MOD;
        }
        for (; i < l; i++) {
            b += a += data.charAt(i);
        }
        a %= MOD;
        b %= MOD;
        return (int) a | (int) b << 16;
    }
}
