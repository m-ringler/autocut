package helliker.id3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

/*
   Copyright (C) 2001 Jonathan Hilliker
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
 * Reads information from a Xing info header. Thanks to the Scilla project
 * (http://www.xs4all.nl/~rwvtveer/scilla/) for code ideas.
 */
public class XingVBRHeader extends AbstractVBRHeader {
    @SuppressWarnings("hiding")
    private static final Logger logger = Logger.getLogger(MPEGAudioFrameHeader.class.getPackage().getName());

  /**
   * The XING header identifier.
   */
  private static final String IDENTIFIER = "Xing";

  /**
   * the Size of the Flags
   */
  private static final int FLAGS_SIZE = 4;
  /**
   * the TOC Size
   */
  private static final int TOC_SIZE = 100;

  /**
   * number of Frames
   */
  private int numFrames = -1;
  /**
   * number of Bytes
   */
  private int numBytes = -1;
  /**
   * ???
   */
  private int vbrScale = -1;
  /**
   * ???
   */
  private byte[] toc;
  /**
   * the length
   */
  private int length = -1;
  /**
   * Exsistence Mag
   */
  private boolean exists = false;

  private int filesize = -1;


  /**
   * Looks for a Xing VBR header in the file. If it is found then that means
   * this is a variable bit rate file and all the data in the header will be
   * parsed. The position of the header buffer will be unchanged, and its mark
   * will be set to its position when this method returns.
   *
   *@param xingbytes                   the bytes to read the xing header from
   *@param layer                       the layer value read by the
   *      MPEGAudioFrameHeader
   *@param mpegVersion                 the version value read by the
   *      MPEGAudioFrameHeader
   *@param sampleRate                  the sample rate read by the
   *      MPEGAudioFrameHeader
   *@param channelMode                 the channel mode read by the
   *      MPEGAudioFrameHeader
   *@exception IOException             if an error occurs
   *@exception CorruptHeaderException  if an error occurs
   */
  public XingVBRHeader(ByteBuffer header, int layer,
                       int mpegVersion, int sampleRate,
                       int channelMode, long filesize)
     throws IOException{

    if(filesize <= Integer.MAX_VALUE){
        this.filesize = (int) filesize;
    }

    header.mark();
    exists = checkHeader(header, channelMode, mpegVersion);

    if (exists) {
      readHeader(header);
      super.calc(layer, mpegVersion, sampleRate);
    }
    header.reset();
  }


    /**
    * Checks to see if a xing header is in this file. If so the buffer will
    * be positioned after the HEAD_START bytes
    *
    * @param raf              the file to read from
    * @param offset           the location of the first mpeg frame
    * @param channelMode      the channel mode read by the MPEGAudioFrameHeader
    * @param mpegVersion      the version value read by the MPEGAudioFrameHeader
    * @return                 whether the header was found
    * @exception IOException  if an error occurs
    */
    private boolean checkHeader(ByteBuffer header, int channelMode,
                int mpegVersion)
    {
       int hs;
       byte[] id = null;
       try{
           id = IDENTIFIER.getBytes("US-ASCII");
       } catch (Exception ex){
           throw new Error(ex);
       }
       if (mpegVersion == MPEGAudioFrameHeader.MPEG_V_1) {
           hs = (channelMode == MPEGAudioFrameHeader.MONO_MODE)? 21 : 36;
       } else {
           hs = (channelMode == MPEGAudioFrameHeader.MONO_MODE)? 13 : 21;
       }
       int j = 0;
       int i = 0;
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
               logger.warning("Xing header found at position " + (i - j) + " after frame start; expected at " + hs);
           }
       }
       return result;
    }


  /**
   * Parses the header data.
   *
   *@param header                      the data to read from (pointing after the
   *      IDENTIFIER)
   *@exception IOException             if an error occurs
   *@exception CorruptHeaderException  if an error occurs
   */
  private void readHeader(ByteBuffer header)
     throws IOException{

    length = IDENTIFIER.length();
    byte flags[] = new byte[FLAGS_SIZE];
    header.get(flags);
    length += flags.length;

    if (BinaryParser.bitSet(flags[3], 0)) {
      numFrames = header.getInt();
      length += 4;
    }
    if (BinaryParser.bitSet(flags[3], 1)) {
      numBytes = header.getInt();
      length += 4;
    }
    if (BinaryParser.bitSet(flags[3], 2)) {
      toc = new byte[TOC_SIZE];
      header.get(toc);
      length += TOC_SIZE;
    }
    if (BinaryParser.bitSet(flags[3], 3)) {
      vbrScale = header.getInt();
      length += 4;
    }

    // If bytes aren't given then we can get a reasonable guess with the
    // file length
    if (numBytes <= 0 && filesize > 0) {
      numBytes = filesize;
    }
  }

  /**
   * Returns true if a Xing VBR header was found in the file passed to the
   * constructor.
   *
   *@return   whether a Xing VBR header was found
   */
  @Override
public boolean exists() {
    return exists;
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
   * Returns the VBR scale used to generate this VBR file. If a header is not
   * found, -1 is returned.
   *
   *@return   the VBR scale used to generate this VBR file
   */
  public int getVBRScale() {
    return vbrScale;
  }


  /**
   * Returns the toc used to seek to an area of this VBR file. If a header is
   * not found an empty array is returned.
   *
   *@return   the toc used to seek to an area of this VBR file
   */
  public byte[] getTOC() {
    return toc;
  }

  /**
   * Returns the length (in bytes) of this Xing VBR header.
   *
   *@return   the length of this Xing VBR header
   */
  @Override
public int getLength() {
    return length;
  }

}// XingVBRHeader

