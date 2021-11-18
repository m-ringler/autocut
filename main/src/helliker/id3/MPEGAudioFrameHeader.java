package helliker.id3;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 Copyright (C) 2001,2002 Jonathan Hilliker
 Copyright (C) 2007 Moritz Ringler
 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.
 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 Lesser General Public License for more details.
 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
/**
 * This class reads through the file specified and tries to find an mpeg frame.
 * It then reads data from the header of the first frame encountered. <br/>
 * 
 * @author Jonathan Hilliker
 * @version 1.8
 */
public class MPEGAudioFrameHeader {
    private static final Logger logger = Logger
            .getLogger(MPEGAudioFrameHeader.class.getPackage().getName());

    /**
     * The size of the IO buffer, this will also be the maximum number of bytes
     * read during one recursion of findOffset.
     **/
    private final int CHUNK_SIZE = 0x4000;

    public final static int FREE_FORMAT = -2;

    public final static int NULL = -1;

    /**
     * MPEG Version 2.5
     */
    public final static int MPEG_V_25 = 0;
    /**
     * MPEG Version 2
     */
    public final static int MPEG_V_2 = 2;
    /**
     * MPEG Version 1
     */
    public final static int MPEG_V_1 = 3;
    /**
     * Layer III
     */
    public final static int MPEG_L_3 = 1;
    /**
     * Layer II
     */
    public final static int MPEG_L_2 = 2;
    /**
     * Layer I
     */
    public final static int MPEG_L_1 = 3;
    /**
     * Mono
     */
    public final static int MONO_MODE = 3;

    /**
     * Header Size
     */
    private static final int HEADER_SIZE = 4;

    /**
     * the number of bytes that findOffset is guaranteed to read from the mp3
     * file
     */
    private static final int HEADER_BUFFER_SIZE = 500;

    /**
     * The MPEG Bitrate Table. -2 means "free bitrate" and -1 means
     * "not allowed".
     */
    private static final int[][] bitrateTable = {
            { FREE_FORMAT, FREE_FORMAT, FREE_FORMAT, FREE_FORMAT, FREE_FORMAT },
            { 32, 32, 32, 32, 8 }, { 64, 48, 40, 48, 16 },
            { 96, 56, 48, 56, 24 }, { 128, 64, 56, 64, 32 },
            { 160, 80, 64, 80, 40 }, { 192, 96, 80, 96, 48 },
            { 224, 112, 96, 112, 56 }, { 256, 128, 112, 128, 64 },
            { 288, 160, 128, 144, 80 }, { 320, 192, 160, 160, 96 },
            { 352, 224, 192, 176, 112 }, { 384, 256, 224, 192, 128 },
            { 416, 320, 256, 224, 144 }, { 448, 384, 320, 256, 160 },
            { NULL, NULL, NULL, NULL, NULL } };
    /**
     * The MPEG Sample rate Table
     */
    private static final int[][] sampleTable = { { 44100, 22050, 11025 },
            { 48000, 24000, 12000 }, { 32000, 16000, 8000 },
            { NULL, NULL, NULL } };

    /**
     * MPEG Version Lables
     */
    private static final String[] versionLabels = { "MPEG Version 2.5", null,
            "MPEG Version 2.0", "MPEG Version 1.0" };
    /**
     * MPEG Layer Lables
     */
    private static final String[] layerLabels = { null, "Layer III",
            "Layer II", "Layer I" };
    /**
     * MPEG ChannelLables
     */
    private static final String[] channelLabels = { "Stereo",
            "Joint Stereo (STEREO)", "Dual Channel (STEREO)",
            "Single Channel (MONO)" };
    /**
     * Emphasis Lables
     */
    private static final String[] emphasisLabels = { "none", "50/15 ms", null,
            "CCIT J.17" };
    /**
     * MPEG slot lengths
     */
    private static final int[] slotLength = { NULL, 1, 1, 4 };// in bytes

    /**
     * VBR Header
     */
    private VBRHeader vbrHeader = null;
    /**
     * The MP3 File
     */
    private File mp3 = null;
    /**
     * The Version
     */
    private int version;
    /**
     * The Layer
     */
    private int layer;
    /**
     * The Bitrate
     */
    private int bitRate;
    /**
     * The free format bitrate as detected from the distance of sync words
     */
    private int ffBitRate = NULL;
    /** Whether a variable bit rate has been detected. */
    private boolean vbr = false;

    /**
     * The Samplerate
     */
    private int sampleRate;
    /**
     * The Channel mode
     */
    private int channelMode;
    /**
     * Copyright Flag
     */
    private boolean copyrighted;
    /**
     * CRC Flag
     */
    private boolean crced;
    /**
     * Original Flag
     */
    private boolean original;
    /**
     * Private Bit
     */
    private boolean privateBit;
    /**
     * Emphais
     */
    private int emphasis;
    /**
     * Header Location in File?
     */
    private long location;
    /**
     * Padding Flag
     */
    private boolean padding;

    /** Loggging control */
    private static Level lev = Level.FINE;

    /**
     * Create an MPEGAudioFrameHeader from the file specified. Upon creation
     * information will be read in from the first frame header the object
     * encounters in the file.
     * 
     * @param mp3
     *            the file to read from
     * @exception NoMPEGFramesException
     *                if the file is not a valid mpeg
     * @exception FileNotFoundException
     *                if an error occurs
     * @exception IOException
     *                if an error occurs
     */
    public MPEGAudioFrameHeader(File mp3) throws NoMPEGFramesException,
            IOException {
        this(mp3, 0);
    }

    /**
     * Create an MPEGAudioFrameHeader from the file specified. Upon creation
     * information will be read in from the first frame header the object
     * encounters in the file. The offset tells the object where to start
     * searching for an MPEG frame. If you know the size of an id3v2 tag
     * attached to the file and pass it to this ctor, it will take less time to
     * find the frame.
     * 
     * @param mp3
     *            the file to read from
     * @param minSyncRange
     *            this is the minimum length of the file that is searched for
     *            consecutive headers
     * @exception NoMPEGFramesException
     *                if the file is not a valid mpeg
     * @exception FileNotFoundException
     *                if an error occurs
     * @exception IOException
     *                if an error occurs
     */
    public MPEGAudioFrameHeader(File mp3, int minSyncRange)
            throws NoMPEGFramesException, IOException {

        this.mp3 = mp3;
        location = 0;
        int sync = (int) Math.min(mp3.length() * 3 / 4, minSyncRange);

        FileChannel channel = new FileInputStream(mp3).getChannel();
        ByteBuffer bb = ByteBuffer.allocate(CHUNK_SIZE);
        try {
            while (true) {
                clear();
                // look for a candidate Audio Frame Header
                channel.position(location);
                location = findOffset(bb, channel, 200);
                if (location == NULL) {
                    throw new NoMPEGFramesException(mp3);
                }
                logger.log(lev, "Found syncword at " + hex(location));

                // parse the header; does not affect bb position
                readHeader(bb);

                // look for a xing header; does not affect bb position
                vbrHeader = new XingVBRHeader(bb, layer, version, sampleRate,
                        channelMode, mp3.length());
                if (vbrHeader.exists()) {
                    logger.log(lev, "Found Xing header.");
                } else {
                    vbrHeader = null;
                }

                // look for a VBRI header; does not affect bb position
                if (vbrHeader == null) {
                    vbrHeader = new VBRIHeader(bb, layer, version, sampleRate);
                    if (vbrHeader.exists()) {
                        logger.log(lev, "Found VBRI header.");
                    } else {
                        vbrHeader = null;
                    }
                }

                if (vbrHeader != null) {
                    vbr = true;
                    // we have found a VBR header in this frame so this
                    // must be the first valid mpeg frame
                    break;
                }

                // we test whether we have really found a header by
                // looking for consecutive headers/snyc words
                channel.position(location);
                if (bitRate == FREE_FORMAT) {
                    if (testFreeFormat(channel, bb, sync)) {
                        break;
                    }
                } else {
                    if (testKnownBitRate(channel, bb, sync)) {
                        break;
                    }
                }
                location++;
            }
        } finally {
            channel.close();
        }
        logger.log(lev, mp3 + " audio offset is 0x"
                + Long.toHexString(location).toUpperCase());
        logger.log(lev, toString());
    }

    private boolean testKnownBitRate(FileChannel channel, ByteBuffer bb,
            int minSyncRange) throws IOException {
        long length = getUnpaddedFrameLength(bitRate);
        logger.log(lev, "known bitrate test with framelength " + length);
        return sync(channel, bb, minSyncRange, length, false);
    }

    /**
     * Tries to find consecutive mpeg audio frame headers. If the mp3 file is a
     * VBR file without header, and minSyncRange is equal to the size of the
     * file, this method will calculate and store the vbr playing time in a new
     * PseudoVBRHeader.
     * 
     * @param channel
     *            the input file channel positioned at the beginning of the last
     *            header found
     * @param bb
     *            an I/O buffer that contains at its position the bytes of the
     *            last header found
     * @param minSyncRange
     *            the minimum number of bytes from the file to use for
     *            synchronization
     * @param frameLength
     *            the unpadded length of a single frame as determined from
     *            previously found headers
     * @param cbr
     *            whether to assume a constant bitrate
     * @return if consecutive mpeg audio frame headers were found
     **/
    private boolean sync(FileChannel channel, ByteBuffer bb, int minSyncRange,
            long frameLength, boolean cbr) throws IOException {
        boolean result = false;
        long loc = channel.position();
        long len = frameLength;

        long firstloc = loc;
        int framecount = 1;

        do {
            boolean pad = BinaryParser.bitSet(bb.get(bb.position() + 2), 1);
            long nextLoc = loc + len + (pad ? slotLength[layer] : 0);
            framecount++;

            // look for another consecutive syncword
            if (nextLoc > loc && nextLoc < channel.size()) {
                channel.position(nextLoc);
                long nextOffs = findOffset(bb, channel, 0);
                result = (nextOffs == nextLoc);
                if (result) {
                    loc = nextLoc;
                    if (!cbr) {
                        byte brbyte = bb.get(bb.position() + 2);
                        int br = findBitRate(BinaryParser.convertToDecimal(
                                brbyte, 4, 7));
                        long newlen = getUnpaddedFrameLength(br);
                        if (newlen != len) {
                            vbr = true;
                        }
                        len = newlen;
                    }
                } else {
                    logger.log(lev, "Expected frame at offset "
                            + hex(nextLoc)
                            + ((nextOffs < 0) ? " found none."
                                    : " found one at " + hex(nextOffs)));
                }
            } else {
                result = false;
            }
        } while (result && loc < minSyncRange);

        // calculate and store VBR playing time and average bitrate
        if (vbrHeader == null && minSyncRange == channel.size() && vbr) {
            long numbytes = loc + len - firstloc;
            if (numbytes < Integer.MAX_VALUE) {
                vbrHeader = new PseudoVBRHeader(framecount, (int) numbytes,
                        layer, version, sampleRate);
            }
        }

        return result;
    }

    private static String hex(long longval) {
        return "0x" + Long.toHexString(longval).toUpperCase();
    }

    private boolean testFreeFormat(FileChannel channel, ByteBuffer bb,
            int minSyncRange) throws IOException {
        logger.log(Level.FINER, "free format test");
        boolean result = false;
        // Free format: must find the frame length experimentally
        channel.position(location + 1);
        long loc2 = findOffset(bb, channel, 0);// maxRecursion = 0 limits
                                               // detectable framelength to
                                               // CHUNK_SIZE
        if (loc2 == NULL) {
            return false;
        }

        long length = loc2 - location - (padding ? slotLength[layer] : 0);
        channel.position(loc2);
        if (sync(channel, bb, minSyncRange, length, true)) {
            result = true;
            ffBitRate = calculateBitrate(length);
        }
        return result;
    }

    private void clear() {
        version = NULL;
        layer = NULL;
        bitRate = NULL;
        sampleRate = NULL;
        channelMode = NULL;
        copyrighted = false;
        crced = false;
        original = false;
        emphasis = NULL;
        padding = false;
    }

    /**
     * When this method returns bb.hasRemaining() will be true iff an FF byte
     * was found. In that case the buffer will be positioned at the FF byte.
     */
    private static void findFF(ByteBuffer bb) {
        while (bb.hasRemaining()) {
            if ((bb.get() & 0xFF) == 0xFF) {
                bb.position(bb.position() - 1);
                return;
            }
        }
    }

    private boolean testFrameHeader(ByteBuffer bb) {
        boolean result = false;
        bb.mark(); // mark the position of the ff byte
        byte test = bb.get(); // the ff byte
        if ((test & 0xFF) == 0xFF) {
            test = bb.get();
            // Frame sync and layer version tests
            if (BinaryParser.matchPattern(test, "111xxxxx") && // frame sync
                    !BinaryParser.matchPattern(test, "xxxxx00x") && // valid
                                                                    // layer
                    (version == NULL || version == BinaryParser
                            .convertToDecimal(test, 3, 4))
                    && (layer == NULL || layer == BinaryParser
                            .convertToDecimal(test, 1, 2))) {
                test = bb.get();
                if (!BinaryParser.matchPattern(test, "1111xxxx") && // valid
                                                                    // bitrate
                        !BinaryParser.matchPattern(test, "xxxx11xx") && // valid
                                                                        // sampling
                                                                        // rate
                        (bitRate != FREE_FORMAT || FREE_FORMAT == findBitRate(BinaryParser
                                .convertToDecimal(test, 4, 7)))) {
                    test = bb.get();
                    if (!BinaryParser.matchPattern(test, "xxxxxx10")) // valid
                                                                      // emphasis
                    {
                        result = true;
                    }
                }
            }
        }
        bb.reset(); // return the buffer position to the ff byte
        return result;
    }

    // private long findOffset(ByteBuffer bb, FileChannel channel) throws
    // IOException{
    // return findOffset(bb, channel, Integer.MAX_VALUE);
    // }

    /**
     * Attempts to find the location of the first valid mpeg frame in the file.
     * If the return value is not NULL the byte buffer will be positioned at the
     * first header byte and will contain at least HEADER_BUFFER_SIZE bytes of
     * the input file after that position (to allow reading the Xing header from
     * it).
     * 
     * @param channel
     *            the file to scan
     * @param bb
     *            a byte buffer with capacity > HEADER_BUFFER_SIZE
     * @param maxRecurse
     *            the maximum number of times this method is allowed to recurse
     *            before it finds a valid header, this limits the maximum number
     *            of bytes read from the file to maxRecurse * bb.capacity()
     * @return the location of the first mpeg frame or NULL if it fails
     * @exception IOException
     *                if an error occurs
     * @throws IllegalArgumentException
     *             if the bb is too small.
     */
    private long findOffset(ByteBuffer bb, FileChannel channel, int maxRecurse)
            throws IOException {
        if (bb.capacity() <= HEADER_BUFFER_SIZE) {
            throw new IllegalArgumentException("Buffer is too small.");
        }
        long offs = channel.position();

        // Fill buffer from file
        bb.clear();
        for (int bytesread = 0; bytesread != -1 && bb.hasRemaining();) {
            bytesread = channel.read(bb);
        }
        bb.flip();

        // We require more than HEADER_BUFFER_SIZE bytes
        if (bb.remaining() <= HEADER_BUFFER_SIZE) {
            logger.log(lev, "Reached end of file.");
            return NULL;
        }

        // Look for 0xFF byte
        findFF(bb);

        while (bb.remaining() >= HEADER_BUFFER_SIZE) {
            if (testFrameHeader(bb)) {
                return channel.position() - bb.remaining();
            }
            // not a valid header, so look for next FF byte
            bb.get(); // skip the old FF byte
            findFF(bb);
        }

        if (maxRecurse == 0) {
            logger.log(lev, "Reached maximum recursion level.");
            return -1;
        }

        // Position the channel at the position of the last found FF byte
        // Since we're here, we have either advanced the buffer position
        // through findFF or by traversing the loop and calling bb.get().
        // Therefore bb.remaining() is less than the number of bytes read
        // from the file and therefore the following call will always advance
        // the file position compared to the entry of the method.
        // Thus, we avoid inifinite recursion
        channel.position(channel.position() - bb.remaining());
        assert (channel.position() > offs);

        return findOffset(bb, channel, maxRecurse - 1);
    }

    /**
     * Read in all the information found in the mpeg header.
     * 
     * @param raf
     *            the open file to find the frame in
     * @param location
     *            the location of the header (found by findFrame)
     */
    private void readHeader(ByteBuffer bb) {
        bb.mark();
        byte[] head = new byte[HEADER_SIZE];
        bb.get(head);

        version = BinaryParser.convertToDecimal(head[1], 3, 4);
        layer = BinaryParser.convertToDecimal(head[1], 1, 2);
        bitRate = findBitRate(BinaryParser.convertToDecimal(head[2], 4, 7));
        sampleRate = findSampleRate(BinaryParser
                .convertToDecimal(head[2], 2, 3));
        padding = BinaryParser.bitSet(head[2], 1);
        privateBit = BinaryParser.bitSet(head[2], 0);
        channelMode = BinaryParser.convertToDecimal(head[3], 6, 7);
        copyrighted = BinaryParser.bitSet(head[3], 3);
        crced = !BinaryParser.bitSet(head[1], 0);
        original = BinaryParser.bitSet(head[3], 2);
        emphasis = BinaryParser.convertToDecimal(head[3], 0, 1);
        bb.reset();

    }

    /**
     * Based on the bitrate index found in the header, try to find and set the
     * bitrate from the table.
     * 
     * @param bitrateIndex
     *            the bitrate index read from the header
     */
    private int findBitRate(int bitrateIndex) {
        int ind = NULL;

        if (version == MPEG_V_1) {
            if (layer == MPEG_L_1) {
                ind = 0;
            } else if (layer == MPEG_L_2) {
                ind = 1;
            } else if (layer == MPEG_L_3) {
                ind = 2;
            }
        } else if ((version == MPEG_V_2) || (version == MPEG_V_25)) {
            if (layer == MPEG_L_1) {
                ind = 3;
            } else if ((layer == MPEG_L_2) || (layer == MPEG_L_3)) {
                ind = 4;
            }
        }

        return ((ind != NULL) && (bitrateIndex >= 0) && (bitrateIndex <= 15)) ? bitrateTable[bitrateIndex][ind]
                : NULL;
    }

    /**
     * Based on the sample rate index found in the header, attempt to lookup and
     * set the sample rate from the table.
     * 
     * @param sampleIndex
     *            the sample rate index read from the header
     */
    private int findSampleRate(int sampleIndex) {
        int ind = NULL;

        switch (version) {
        case MPEG_V_1:
            ind = 0;
            break;
        case MPEG_V_2:
            ind = 1;
            break;
        case MPEG_V_25:
            ind = 2;
        }

        if ((ind != NULL) && (sampleIndex >= 0) && (sampleIndex <= 3)) {
            return sampleTable[sampleIndex][ind];
        }
        return NULL;
    }

    private int getUnpaddedFrameLength(int bitrate) {
        if (layer == MPEG_L_1) {
            return 48000 * bitrate / sampleRate;
        }

        int coeff = (layer == MPEG_L_3 && version != MPEG_V_1) ? 72000 : 144000;
        return coeff * bitrate / sampleRate;
    }

    /**
     * Computes the length of the frame found. This is not necessarily constant
     * for all frames.
     * 
     * @return the length of the frame found
     */
    public int getFrameLength() {
        int pad = (padding) ? 1 : 0;
        if (layer == MPEG_L_1) {
            return (12000 * getBitRate() / sampleRate + pad) * 4;
        }

        int coeff = (layer == MPEG_L_3 && version != MPEG_V_1) ? 72000 : 144000;
        return (coeff * getBitRate() / sampleRate + pad);
    }

    /**
     * Calculates the bibtrate from the length of an unpadded frame. This is the
     * inverse of the calculation performed by {@link #getFrameLength}.
     * 
     * @param length
     *            the length in bytes of an unpadded frame
     **/
    public int calculateBitrate(long length) {
        if (layer == MPEG_L_1) {
            return (int) (length * sampleRate / 48000);
        }

        int coeff = (layer == MPEG_L_3 && version != MPEG_V_1) ? 72000 : 144000;
        return (int) (length * sampleRate / coeff);
    }

    /**
     * Return a string representation of this object. Includes all information
     * read in.
     * 
     * @return a string representation of this object
     */
    @Override
    public String toString() {
        String str = new String();

        str = getVersion() + " " + getLayer() + "\nBitRate:\t\t\t"
                + getBitRate() + "kbps\nSampleRate:\t\t\t" + getSampleRate()
                + "Hz\nChannelMode:\t\t\t" + getChannelMode()
                + "\nCopyrighted:\t\t\t" + isCopyrighted()
                + "\nOriginal:\t\t\t" + isOriginal() + "\nCRC:\t\t\t\t"
                + isProtected() + "\nEmphasis:\t\t\t" + getEmphasis()
                + "\nOffset:\t\t\t\t" + getLocation() + "\nPrivateBit:\t\t\t"
                + privateBitSet() + "\nPadding:\t\t\t" + hasPadding()
                + "\nFrameLength:\t\t\t" + getFrameLength() + "\nVBR:\t\t\t\t"
                + isVBR();
        if (!isVBR()) {
            str += "\nNumFrames:\t\t\t\t" + getNumFrames();
        } else if (vbrHeader != null) {
            str += "\n" + vbrHeader.toString();
        }

        return str;
    }

    /**
     * Return the version of the mpeg in string form. Ex: MPEG Version 1.0
     * 
     * @return the version of the mpeg
     */
    public String getVersion() {
        String str = null;

        if ((version >= 0) && (version < versionLabels.length)) {
            str = versionLabels[version];
        }

        return str;
    }

    /**
     * Return the layer description of the mpeg in string form. Ex: Layer III
     * 
     * @return the layer description of the mpeg
     */
    public String getLayer() {
        String str = null;

        if ((layer >= 0) && (layer < layerLabels.length)) {
            str = layerLabels[layer];
        }

        return str;
    }

    /**
     * Return the channel mode of the mpeg in string form. Ex: Joint Stereo
     * (STEREO)
     * 
     * @return the channel mode of the mpeg
     */
    public String getChannelMode() {
        String str = null;

        if ((channelMode >= 0) && (channelMode < channelLabels.length)) {
            str = channelLabels[channelMode];
        }

        return str;
    }

    /**
     * Returns the sample rate of the mpeg in Hz
     * 
     * @return the sample rate of the mpeg in Hz
     */
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * Returns true if the audio is copyrighted
     * 
     * @return true if the audio is copyrighted
     */
    public boolean isCopyrighted() {
        return copyrighted;
    }

    /**
     * Returns true if this mpeg is protected by CRC
     * 
     * @return true if this mpeg is protected by CRC
     */
    public boolean isProtected() {
        return crced;
    }

    /**
     * Returns true if this is the original media
     * 
     * @return true if this is the original media
     */
    public boolean isOriginal() {
        return original;
    }

    /**
     * Returns true if this mpeg is encoded in VBR
     * 
     * @return if VBR is present
     */
    public boolean isVBR() {
        return vbr;
    }

    /**
     * Returns the emphasis. I don't know what this means, it just does it...
     * 
     * @return the emphasis
     */
    public String getEmphasis() {
        String str = null;

        if ((emphasis >= 0) && (emphasis < emphasisLabels.length)) {
            str = emphasisLabels[emphasis];
        }

        return str;
    }

    /**
     * Returns the offset at which the first mpeg frame was found in the file.
     * 
     * @return the offset of the mpeg data
     */
    public long getLocation() {
        return location;
    }

    /**
     * Returns true if the file passed to the constructor is an mp3 (MPEG layer
     * III).
     * 
     * @return true if the file is an mp3
     */
    public boolean isMP3() {
        return (layer == MPEG_L_3);
    }

    /**
     * Returns true if the mpeg frames are padded in this file.
     * 
     * @return true if the mpeg frames are padded in this file
     */
    public boolean hasPadding() {
        return padding;
    }

    /**
     * Returns true if the private bit is set.
     * 
     * @return true if the private bit is set
     */
    public boolean privateBitSet() {
        return privateBit;
    }

    /**
     * Returns the playing time of a variable bitrate file. For VBR files with
     * XING or VBRI header, this method returns the playing time read from the
     * header. For VBR files without header this method will return NULL or the
     * actual playing time if a previous call to analyzeVBR() succeeded.
     * 
     * @return the playing time of this mpeg in seconds or NULL
     */
    public int getVBRPlayingTime() {
        int result = NULL;
        if (vbrHeader != null) {
            result = vbrHeader.getPlayingTime();
        }
        return result;
    }

    /**
     * Analyzes a VBR file that does not have a XING of VBRI header.
     * 
     * @return true iff the mp3 file is a VBR file without header, had not
     *         previously been analyzed successfully, and was successfully
     *         analyzed as a result of calling this method.
     */
    public boolean analyzeVBR() throws IOException {
        boolean result = false;
        if (vbr && vbrHeader == null) {
            logger.log(lev, "Analyzing VBR");
            FileChannel channel = (new FileInputStream(mp3)).getChannel();
            try {
                channel.position(location);
                ByteBuffer bb = ByteBuffer.allocate(HEADER_BUFFER_SIZE + 1);
                long offs = findOffset(bb, channel, 0);
                assert (offs == location);
                channel.position(location);
                long fileLength = channel.size();
                if (fileLength < Integer.MAX_VALUE) {
                    sync(channel, bb, (int) fileLength,
                            getUnpaddedFrameLength(bitRate), false);
                }
                logger.log(lev, String.valueOf(vbrHeader));
                result = (vbrHeader != null);
            } finally {
                channel.close();
            }
        }
        return result;
    }

    /**
     * Returns the bitrate of this mpeg. For VBR files with XING or VBRI header,
     * this method returns the average bit rate read from the header. For VBR
     * files without header this method will return NULL or the actual average
     * bitrate if a previous call to analyzeVBR() succeeded.
     * 
     * @return the bitrate of this mpeg (in kbps)
     */
    public int getBitRate() {
        int br = NULL;

        if (vbrHeader != null) {
            br = vbrHeader.getAvgBitrate();
        } else if (bitRate == FREE_FORMAT) {
            br = (ffBitRate > 0) ? ffBitRate : FREE_FORMAT;
        } else {
            br = bitRate;
        }

        return br;
    }

    /**
     * Returns the number of frames in this mpeg file. For CBR files, this does
     * not subtract the size of an id3v1 tag if present so it is not deadly
     * accurate. For VBR files with XING or VBRI header, this method returns the
     * number of frames read from the header. For VBR files without header this
     * method will return NULL or the actual framecount if a previous call to
     * {@link #analyzeVBR} succeeded.
     * 
     * @return the number of frames in this mpeg file
     */
    public int getNumFrames() {
        int num = NULL;

        if (vbrHeader != null) {
            num = vbrHeader.getNumFrames();
        } else {
            num = ((int) (mp3.length() - location)) / getFrameLength();
        }

        return num;
    }

    public static void main(String[] argv) throws Exception {
        lev = Level.INFO;
        for (int i = 0; i < argv.length; i++) {
            File f = new File(argv[i]);
            MPEGAudioFrameHeader mpeg = new MPEGAudioFrameHeader(f, 20000);
            System.out.println(f.getName());
            System.out.println("audiooffset: " + hex(mpeg.getLocation()));
        }
    }

}
