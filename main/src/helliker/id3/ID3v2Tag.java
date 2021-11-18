package helliker.id3;

// CHANGED BY MORITZ RINGLER (mr) for mp3dings
// $Id: ID3v2Tag.java,v 1.16 2006/08/12 12:38:37 ringler Exp $
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Arrays;

/*
 Copyright (C) 2001,2002 Jonathan Hilliker
 with modifications
 Copyright (C) 2007-2008 Moritz Ringler
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
 * This class reads and writes id3v2 tags from a file.<br />
 * */
public class ID3v2Tag extends AbstractID3v2Tag {
    /*
     * Only the I/O-related parts of ID3v2Tag are implemented here, the data
     * manipulation stuff is implemented in AbstractID3v2Tag.
     */

    public static class CorruptHeaderException extends IOException {

        private static final long serialVersionUID = 1227590510424116103L;
        private final File file;
        private final long audioOffset;
        private final long tagLength;

        public CorruptHeaderException(File f, long off, long taglen) {
            file = f;
            audioOffset = off;
            tagLength = taglen;
        }

        public long getTagLength() {
            return tagLength;
        }

        public long getAudioOffset() {
            return audioOffset;
        }

        public File getFile() {
            return file;
        }

        @Override
        public String getMessage() {
            StringBuffer sb = new StringBuffer("Corrupt header in file ");
            sb.append(file);
            sb.append(". Audio data begins at offset ");
            sb.append(audioOffset);
            sb.append(" (0x");
            sb.append(Long.toHexString(audioOffset));
            sb.append("), ");
            sb.append(" ID3v2 tag ends at offset ");
            sb.append(tagLength);
            sb.append(" (0x").append(Long.toHexString(tagLength)).append(").");
            return sb.toString();
        }
    }

    // mp3Ext writes padding (wrongfully) as "MP3ext V...."
    /**
     * MP3 Ext badid
     */
    private static final String MP3EXT_BADID = "MP3e";

    // Used to calculate padding change
    /**
     * The maximum allowed padding size in bytes.
     */
    private static final int NEWTAG_LIMIT = 160000;

    /**
     * The File.
     */
    private File mp3 = null;

    /**
     * The tag header.
     */
    private ID3v2Header head = null;

    /**
     * The extended tag header.
     */
    private ID3v2ExtendedHeader ext_head = null;

    /**
     * The tag footer.
     */
    private ID3v2Footer foot = null;

    /** total size of the id3v2 tag in the file */
    private int totalWrittenTagSize;

    /**
     * size of the in-file padding
     */
    private int writtenPadding;

    /**
     * whether the ID3v2 tag exists in the file
     */
    private boolean exists;

    /**
     * Create an id3v2 tag bound to the file provided as a parameter. If a tag
     * exists in the file already, then all the information in the tag will be
     * extracted. If a tag doesn't exist, then this is the file that will be
     * written to when the writeTag method is called.
     * 
     * @param mp3
     *            the file to write/read the the tag information to/from
     * @exception FileNotFoundException
     *                if an error occurs
     * @exception IOException
     *                if an error occurs
     * @exception ID3v2FormatException
     *                if an exisiting id3v2 tag isn't correct
     */
    public ID3v2Tag(File mp3) throws IOException, ID3v2FormatException {
        this.mp3 = mp3;
        // this.mpegOffset = mpegOffset;

        head = new ID3v2Header(mp3);
        exists = head.headerExists();

        if (exists) {
            if (head.getExtendedHeader()) {
                ext_head = new ID3v2ExtendedHeader(mp3);
            }

            if (head.getFooter()) {
                foot = new ID3v2Footer(mp3, head.getTagSize()
                        + head.getHeaderSize());
            }

            RandomAccessFile in = null;

            try {
                in = new RandomAccessFile(mp3, "r");

                // For now only support id3v2.3.0 or greater
                if (head.getMajorVersion() >= 3) {
                    parseFrames(in);

                    // unset the unsynchronization flag
                    head.setUnsynchronisation(false);
                }
            } finally {
                if (in != null) {
                    in.close();
                }
            }

            /*
             * the value returned by head.getTagSize is initially read from the
             * tag header. <blockquote> The ID3v2 tag size is the size of the
             * complete tag after unsychronisation, including padding, excluding
             * the header but not excluding the extended header (total tag size
             * - 10). Only 28 bits (representing up to 256MB) are used in the
             * size description to avoid the introduction of 'false
             * syncsignals'. </blockquote>
             */
            int mutableTagSize = head.getTagSize();
            if (mutableTagSize > 1000000) {
                throw new ID3v2FormatException("Tag too large.");
            }

            totalWrittenTagSize = mutableTagSize;
            if (exists) {
                totalWrittenTagSize += head.getHeaderSize();
                if (head.getFooter()) {
                    totalWrittenTagSize += foot.getFooterSize();
                }
            }
            // Check to validate tag size taken out because MusicMatch
            // has some bugs that causes the check to fail
        }
    }

    /**
     * Read the frames from the file and create ID3v2Frame objects from the data
     * found. Should only be called from the constructor.
     * 
     * @param raf
     *            the open file to read from
     * @exception IOException
     *                if an error occurs
     * @exception ID3v2FormatException
     *                if an error occurs
     */
    private void parseFrames(RandomAccessFile raf) throws IOException,
            ID3v2FormatException {

        long offset = head.getHeaderSize();
        int framesLength = head.getTagSize(); // Actually length of frames +
                                              // padding

        if (head.getExtendedHeader()) {
            framesLength -= ext_head.getSize();
            offset += ext_head.getSize();
        }

        byte[] tagBytes = new byte[framesLength];
        try {
            raf.seek(offset);
            raf.readFully(tagBytes);
        } catch (java.io.EOFException ex) {
            throw new ID3v2FormatException(mp3.getName()
                    + ": EOF before end of ID3v2 tag.");
        }

        int pos = 0;
        int curLength = 0;
        int szparse = 0;
        byte[] sz = new byte[4];
        byte[] flags = new byte[2];

        int[] frameoffs = new int[256];
        int framecount = 0;
        int padstart = tagBytes.length;

        // first pass: find frame offsets
        // FIRSTPASS:
        while (pos < tagBytes.length) {
            String id = "";
            // System.err.print(pos + " ");
            if (pos < tagBytes.length - 4) {
                id = new String(tagBytes, pos, 4);
            }
            if (id.equals(MP3EXT_BADID)) {
                padstart = pos;
                break;
            } else if (id.matches("[A-Z][A-Z0-9]{3}")) {
                padstart = tagBytes.length;
                logger.fine("Found Frame " + id + " at offset "
                        + Long.toHexString(offset + pos));
                frameoffs[framecount++] = pos;
                if (framecount == frameoffs.length) {
                    int[] tmp = new int[2 * frameoffs.length];
                    System.arraycopy(frameoffs, 0, tmp, 0, framecount);
                    frameoffs = tmp;
                }
                pos += 4;
                System.arraycopy(tagBytes, pos, sz, 0, 4);
                // for (int k = 0; k < sz.length;
                // k++)System.out.println(Integer.toHexString(sz[k]));
                if ((head.getMajorVersion() > 3)) {
                    szparse = 1;
                    curLength = BinaryParser.convertToSynchsafeInt(sz);
                } else {
                    szparse = 2;
                    ByteBuffer bb = ByteBuffer.wrap(sz);
                    curLength = bb.getInt();
                }
                // Added by Reed
                if (curLength < 0 || pos + curLength > tagBytes.length) {
                    throw new ID3v2FormatException("ID3v2Tag.parseFrames: "
                            + "Invalid frame size for " + id + ": " + pos + ":"
                            + curLength + ":" + tagBytes.length);
                }

                pos += (6 + curLength);

            } else {
                padstart = pos;
                // PADDING:
                for (; pos < tagBytes.length; pos++) {
                    if (tagBytes[pos] != 0) {
                        boolean looksLikeSsi = true;
                        for (int k = 0; k < sz.length; k++) {
                            if ((sz[k] & 0xFF) >= 0x80) {
                                looksLikeSsi = false;
                                break;
                            }
                        }
                        if (looksLikeSsi) {
                            // try "the other method" of reading tag size
                            switch (szparse) {
                            case 1:
                                ByteBuffer bb = ByteBuffer.wrap(sz);
                                pos = padstart - curLength + bb.getInt();
                                break;
                            case 2:
                                pos = padstart
                                        - curLength
                                        + BinaryParser
                                                .convertToSynchsafeInt(sz);
                                break;
                            default:
                                // do nothing
                            }
                        }
                        szparse = 0;
                        break;
                    }
                }
                if (pos == padstart) {
                    pos++;
                }
            }
        }

        // second pass: store frames
        // SECONDPASS:
        for (int i = 0; i < framecount; i++) {
            pos = frameoffs[i];
            final String id = new String(tagBytes, pos, 4);
            System.arraycopy(tagBytes, pos + 8, flags, 0, 2);
            pos += 10;

            curLength = (i + 1 < framecount) ? frameoffs[i + 1] - pos
                    : padstart - pos;
            byte[] buf = new byte[curLength];
            System.arraycopy(tagBytes, pos, buf, 0, curLength);
            if (BinaryParser.bitSet(flags[1], 1) || head.getUnsynchronisation()) {
                // remove unsynchronization
                buf = deunsync(buf);

                // unset the unsynchronization flag
                flags[1] = (byte) (flags[1] & ~(1 << 1));
            }

            putFrame(new ID3v2Frame(id, flags, buf));
        }
        writtenPadding = tagBytes.length - padstart;

        logger.fine(mp3.getName() + " ID3tag\n" + "offset: " + offset
                + "\nframeslength: " + framesLength + "\npadding: "
                + writtenPadding);

    }

    private String putFrame(ID3v2Frame frame) {
        /*
         * The following line violates the specification in cases where multiple
         * frames of the same kind are allowed. Of multiple frames with the same
         * ID only the last one in the tag will be preserved
         */
        // frames.put(id, frame);
        /*
         * Here we preserve all frames that we encounter, but we will modify
         * only the first frame of a given id, since this is the one that we
         * will find at its proper name in frames.
         */
        final String id = frame.getId();
        String key = id;
        if (frames.containsKey(key)) {
            int i = 0;
            key = id + (i++);
            while (frames.containsKey(key)) {
                key = id + (i++);
            }
        }
        frames.put(key, frame);
        return key;
    }

    @Override
    public void writeTag() throws IOException {
        writeTag(false);
    }

    /**
     * Saves all the information in the tag to the file passed to the
     * constructor. If a tag doesn't exist, a tag is prepended to the file. If
     * the new tag size is less than the old size + padding, then the old tag
     * and part of the old padding will be overwritten. Otherwise, a new tag
     * will be prepended to the file.<br>
     * This method fixes incorrect header size information where this can be
     * done without any risk of data loss.
     * 
     * @exception FileNotFoundException
     *                if an error occurs
     * @exception IOException
     *                if an error occurs
     */
    public void writeTag(boolean forceMpegOffset) throws IOException {
        // unset the unsynchronization flag
        head.setUnsynchronisation(false);

        /* test whether mp3 is writable */
        if (!mp3.canWrite()) {
            throw new IOException("Cannot modify mp3 file.");
        }

        /* calculate the size of the complete written tag */
        int minTotalSize = minimumTotalSize();
        int writtenTagLength = getWrittenTagLength(forceMpegOffset);
        int pad = calculatePadding(minTotalSize, writtenTagLength);
        int totalSize = minTotalSize + pad;

        /* Creates the new tag in-memory. */
        byte[] id3 = getBytes(totalSize);

        if (totalSize <= writtenTagLength) {// the file does not need to change
                                            // size
            logger.fine(mp3.getName() + ": changing id3 tag in place.");
            writeTagSimple(id3);
        } else { // the tag needs to grow
            logger.fine(mp3.getName() + ": writing new file with new tag.");
            writeTagTempFile(id3, writtenTagLength);
        }

        /* update the in-file tag and padding sizes. */
        writtenPadding = pad;
        totalWrittenTagSize = totalSize;
        exists = true;
    }

    private int calculatePadding(int minTotalSize, int writtenTagLength) {
        int pad = writtenTagLength - minTotalSize;
        if (pad < 0) {
            int newTagSize = (writtenTagLength <= 0) ? 512
                    : 2 * writtenTagLength;
            while (newTagSize < minTotalSize) {
                newTagSize *= 2;
            }

            if (newTagSize <= NEWTAG_LIMIT) {
                pad = newTagSize - minTotalSize;
            } else {
                // Gee if it's over the limit this tag is pretty big,
                // so screw padding altogether
                pad = 0;
            }
        }
        return pad;
    }

    /**
     * Compares the size of the complete written tag to the return value of
     * {@link MPEGAudioFrameHeader#findOffset}. If both numbers are equal (as
     * they should be) this method will return their common value. Otherwise if
     * useMpegOffset is true the audio data offset will be used, if it is false
     * a CorruptHeaderException will be thrown.
     * 
     * @return the total written tag size otherwise
     * @see writeTag
     * @see removeTag
     **/
    private int getWrittenTagLength(boolean useMpegOffset) throws IOException {
        int result = totalWrittenTagSize;

        // find beginning of audio data
        int mpegOffset = 0;
        try {
            long tmp = (new MPEGAudioFrameHeader(mp3,
                    2 * totalWrittenTagSize + 10)).getLocation();
            if (tmp >= Integer.MAX_VALUE) {
                throw new IOException(
                        "No MPEG audio frame header found in first "
                                + Integer.MAX_VALUE + " bytes.");
            }
            mpegOffset = (int) tmp;
        } catch (IOException iox) {
            throw iox;
        } catch (RuntimeException rx) {
            throw rx;
        } catch (Exception ex) {
            IOException iox = new IOException(ex.getMessage());
            iox.initCause(iox);
            throw iox;
        }

        // see if beginning of audio data is identical to tag length
        if (mpegOffset != totalWrittenTagSize) {
            if (useMpegOffset) {
                logger.info(mp3.getName()
                        + ": Audio offset does not equal ID3v2 tag size:\n"
                        + "\tAudio offset:" + mpegOffset + " (0x"
                        + Long.toHexString(mpegOffset) + ")\n"
                        + "\tTotal written tag size: " + totalWrittenTagSize
                        + " (0x" + Long.toHexString(totalWrittenTagSize)
                        + ")\n" + "Using audio offset (forced).");
                result = mpegOffset;
            } else {
                throw new CorruptHeaderException(mp3, mpegOffset,
                        totalWrittenTagSize);
            }
        }
        return result;
    }

    /* Checks whether bytes between start and end are all null. */
    @SuppressWarnings("unused")
    private boolean isPadding(long start, long end) throws IOException {
        int length = (int) (end - start);
        boolean isPadding = true;
        InputStream in = new FileInputStream(mp3);
        try {
            byte[] b = new byte[length];
            isPadding = (in.skip(start) == start) && (in.available() >= length)
                    && (in.read(b, 0, length) == length);
            for (int i = 0; i < length && isPadding; i++) {
                isPadding = (b[i] == 0);
            }
        } finally {
            in.close();
        }
        return isPadding;
    }

    private void writeTagSimple(byte[] id3) throws IOException {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(mp3, "rw");
            raf.seek(0);
            raf.write(id3);
        } finally { // cleanup
            /* close random access file */
            if (raf != null) {
                raf.close();
            }
        }
    }

    private void writeTagTempFile(byte[] id3, int tagEnd) throws IOException {
        FileChannel source = null, target = null;
        FileOutputStream tfos = null;
        FileLock targetLock = null;
        File tf = null;
        try {
            /* Create a temporary file to write to, use efficient NIO API */
            String tmpfilename = mp3.getName();
            tmpfilename = tmpfilename.replaceFirst("\\.mp3$", "");
            if (tmpfilename.length() < 3) {
                tmpfilename += "mp3dings";
            }
            tf = File.createTempFile(tmpfilename, ".tmp.mp3", mp3
                    .getAbsoluteFile().getParentFile());
            tfos = new FileOutputStream(tf);
            target = tfos.getChannel();
            targetLock = target.tryLock();
            if (targetLock == null) {
                throw new IOException("Cannot lock temporary target file.");
            }
            /* Create a file channel to read from */
            source = new FileInputStream(mp3).getChannel();
            /* write the new tag to the temp target */
            target.write(ByteBuffer.wrap(id3));
            /* copy the audio portion of the mp3 to the temp target */
            source.transferTo(tagEnd, source.size() - tagEnd, target);
        } catch (IOException x) {
            /* if an error occurred delete the temp file. */
            if (tf != null && tf.exists()) {
                /* release file locks */
                if (targetLock != null) {
                    targetLock.release();
                }
                /* close target file output stream and channel */
                if (tfos != null) {
                    tfos.close();
                }
                tf.delete();
                tf = null;
            }
            throw x; // finally-block will be executed anyways
        } finally { // cleanup
            /* release file locks */
            if (targetLock != null) {
                targetLock.release();
            }
            /* close target file output stream and channel */
            if (tfos != null) {
                tfos.close();
            }
            /* close source file channel */
            if (source != null) {
                source.close();
            }
        }
        /* replace the source mp3 with the succesfully completed target file */
        String message;
        if (tf == null) {
            message = "Could not tag file " + mp3.getName();
            logger.severe(message);
            throw new IOException(message);
        } else if (!tf.exists()) {
            message = "Temporary file has disappeared: " + tf.getName();
            logger.severe(message);
            throw new IOException(message);
        } else if (!mp3.delete()) {
            message = "Cannot overwrite file " + mp3.getName();
            logger.severe(message);
            tf.delete();
            throw new IOException(message);
        } else if (!tf.renameTo(mp3)) {
            message = "Cannot rename temporary mp3 file " + tf.getName() + "->"
                    + mp3.getName();
            logger.severe(message);
            throw new IOException(message);
        }
    }

    /**
     * Remove an existing id3v2 tag from the file passed to the constructor.
     * 
     * @exception FileNotFoundException
     *                if an error occurs
     * @exception IOException
     *                if an error occurs
     */
    @Override
    public void removeTag() throws IOException {
        removeTag(false);
    }

    @Override
    public void removeTag(boolean forceMpegOffset) throws IOException {
        if (exists || forceMpegOffset) {
            int offset = getWrittenTagLength(forceMpegOffset);
            writeTagTempFile(new byte[] {}, offset);
        }
        exists = false;
        totalWrittenTagSize = 0;
        writtenPadding = 0;
    }

    /**
     * Return a binary representation of this object to be written to a file.
     * This is in the format of the id3v2 specifications. This includes the
     * header, extended header (if it exists), the frames, padding (if it
     * exists), and a footer (if it exists).
     * 
     * @return a binary representation of this id3v2 tag
     */
    private byte[] getBytes(int totalSize) {
        byte[] b = new byte[totalSize];
        int bytesCopied = 0;

        /* update header size information */
        int mutableTagSize = totalSize;
        mutableTagSize -= head.getHeaderSize();
        if (head.getFooter()) {
            mutableTagSize -= foot.getFooterSize();
        }
        head.setTagSize(mutableTagSize);

        /* add header */
        int length = head.getHeaderSize();
        System.arraycopy(head.getBytes(), 0, b, bytesCopied, length);
        bytesCopied += length;
        assert bytesCopied == 10; // header size should always equal 10

        /* add extended header if it exists */
        if (head.getExtendedHeader()) {
            length = ext_head.getSize();
            System.arraycopy(ext_head.getBytes(), 0, b, bytesCopied, length);
            bytesCopied += length;
        }

        /* add the actual data frames */
        length = frames.getLength();
        System.arraycopy(frames.getBytes(), 0, b, bytesCopied, length);
        bytesCopied += length;

        /* add padding */
        // Bytes should all be zero's by default
        int pad = b.length - bytesCopied;
        if (head.getFooter()) {
            pad -= foot.getFooterSize();
        }
        assert (pad >= 0);
        Arrays.fill(b, bytesCopied, bytesCopied + pad, (byte) 0);
        bytesCopied += pad;

        /* add footer if it exists */
        if (head.getFooter()) {
            length = foot.getFooterSize();
            System.arraycopy(foot.getBytes(), 0, b, bytesCopied, length);
            bytesCopied += length;
        }
        assert (bytesCopied == b.length);

        return b;
    }

    /**
     * Returns true if an id3v2 tag exists in the file that was passed to the
     * constructor and false otherwise
     * 
     * @return true if an id3v2 tag exists in the file passed to the ctor
     */
    public boolean tagExists() {
        return exists;
    }

    private int minimumMutableSize() {
        int minsize = frames.getLength();
        if (head.getExtendedHeader()) {
            minsize += ext_head.getSize();
        }
        return minsize;
    }

    private int minimumTotalSize() {
        int minSize = minimumMutableSize();
        minSize += head.getHeaderSize();
        if (head.getFooter()) {
            minSize += foot.getFooterSize();
        }
        return minSize;
    }

    public int getTotalWrittenTagSize() {
        return totalWrittenTagSize;
    }

    /**
     * Return a string representation of this object. This includes all data
     * contained in all parts of this tag.
     * 
     * @return a string representation of this object
     */
    @Override
    public String toString() {
        String str = head.toString();

        str += "\nTotalWrittenTagSize:\t\t\t" + totalWrittenTagSize + " bytes";

        if (head.getExtendedHeader()) {
            str += "\n" + ext_head.toString();
        }

        str += "\n" + frames.toString();

        if (head.getFooter()) {
            str += foot.toString();
        }

        return str;
    }

    /**
     * Copies information from the ID3Tag parameter and inserts it into this
     * tag. Previous data will be overwritten. [NOT IMPLEMENTED]
     * 
     * @param tag
     *            the tag to copy from
     */
    @Override
    public void copyFrom(ID3Tag tag) {
        throw new Error("Not implemented");
        // Not implemented yet
    }

    public static byte[] deunsync(byte[] unsynchronizedBytes) {
        java.nio.ByteBuffer decoded = java.nio.ByteBuffer
                .wrap(unsynchronizedBytes);
        int start = 0;

        for (int pos = 0; pos < unsynchronizedBytes.length - 1; pos++) {
            // Look for FF 00
            if ((unsynchronizedBytes[pos] & 0xFF) == 0xFF
                    && unsynchronizedBytes[pos + 1] == 0x00) {
                // pos is index of FF.
                // Append unsynchronizedBytes[start .. pos] to decoded.
                decoded.put(unsynchronizedBytes, start, pos - start + 1);

                // Set new start index to pos + 2
                start = pos + 2;

                // Skip 00
                pos++;
            }
        }

        byte[] result;
        if (start == 0) {
            result = unsynchronizedBytes;
        } else {
            decoded.put(unsynchronizedBytes, start, unsynchronizedBytes.length
                    - start);
            decoded.flip();
            result = new byte[decoded.limit()];
            decoded.get(result);
            // System.out.println(buf.length + "->" + result.length);
        }

        return result;
    }

}
