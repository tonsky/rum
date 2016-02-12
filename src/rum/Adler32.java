package rum;

// Algorithm taken from React.js

public class Adler32 {
    public static int MOD = 65521;

    public static int calc(String data) {
        int a = 1;
        int b = 0;
        int i = 0;
        int l = data.length();
        int m = l & ~0x3;
        int n;
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
        return a | b << 16;
    }
}
