/* Copyright (C) 2009-2010 Moritz Ringler
 * $Id: MP3Search.java 78 2010-12-13 20:36:22Z ringler $
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.mospace.hspiel.mp3autocut;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import org.apache.commons.math.transform.FastFourierTransformer;
import org.apache.commons.math.complex.Complex;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
A precompiled search for a particular mp3 audio pattern.
**/
public class MP3Search
{
    private final static double LN10 = Math.log(10);
    private final static int POINTS_PER_FRAME = 2;

    private final AtomicInteger samplesPerFrame = new AtomicInteger(0);
    private final double[] zeroPaddedPattern;
    private final Complex[] transformedPattern;
    private final int patternLength;

    /** Constructs a new MP3Search for the specified pattern.
    * @param patternFile a constant bitrate mp3 file with the audio pattern or
    * a file produced by the {@link #writePattern} method of this class.
    * @exception IOException thrown when the pattern file cannot be read
    * @exception MP3SearchException thrown when the pattern file is in an illegal format
    */
    public MP3Search(File patternFile) throws IOException, MP3SearchException{
        final double[] pattern = readPattern(patternFile);
        this.patternLength = pattern.length;
        this.zeroPaddedPattern = zeroPad(pattern, nextPowerOfTwo(3 * patternLength + 1));
        //print(zeroPaddedPattern, "pattern.out");
        this.transformedPattern = (new FastFourierTransformer()).transform(this.zeroPaddedPattern);
    }

    /** Searches the specified time span of the specified file for the pattern.
        This method is thread-safe.
        @param mp3 the file to search
        @param tims the playing time in milliseconds at which to begin the search
        @param tfms the playing time in milliseconds at which to stop the search
        @return a SearchResult indicating where the best match was found and how
                good the match was
    */
    public SearchResult search(File mp3, int tims, int tfms) throws IOException, MP3SearchException{
        SearchResult result = this.searchStep(mp3, tims, tfms);

        if (result != null)
        {
            result = result.fillInMissingPositionFields(mp3);
        }

        return result;
    }

    /** Searches the specified time span of the specified file for the pattern.
        @param mp3 the file to search
        @param tims the playing time in milliseconds at which to begin the search
        @param tfms the playing time in milliseconds at which to stop the search
        @return a SearchResult whose Mp3Position fields carry only framecount information
    */
    SearchResult searchStep(File mp3, int tims, int tfms)
    throws IOException, MP3SearchException
    {
        final FileInputStream smp3 = new FileInputStream(mp3);
        final FastFourierTransformer fft = new FastFourierTransformer();

        /* Construct the search buffer */
        final int twoPatternLengths = 2 * this.patternLength;
        final DoubleBuffer db = DoubleBuffer.allocate(twoPatternLengths);

        /* Start reading into the second half of the search buffer */
        db.position(this.patternLength);

        /*
            Construct a double array that will serve as input to the
            search(double[]) method and thus the fourier transformer.
            Note that zeroPaddedPattern.length is a power of two
            >= 3 * patternLength + 1;
        */
        final double[] data = new double[this.zeroPaddedPattern.length];
        java.util.Arrays.fill(data, 0.0);

        /* return values */
        float maxCorr = 0;
        int maxCorrFrames = 0;

        try
        {
            /* Open an MP3Reader on the input stream */
            final MP3Reader reader = new MP3Reader(smp3);

            /* set input position to tims */
            while (reader.hasMoreFrames() && reader.getMillis() < tims)
            {
                reader.readFrame(false);
            }

            /* Start search */
            boolean isInitialBufferFill = true;
            while (reader.hasMoreFrames() && reader.getMillis() <= tfms)
            {
                /* read loudness into second half of db */
                readLoudness(reader, db, 0);

                /*
                  points is the number of points in the search buffer,
                  2 * patternLength in the interior of the search region
                 */
                final int numberOfPoints = db.position();

                /* copy all data in the search buffer into data */
                db.flip();
                db.get(data, 0, numberOfPoints);

                /*
                    fill up with zeros if necessary.
                    Elements of data[] with index >= twoPatternLengths are always zero.
                */
                if (numberOfPoints < twoPatternLengths){
                    java.util.Arrays.fill(data, numberOfPoints, twoPatternLengths, 0.0);
                }

                /*
                    compare with pattern if the start of the search buffer
                    is at a position >= ti
                */
                if (isInitialBufferFill)
                {
                    isInitialBufferFill = false;
                }
                else
                {
                    /* correlate data and pattern */
                    final float[] searchResult = search(fft, data);

                    /* update maxCorr and maxCorrFrames */
                    if (searchResult[0] > maxCorr)
                    {
                        maxCorr = searchResult[0];
                        maxCorrFrames = reader.getFrameCount() +
                            ((int)searchResult[1] - numberOfPoints)/POINTS_PER_FRAME;
                        //print(data, "data.out");
                    }
                }

                /* stop if the buffer could not be completely filled
                because we have reached the end of the audio input */
                if (numberOfPoints < twoPatternLengths)
                {
                    break;
                }

                /* prepare for next read by moving the second half of
                   the search buffer into the first half, setting its
                   position to patternLength and its limit to twoPatternLengths */
                db.position(patternLength);
                db.compact();
            }
        }
        finally
        {
            /* close the input stream */
            smp3.close();
        }

        return new SearchResult(
            new MP3Position(-1, maxCorrFrames, -1),
            new MP3Position(-1, maxCorrFrames + patternLength/POINTS_PER_FRAME, -1),
            maxCorr);
    }

    /** Searches the pattern in the specified data and returns the
        maximum correlation and the integer delay at which the
        sequence with the maximum correlation begins. */
    private float[] search(FastFourierTransformer fft, double[] data){
        /* compute norms */
        final double[] norms = norms(data, 2 * patternLength);
        assert(norms.length == patternLength + 1);

        /*
            compute the cross correlation of data and pattern.
            The pattern (as returned by readPattern())
            is already in reverse order. Cross-correlation
            with the forward pattern is equivalent to convolution,
            with the reverse pattern, which is what we do here.
        */
        Complex[] cbuff = fft.transform(data);
        for (int k = 0; k < cbuff.length; k++)
        {
            cbuff[k] = cbuff[k].multiply(transformedPattern[k]);
        }

        cbuff = fft.inversetransform(cbuff);

        /* write data and cross correlation to disk (for debugging) */
        // double[] dbuff = new double[cbuff.length];
        // for(int k = 0; k < cbuff.length; k++){
            // dbuff[k] = cbuff[k].getReal();
        // }
        // print(dbuff, "cc.out");

        /*
            Find the maximum of the cross correlation function
            for delays between 0 and patternLength.
        */
        double maxCorr = Double.NEGATIVE_INFINITY;
        int maxCorrDelay = Integer.MIN_VALUE;

        for (int delay = 0; delay < patternLength; delay ++)
        {
            /* k = patternLength - 1 corresponds to delay zero */
            final double ck =
                cbuff[delay + patternLength - 1].getReal() / norms[delay];
            if (ck > maxCorr){
                maxCorr = ck;
                maxCorrDelay = delay;
            }
        }

        /* return the result */
        return new float[]{ (float)maxCorr, maxCorrDelay };
    }

    /** Reads double loudness values from the specified reader into the
        specified buffer. The buffer is completely filled if the reader
        can provide enough data. If the buffer is <code>null</code>
        a new buffer is allocated that can hold all loudness data for
        the specified filesize.
        @param reader the mp3 audio data source
        @param loudness the output buffer or <code>null</code> if a new
            output buffer should be allocated and returned
        @param filesize the number of bytes of mp3 data for which an output
            buffer should be allocated. Ignored unless <code>loudness</code>
            is <code>null</code>.
        @return the specified or newly allocated output buffer
    */
    private DoubleBuffer readLoudness(MP3Reader reader,
            DoubleBuffer loudness, int filesize)
            throws MP3SearchException{

        DoubleBuffer lloudness = loudness;
        ShortBuffer sampleBuffer = reader.getOutput();

        while(reader.hasMoreFrames() &&
             (loudness == null || loudness.hasRemaining()))
        {
            /* read a single frame. This fills sampleBuffer. */
            reader.readFrame();

            /* check if we have got some samples */
            if (sampleBuffer.position() > 0){
                /* set samples per frame if not previously set */
                this.samplesPerFrame.compareAndSet(0, sampleBuffer.position());

                /* check samples per frame */
                if (  sampleBuffer.position() != samplesPerFrame.get() ){
                    throw new Error("All MP3 frames (both pattern and file to cut) must have the same number of samples.");
                }

                /*
                    The loudness of the samples in the frame
                    computed as the logarithm of the square
                    amplitude is summed in
                    the POINTS_PER_FRAME slots of the frameloudness array.
                */
                double[] frameloudness = new double[POINTS_PER_FRAME];

                sampleBuffer.flip(); // prepare sampleBuffer for read access
                final double BLOCKSIZE = sampleBuffer.limit() * 1.0f/POINTS_PER_FRAME;
                java.util.Arrays.fill(frameloudness, 0.0);
                for (int k = 0; sampleBuffer.hasRemaining(); k++){
                    double d = sampleBuffer.get();
                    d = d * d;
                    if(d > 0){
                        frameloudness[(int) (k/BLOCKSIZE)] += Math.log(d);
                    }
                }

                /* convert to decadic log, normalize and
                fill missing values with the equivalent of
                total decadic loudness 1 */
                final double normalizationFactor = POINTS_PER_FRAME * 1.0f/samplesPerFrame.get();
                final double factor = normalizationFactor/LN10;
                for (int k = frameloudness.length - 1; k >= 0; k--)
                {
                    if (frameloudness[k] == 0)
                    {
                        frameloudness[k] = normalizationFactor;
                    }
                    else
                    {
                        frameloudness[k] *= factor;
                    }
                }

                /* create the output buffer if it is null */
                if (lloudness == null)
                {
                    lloudness = DoubleBuffer.allocate(
                        filesize/reader.getLastHeader().framesize * POINTS_PER_FRAME);
                }

                /* store loudness in output buffer */
                lloudness.put(frameloudness);

                // prepare for next read
                sampleBuffer.clear();
            }
        }

        return lloudness;
    }

    public static void main(String[] argv) throws Exception{
        MP3Search me = new MP3Search(new File(argv[0]));
        int starttime = (argv.length > 2)
            ? (int)(1000 * Float.parseFloat(argv[2]))
            : 0;
        int enddtime = (argv.length > 3)
            ? (int)(1000 * Float.parseFloat(argv[3]))
            : Integer.MAX_VALUE;

        SearchResult result = me.search(
            new File(argv[1]),
            starttime,
            enddtime);

        if (result != null)
        {
        System.err.printf((Locale) null, "Best match is t = (%.3f s ... %.3f s) "+
            "with cross-correlation %.3f.\n",
            result.getStart().getTimeMillis()/1000.0, result.getEnd().getTimeMillis()/1000.0, result.getCorrelation());
        }
    }

    /* I/O */
    /** Writes the pattern of this MP3Search to the specified file.
    * The file can be passed to the constructor to recreate the MP3Search,
    * note that the format of the file may change between different
    * versions of this class.
    * @param file The output file.
    * @exception IOException thrown when the specified file cannot be written
    */
    public void writePattern(File file) throws IOException{
        ByteBuffer b = ByteBuffer.allocate(Double.SIZE/8 * this.patternLength);
        b.asDoubleBuffer().put(this.zeroPaddedPattern, 0, this.patternLength);
        b.position(0);
        FileChannel out = (new FileOutputStream(file)).getChannel();
        try
        {
            while (b.hasRemaining())
            {
                out.write(b);
            }

            out.force(true);
        }
        finally
        {
            out.close();
        }
    }

    /** Reads an audio pattern from the specified file.
    * @param patternFile a constant bitrate mp3 file with the audio pattern or
    * a file produced by the {@link #writePattern} method of this class.
    * @return the pattern as a double array
    * @exception IOException thrown when the pattern file cannot be read
    * @exception MP3SearchException thrown when the pattern file is in an illegal format
    */
    private double[] readPattern(File patternFile) throws IOException,
            MP3SearchException {
        double[] pattern = null;

        if (patternFile.getName().endsWith(".mp3"))
        {
            /* read MP3 pattern */
            final long filesize = patternFile.length();
            if (filesize > Integer.MAX_VALUE/2 - 1)
            {
                throw new IllegalArgumentException("Pattern is too large.");
            }

            final InputStream spattern = new FileInputStream(patternFile);
            DoubleBuffer loudness = null;
            try
            {
                MP3Reader reader = new MP3Reader(spattern);
                loudness = readLoudness(reader, null, (int)filesize);
            }
            finally
            {
                spattern.close();
            }

            /* Get loudness as double array */
            pattern = new double[loudness.position()];
            loudness.flip();
            loudness.get(pattern);
            final int n = pattern.length;

            /* Allow DoubleBuffer to be garbage-collected */
            loudness = null;

            /* normalize */
            double norm = l2norm(pattern, n);
            for (int k = 0; k <  n; k++)
            {
                pattern[k] = pattern[k]/norm;
            }

            /* flip */
            flip(pattern);
        }
        else
        {
            /* read serialized pattern */
            pattern = readSerializedPattern(patternFile);
        }

        return pattern;
    }

    private static double[] readSerializedPattern(File f) throws IOException{
        final FileChannel in = (new FileInputStream(f)).getChannel();
        if (f.length() > Integer.MAX_VALUE){
            throw new IOException("File " + f + " is too big.");
        }
        final ByteBuffer b = ByteBuffer.allocate((int) f.length());
        try{
            while(b.hasRemaining()){
                in.read(b);
            }
        } finally {
            in.close();
        }
        b.flip();
        double[] result = new double[(int) f.length()/(Double.SIZE/8)];
        b.asDoubleBuffer().get(result);
        return result;
    }

    /** Writes the specified list of doubles to the specified file.
        Used for debugging.
    */
    @SuppressWarnings("unused")
	private static void print(double[] d, String file){
        try
        {
            final PrintStream out = new PrintStream(new FileOutputStream(file));
            for (int k = 0; k < d.length; k++)
            {
                out.println(d[k] + " ");
            }

            out.close();
        }
        catch (Exception ex)
        {
            throw new Error(ex);
        }
    }

    /* Array utilities */
    /** Reverses the order of the elements in d. */
    private static void flip(double[] d)
    {
        double buff;
        int i = 0;
        final int n = d.length - 1;
        for (int j = n; j > i; j--)
        {
            i = n - j;
            buff = d[j];
            d[j] = d[i];
            d[i] = buff;
        }
    }

    /** Returns an array whose first entries consist of the entries
    of pattern and whose length is a power of two greater than or
    equal to minlength. All entries after the entries from pattern are
    zero.
    @return pattern if its length is a power of two &ge; minlength or a newly allocated array
    */
    private static double[] zeroPad(double[] pattern, int minlength)
    {
        // zero-padding;
        double[] result = pattern;
        final int n = pattern.length;
        final int n2 = nextPowerOfTwo(Math.max(n, minlength));
        if (n2 != n)
        {
            result = new double[n2];
            System.arraycopy(pattern, 0, result, 0, n);
            java.util.Arrays.fill(result, n + 1, n2, 0);
        }

        return result;
    }

    /* Arithmetics */
    /** Computes the smallest power of two 2<sup>n</sup> such that d &le; 2<sup>n</sup>. **/
    private static int nextPowerOfTwo(double d)
    {
        double log2d = Math.log(d) / Math.log(2);
        return (int)Math.pow(2, Math.ceil(log2d));
    }

    /** Computes the square root of the sum of the squares of the first n entries in d.
    * l2norm(d, n) = sqrt ( sum_(i=0)^(n-1) d[i] )
    */
    private static double l2norm(double[] d, int n)
    {
        double result = 0;
        for (int k = 0; k < n; k++)
        {
            result += (d[k] * d[k]);
        }

        result = Math.sqrt(result);
        return result;
    }

    /** Computes the l2norms of all contiguous blocks of length
        this.patternLength in the first n entries of d.
        @return an array of length <code>n - this.patternLength + 1</code> with the norms.
    **/
    private double[] norms(final double[] d, int n)
    {
        double[] result = new double[n - this.patternLength + 1];
        result[0] = l2norm(d, this.patternLength);
        double nsqr = result[0] * result[0];
        for (int k = 0, j = this.patternLength;
            j < n;
            j++)
        {
            nsqr = nsqr - d[k] * d[k] + d[j] * d[j];
            result[++k] = Math.sqrt(nsqr);
        }

        return result;
    }
}


