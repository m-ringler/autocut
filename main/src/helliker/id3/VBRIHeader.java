package helliker.id3;

import java.io.IOException;
import java.nio.ByteBuffer;

/*
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
public class VBRIHeader extends AbstractVBRHeader{
    /**
    * The VBRI header identifier;
    */
    private static final String IDENTIFIER = "VBRI";

    /**
    * number of Frames
    */
    private int numFrames;

    /**
    * number of Bytes
    */
    private int numBytes;

    /**
    * VBR quality setting
    */
    private int quality;

    /** The TOC scale **/
    private int tocScale;

    /** bytes per toc entry **/
    private int tocBytesPerEntry;

    /** frames per toc entry **/
    private int tocFramesPerEntry;

    /** length of toc in bytes **/
    private int tocLength;

    /** number of toc entries **/
    private int tocNumEntries;

    /**
    * the length
    */
    private int length;


    /**
    * Exsistence Mag
    */
    private boolean exists = false;

    private int version = -1;

    public VBRIHeader(ByteBuffer header, int layer,
        int mpegVersion, int sampleRate)// throws IOException
    {
        header.mark();
        exists = checkHeader(header);

        if (exists) {
          readHeader(header);
          super.calc(layer, mpegVersion, sampleRate);
        }
        header.reset();
    }

    /**
    * Returns true if a VBRI header was found in the file passed to the
    * constructor.
    *
    *@return   whether a VBRI header was found
    */
    @Override
    public boolean exists() {
        return exists;
    }

    /**
    * Checks to see if a VBRI header is in this file. If so the buffer will
    * be positioned after the HEAD_START bytes
    *
    * @param channelMode      the channel mode read by the MPEGAudioFrameHeader
    * @param mpegVersion      the version value read by the MPEGAudioFrameHeader
    * @return                 whether the header was found
    * @exception IOException  if an error occurs
    */
    private boolean checkHeader(ByteBuffer header)
    {
        int hs = 36;
        byte[] id = null;
        try{
            id = IDENTIFIER.getBytes("US-ASCII");
        } catch (Exception ex){
            throw new Error(ex);
        }
        int i = 0;
        int j = 0;
        for(;i<40 && j < id.length; i++){
            byte c = header.get();
            if(c == id[j]){
                j++;
            } else {
                j = 0;
            }
        }
        boolean result = (j == id.length);
        if(result){
            if((i -j) != hs){
                logger.warning("VBRI header found at position " + (i - j) + " after frame start; expected at " + hs);
            }
        }
        return result;
    }

    /**
    * Parses the header data.
    *
    *@param header the data to read from (pointing after the IDENTIFIER)
    *@exception IOException             if an error occurs
    *@exception CorruptHeaderException  if an error occurs
    */
    private void readHeader(ByteBuffer header)
    //throws IOException
    {

        version = header.getShort() & 0xFFFF; //unsigned short, 2 bytes

        //skip delay, 2 byte float;
        header.getShort();

        quality = header.getShort() & 0xFFFF; //unsigned short, 2 bytes

        numBytes = header.getInt(); //unsigned int, 4 bytes
        if(numBytes < 0){
            logger.warning("Cannot process VBRI headers with more than " + Integer.MAX_VALUE + " bytes ");
            exists = false;
            return;
        }

        numFrames = header.getInt(); //unsigned int, 4 bytes
        if(numFrames < 0){
            logger.warning("Cannot process VBRI headers with more than " + Integer.MAX_VALUE + " bytes ");
            exists = false;
            return;
        }

        tocNumEntries = header.getShort() & 0xFFFF; //unsigned short

        tocScale = header.getShort() & 0xFFFF; //unsigned short

        tocBytesPerEntry = header.getShort() & 0xFFFF; //unsigned short
        if(tocBytesPerEntry > 4){
            logger.warning("Corrupt VBRI header: toc entry size is " +
                    tocBytesPerEntry + "; max allowed value is 4.");
            exists = false;
            return;
        }

        tocFramesPerEntry = header.getShort() & 0xFFFF; //unsigned short

        tocLength = tocNumEntries * tocBytesPerEntry;
        //toc[] = new byte[tocNumEntries * tocEntrySize];
        length = 26 + tocLength;
    }

    /**
    * Returns the number of MPEG frames in the file passed to the constructor. If
    * a header is not found, -1 is returned.
    *
    *@return   the number of MPEG frames
    */
    @Override
    public int getNumFrames() {
        return numFrames;
    }


    /**
    * Returns the number of data bytes in the mpeg frames. If a header is not
    * found, -1 is returned.
    *
    *@return   the number of data bytes in the mpeg frames
    */
    @Override
    public int getNumBytes() {
        return numBytes;
    }

    /**
     * Returns the value of quality.
     */
    public int getQuality()
    {
        return quality;
    }

    /**
     * Returns the value of tocScale.
     */
    public int getTOCScale()
    {
        return tocScale;
    }

    /**
     * Returns the number of bytes per TOC entry.
     */
    public int getTOCBytesPerEntry()
    {
        return tocBytesPerEntry;
    }

    /**
     * Returns the number of frames per TOC entry.
     */
    public int getTOCFramesPerEntry()
    {
        return tocFramesPerEntry;
    }

    /**
     * Returns the length of the TOC in bytes.
     */
    public int getTOCLength()
    {
        return tocLength;
    }

    /**
     * Returns the value of tocNumEntries.
     */
    public int getTOCNumEntries()
    {
        return tocNumEntries;
    }

    /**
     * Returns the VBRI version.
     */
    public int getVersion()
    {
        return version;
    }

    /** Returns the length of this header in bytes */
    @Override
    public int getLength(){
        return length;
    }
}
