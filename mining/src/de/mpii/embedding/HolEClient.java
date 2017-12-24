package de.mpii.embedding;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/******************************************************************************
 * Created by hovinhthinh on 12/22/17.
 * <p>
 * Compilation:  javac Complex.java
 * Execution:    java Complex
 * <p>
 * Data type for complex numbers.
 * <p>
 * The data type is "immutable" so once you create and initialize
 * a Complex object, you cannot change it. The "final" keyword
 * when declaring re and im enforces this rule, making it a
 * compile-time error to change the .re or .im instance variables after
 * they've been initialized.
 * <p>
 * % java Complex
 * a            = 5.0 + 6.0i
 * b            = -3.0 + 4.0i
 * Re(a)        = 5.0
 * Im(a)        = 6.0
 * b + a        = 2.0 + 10.0i
 * a - b        = 8.0 + 2.0i
 * a * b        = -39.0 + 2.0i
 * b * a        = -39.0 + 2.0i
 * a / b        = 0.36 - 1.52i
 * (a / b) * b  = 5.0 + 6.0i
 * conj(a)      = 5.0 - 6.0i
 * |a|          = 7.810249675906654
 * tan(a)       = -6.685231390246571E-6 + 1.0000103108981198i
 ******************************************************************************/

/******************************************************************************
 * Compilation:  javac Complex.java
 * Execution:    java Complex
 * <p>
 * Data type for complex numbers.
 * <p>
 * The data type is "immutable" so once you create and initialize
 * a Complex object, you cannot change it. The "final" keyword
 * when declaring re and im enforces this rule, making it a
 * compile-time error to change the .re or .im instance variables after
 * they've been initialized.
 * <p>
 * % java Complex
 * a            = 5.0 + 6.0i
 * b            = -3.0 + 4.0i
 * Re(a)        = 5.0
 * Im(a)        = 6.0
 * b + a        = 2.0 + 10.0i
 * a - b        = 8.0 + 2.0i
 * a * b        = -39.0 + 2.0i
 * b * a        = -39.0 + 2.0i
 * a / b        = 0.36 - 1.52i
 * (a / b) * b  = 5.0 + 6.0i
 * conj(a)      = 5.0 - 6.0i
 * |a|          = 7.810249675906654
 * tan(a)       = -6.685231390246571E-6 + 1.0000103108981198i
 ******************************************************************************/

class Complex {
    private final double re;   // the real part
    private final double im;   // the imaginary part

    // create a new object with the given real and imaginary parts
    public Complex(double real, double imag) {
        re = real;
        im = imag;
    }

    // return a string representation of the invoking Complex object
    public String toString() {
        if (im == 0) return re + "";
        if (re == 0) return im + "i";
        if (im < 0) return re + " - " + (-im) + "i";
        return re + " + " + im + "i";
    }

    // return abs/modulus/magnitude
    public double abs() {
        return Math.hypot(re, im);
    }

    // return angle/phase/argument, normalized to be between -pi and pi
    public double phase() {
        return Math.atan2(im, re);
    }

    // return a new Complex object whose value is (this + b)
    public Complex plus(Complex b) {
        Complex a = this;             // invoking object
        double real = a.re + b.re;
        double imag = a.im + b.im;
        return new Complex(real, imag);
    }

    // return a new Complex object whose value is (this - b)
    public Complex minus(Complex b) {
        Complex a = this;
        double real = a.re - b.re;
        double imag = a.im - b.im;
        return new Complex(real, imag);
    }

    // return a new Complex object whose value is (this * b)
    public Complex times(Complex b) {
        Complex a = this;
        double real = a.re * b.re - a.im * b.im;
        double imag = a.re * b.im + a.im * b.re;
        return new Complex(real, imag);
    }

    // return a new object whose value is (this * alpha)
    public Complex scale(double alpha) {
        return new Complex(alpha * re, alpha * im);
    }

    // return a new Complex object whose value is the conjugate of this
    public Complex conjugate() {
        return new Complex(re, -im);
    }

    // return a new Complex object whose value is the reciprocal of this
    public Complex reciprocal() {
        double scale = re * re + im * im;
        return new Complex(re / scale, -im / scale);
    }

    // return the real or imaginary part
    public double re() {
        return re;
    }

    public double im() {
        return im;
    }

    // return a / b
    public Complex divides(Complex b) {
        Complex a = this;
        return a.times(b.reciprocal());
    }

    // return a new Complex object whose value is the complex exponential of this
    public Complex exp() {
        return new Complex(Math.exp(re) * Math.cos(im), Math.exp(re) * Math.sin(im));
    }

    // return a new Complex object whose value is the complex sine of this
    public Complex sin() {
        return new Complex(Math.sin(re) * Math.cosh(im), Math.cos(re) * Math.sinh(im));
    }

    // return a new Complex object whose value is the complex cosine of this
    public Complex cos() {
        return new Complex(Math.cos(re) * Math.cosh(im), -Math.sin(re) * Math.sinh(im));
    }

    // return a new Complex object whose value is the complex tangent of this
    public Complex tan() {
        return sin().divides(cos());
    }


    // a static version of plus
    public static Complex plus(Complex a, Complex b) {
        double real = a.re + b.re;
        double imag = a.im + b.im;
        Complex sum = new Complex(real, imag);
        return sum;
    }

    // See Section 3.3.
    public boolean equals(Object x) {
        if (x == null) return false;
        if (this.getClass() != x.getClass()) return false;
        Complex that = (Complex) x;
        return (this.re == that.re) && (this.im == that.im);
    }

    // See Section 3.3.
    public int hashCode() {
        return Objects.hash(re, im);
    }

}

class FFT {

    // compute the FFT of x[], assuming its length is a power of 2
    public static Complex[] fft(Complex[] x) {
        int n = x.length;

        // base case
        if (n == 1) return new Complex[]{x[0]};

        // radix 2 Cooley-Tukey FFT
        if (n % 2 != 0) {
            throw new IllegalArgumentException("n is not a power of 2");
        }

        // fft of even terms
        Complex[] even = new Complex[n / 2];
        for (int k = 0; k < n / 2; k++) {
            even[k] = x[2 * k];
        }
        Complex[] q = fft(even);

        // fft of odd terms
        Complex[] odd = even;  // reuse the array
        for (int k = 0; k < n / 2; k++) {
            odd[k] = x[2 * k + 1];
        }
        Complex[] r = fft(odd);

        // combine
        Complex[] y = new Complex[n];
        for (int k = 0; k < n / 2; k++) {
            double kth = -2 * k * Math.PI / n;
            Complex wk = new Complex(Math.cos(kth), Math.sin(kth));
            y[k] = q[k].plus(wk.times(r[k]));
            y[k + n / 2] = q[k].minus(wk.times(r[k]));
        }
        return y;
    }


    // compute the inverse FFT of x[], assuming its length is a power of 2
    public static Complex[] ifft(Complex[] x) {
        int n = x.length;
        Complex[] y = new Complex[n];

        // take conjugate
        for (int i = 0; i < n; i++) {
            y[i] = x[i].conjugate();
        }

        // compute forward FFT
        y = fft(y);

        // take conjugate again
        for (int i = 0; i < n; i++) {
            y[i] = y[i].conjugate();
        }

        // divide by n
        for (int i = 0; i < n; i++) {
            y[i] = y[i].scale(1.0 / n);
        }

        return y;

    }

    // compute the circular convolution of x and y
    public static Complex[] cconvolve(Complex[] x, Complex[] y) {

        // should probably pad x and y with 0s so that they have same length
        // and are powers of 2
        if (x.length != y.length) {
            throw new IllegalArgumentException("Dimensions don't agree");
        }

        int n = x.length;

        // compute FFT of each sequence
        Complex[] a = fft(x);
        Complex[] b = fft(y);

        // point-wise multiply
        Complex[] c = new Complex[n];
        for (int i = 0; i < n; i++) {
            c[i] = a[i].times(b[i]);
        }

        // compute inverse FFT
        return ifft(c);
    }


    // compute the linear convolution of x and y
    public static Complex[] convolve(Complex[] x, Complex[] y) {
        Complex ZERO = new Complex(0, 0);

        Complex[] a = new Complex[2 * x.length];
        for (int i = 0; i < x.length; i++) a[i] = x[i];
        for (int i = x.length; i < 2 * x.length; i++) a[i] = ZERO;

        Complex[] b = new Complex[2 * y.length];
        for (int i = 0; i < y.length; i++) b[i] = y[i];
        for (int i = y.length; i < 2 * y.length; i++) b[i] = ZERO;

        return cconvolve(a, b);
    }

    // display an array of Complex numbers to standard output
    public static void show(Complex[] x, String title) {
        System.out.println(title);
        System.out.println("-------------------");
        for (int i = 0; i < x.length; i++) {
            System.out.println(x[i]);
        }
        System.out.println();
    }


    /***************************************************************************
     * Test client and sample execution
     * <p>
     * % java FFT 4
     * x
     * -------------------
     * -0.03480425839330703
     * 0.07910192950176387
     * 0.7233322451735928
     * 0.1659819820667019
     * <p>
     * y = fft(x)
     * -------------------
     * 0.9336118983487516
     * -0.7581365035668999 + 0.08688005256493803i
     * 0.44344407521182005
     * -0.7581365035668999 - 0.08688005256493803i
     * <p>
     * z = ifft(y)
     * -------------------
     * -0.03480425839330703
     * 0.07910192950176387 + 2.6599344570851287E-18i
     * 0.7233322451735928
     * 0.1659819820667019 - 2.6599344570851287E-18i
     * <p>
     * c = cconvolve(x, x)
     * -------------------
     * 0.5506798633981853
     * 0.23461407150576394 - 4.033186818023279E-18i
     * -0.016542951108772352
     * 0.10288019294318276 + 4.033186818023279E-18i
     * <p>
     * d = convolve(x, x)
     * -------------------
     * 0.001211336402308083 - 3.122502256758253E-17i
     * -0.005506167987577068 - 5.058885073636224E-17i
     * -0.044092969479563274 + 2.1934338938072244E-18i
     * 0.10288019294318276 - 3.6147323062478115E-17i
     * 0.5494685269958772 + 3.122502256758253E-17i
     * 0.240120239493341 + 4.655566391833896E-17i
     * 0.02755001837079092 - 2.1934338938072244E-18i
     * 4.01805098805014E-17i
     ***************************************************************************/

    public static void main(String[] args) {
        int n = Integer.parseInt(args[0]);
        Complex[] x = new Complex[n];

        // original data
        for (int i = 0; i < n; i++) {
            x[i] = new Complex(i, 0);
            x[i] = new Complex(-2 * Math.random() + 1, 0);
        }
        show(x, "x");

        // FFT of original data
        Complex[] y = fft(x);
        show(y, "y = fft(x)");

        // take inverse FFT
        Complex[] z = ifft(y);
        show(z, "z = ifft(y)");

        // circular convolution of x with itself
        Complex[] c = cconvolve(x, x);
        show(c, "c = cconvolve(x, x)");

        // linear convolution of x with itself
        Complex[] d = convolve(x, x);
        show(d, "d = convolve(x, x)");
    }

}

public class HolEClient implements EmbeddingClient {
    public static final Logger LOGGER = Logger.getLogger(HolEClient.class.getName());
    private int nEntities, nRelations, eLength;
    private DoubleVector[] entitiesEmbedding, relationsEmbedding;
    private FactEncodedSetPerPredicate[] trueFacts;
    private ConcurrentHashMap<Long, Double>[] cachedRankQueries;

    public HolEClient(String workspace) {
        LOGGER.info("Loading embedding HolE client from '" + workspace + ".");

        try {
            // Read nEntities, nRelations, eLength.
            Scanner metaIn = new Scanner(new File(workspace + "/meta.txt"));
            nEntities = metaIn.nextInt();
            nRelations = metaIn.nextInt();
            int nClasses = metaIn.nextInt();
            metaIn.close();
            trueFacts = new FactEncodedSetPerPredicate[nRelations];
            cachedRankQueries = new ConcurrentHashMap[nRelations];
            for (int i = 0; i < nRelations; ++i) {
                trueFacts[i] = new FactEncodedSetPerPredicate();
                cachedRankQueries[i] = new ConcurrentHashMap<>();
            }
            // Read embeddings.
            DataInputStream eIn = new DataInputStream(new FileInputStream(
                    new File(workspace + "/hole")));
            eLength = (int) (eIn.readDouble() + 1e-6);
            entitiesEmbedding = new DoubleVector[nEntities];
            for (int i = 0; i < nEntities; ++i) {
                entitiesEmbedding[i] = new DoubleVector(eLength);
                for (int j = 0; j < eLength; ++j) {
                    entitiesEmbedding[i].value[j] = eIn.readDouble();
                }
            }
            relationsEmbedding = new DoubleVector[nRelations];
            for (int i = 0; i < nRelations; ++i) {
                relationsEmbedding[i] = new DoubleVector(eLength);
                for (int j = 0; j < eLength; ++j) {
                    relationsEmbedding[i].value[j] = eIn.readDouble();
                }
            }
            eIn.close();
            // Read true facts;
            Scanner fIn = new Scanner(new File(workspace + "/train.txt"));
            while (fIn.hasNext()) {
                int s = fIn.nextInt(), p = fIn.nextInt(), o = fIn.nextInt();
                trueFacts[p].addFact(s, o);
            }
            fIn.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    @Override
//    public double getScore(int subject, int predicate, int object) {
//        int n = (int) (Math.pow(2, Math.ceil(Math.log(eLength) / Math.log(2) - 1e-6)) + 1e-6);
//        Complex s[] = new Complex[n];
//        Complex o[] = new Complex[n];
//        for (int i = 0; i < eLength; ++i) {
//            s[i] = new Complex(entitiesEmbedding[subject].value[i], 0);
//            o[i] = new Complex(entitiesEmbedding[object].value[i], 0);
//        }
//        for (int i = eLength; i < n; ++i) {
//            s[i] = new Complex(0, 0);
//            o[i] = new Complex(0, 0);
//        }
//
//        Complex[] r = FFT.cconvolve(s, o);
//        double result = 0;
//        for (int i = 0; i < eLength; ++i) {
//            result += r[i].re() * relationsEmbedding[predicate].value[i];
//        }
//        return 1.0 / (1 + Math.exp(-result));
//    }

    @Override
    public double getScore(int subject, int predicate, int object) {
        double y = 0, result = 0;
        double a[] = new double[eLength];
        double x2[] = new double[eLength];

        a[0] = entitiesEmbedding[object].value[0];
        for (int i = 1; i < eLength; ++i) {
            a[i] = entitiesEmbedding[object].value[eLength - i];
        }

        for (int i = 0; i < eLength; ++i) {
            y += entitiesEmbedding[subject].value[i] * a[i];
        }
        result += y * relationsEmbedding[predicate].value[0];

        for (int k = 1; k < eLength; ++k) {
            y = 0;
            for (int i = 1; i < eLength; ++i) {
                x2[i] = a[i - 1];
            }
            x2[0] = a[eLength - 1];
            for (int i = 0; i < eLength; ++i) {
                a[i] += x2[i];
                y += entitiesEmbedding[subject].value[i] * x2[i];
            }
            result += y * relationsEmbedding[predicate].value[k];
        }

        return 1.0 / (1 + Math.exp(-result));
    }

    @Override
    public double getInvertedRank(int subject, int predicate, int object) {
        long encoded = FactEncodedSetPerPredicate.encode(subject, object);
        if (cachedRankQueries[predicate].containsKey(encoded)) {
            return cachedRankQueries[predicate].get(encoded);
        }
        int rankH = 1;
        int rankT = 1;
        double score = getScore(subject, predicate, object);
        for (int i = 0; i < nEntities; ++i) {
            if (i == subject || i == object) {
                continue;
            }
            if (!trueFacts[predicate].containFact(i, object) && getScore(i, predicate, object) > score) {
                ++rankH;
            }
            if (!trueFacts[predicate].containFact(subject, i) && getScore(subject, predicate, i) > score) {
                ++rankT;
            }
        }
        double irank = 0.5 / rankH + 0.5 / rankT;
        cachedRankQueries[predicate].put(encoded, irank);
        return irank;
    }

    private static class FactEncodedSetPerPredicate {
        private static final long BASE = 1000000000;
        private HashSet<Long> set = new HashSet<>();

        public static long encode(int subject, int object) {
            return ((long) subject) * BASE + object;
        }

        public void addFact(int subject, int object) {
            set.add(encode(subject, object));
        }

        public boolean containFact(int subject, int object) {
            return set.contains(encode(subject, object));
        }
    }

    public static void main(String[] args) {
        new HolEClient("../data/imdb/");
    }
}
