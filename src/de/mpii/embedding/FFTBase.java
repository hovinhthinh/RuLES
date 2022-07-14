package de.mpii.embedding;

public class FFTBase {
    /**
     * The Fast Fourier Transform (generic version, with NO optimizations).
     *
     * @param xReal  an array of length n, the real part
     * @param xImag  an array of length n, the imaginary part
     * @param DIRECT TRUE = direct transform, FALSE = inverse transform
     * @return 2 arrays of length n
     */
    public static void fft(double[] xReal, double[] xImag, boolean DIRECT) {
        // - n is the dimension of the problem
        // - nu is its logarithm in base e
        int n = xReal.length;

        // Here I check if n is a power of 2. If exist decimals in ld, I quit
        // from the function returning null.
        if ((n & (n - 1)) != 0) {
            throw new RuntimeException("The number of elements is not a power of 2.");
        }

        // Declaration and initialization of the variables
        // ld should be an integer, actually, so I don't lose any information in
        // the cast
        int nu = (int) (Math.log(n) / Math.log(2.0) + 1e-6), n2 = n >> 1, nu1 = nu - 1;
        double tReal, tImag, p, arg, c, s;

        // Here I check if I'm going to do the direct transform or the inverse
        // transform.
        double constant;
        if (DIRECT)
            constant = -2 * Math.PI;
        else
            constant = 2 * Math.PI;

        // First phase - calculation
        int k = 0;
        for (int l = 1; l <= nu; ++l) {
            while (k < n) {
                for (int i = 1; i <= n2; ++i) {
                    p = bitreverseReference(k >> nu1, nu);
                    // direct FFT or inverse FFT
                    arg = constant * p / n;
                    c = Math.cos(arg);
                    s = Math.sin(arg);
                    tReal = xReal[k + n2] * c + xImag[k + n2] * s;
                    tImag = xImag[k + n2] * c - xReal[k + n2] * s;
                    xReal[k + n2] = xReal[k] - tReal;
                    xImag[k + n2] = xImag[k] - tImag;
                    xReal[k] += tReal;
                    xImag[k] += tImag;
                    k++;
                }
                k += n2;
            }
            k = 0;
            --nu1;
            n2 >>= 1;
        }

        // Second phase - recombination
        k = 0;
        int r;
        while (k < n) {
            r = bitreverseReference(k, nu);
            if (r > k) {
                tReal = xReal[k];
                tImag = xImag[k];
                xReal[k] = xReal[r];
                xImag[k] = xImag[r];
                xReal[r] = tReal;
                xImag[r] = tImag;
            }
            k++;
        }

    }

    /**
     * The reference bitreverse function.
     */
    private static int bitreverseReference(int j, int nu) {
        int j2;
        int j1 = j;
        int k = 0;
        for (int i = 1; i <= nu; ++i) {
            j2 = j1 >> 1;
            k = 2 * k + j1 - 2 * j2;
            j1 = j2;
        }
        return k;
    }
}