package helliker.id3;
// CHANGED BY MORITZ RINGLER (mr) for mp3dings
// $Id: ID3v2Tag.java,v 1.16 2006/08/12 12:38:37 ringler Exp $
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.logging.Logger;


/*
   Copyright (C) 2008-2010 Moritz Ringler
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
 * The aspects of ID3v2Tag concerning manipulation of the tag data.
 *
 */

abstract class AbstractID3v2Tag implements ID3Tag {
  /**
   * The tag data.
   */
  final ID3v2Frames frames = new ID3v2Frames();

  public AbstractID3v2Tag(){
      //Default constructor
  }


  final static Logger logger = Logger.getLogger(ID3v2Tag.class.getPackage().getName());

  private final static CharsetEncoder isolatinEncoder = Charset.forName("ISO-8859-1").newEncoder();
  private final static CharsetEncoder utf16Encoder = Charset.forName("UTF-16").newEncoder();
  // http://id3.org/Compliance_Issues says:
  //"If a language is unknown or undefined, the Language Code should be left blank."
  // blank. Huh? That's probably supposed to mean three nulls, three spaces?
  private final static byte[] UNKNOWN_LANGUAGE = new byte[]{32,32,32};

  /** Encodes the specified data as an ID3v2 byte array. If possible
      ISO-8859-1 encoding is used, UTF-16 otherwise.
      The returned byte array consists of
      <ul>
      <li>the ID3v2 encoding byte</li>
      <li>the language bytes if non-null</li>
      <li>the bytes for descr</li>
      <li>the null terminator suitable for the encoding,
      either one or two null bytes
      </li>
      <li>the bytes for val</li>
      </ul>
  **/
  private byte[] encodePair(String descr, String val, byte[] lang, boolean forceIsoLatinVal) throws CharacterCodingException{
      if(lang != null && lang.length != 3){
          throw new IllegalArgumentException("lang must have length 3");
      }
      synchronized(isolatinEncoder){
          // if we can encode in iso-latin we use iso-latin
          // otherwise we use utf-8
          isolatinEncoder.reset();
          boolean isIsoLatin = isolatinEncoder.canEncode(descr)
              && (forceIsoLatinVal || isolatinEncoder.canEncode(val));
          CharsetEncoder encoder = (isIsoLatin)? isolatinEncoder : utf16Encoder;
          encoder.reset();
          ByteBuffer dbytes = encoder.encode(CharBuffer.wrap(descr));
          ByteBuffer vbytes = ((forceIsoLatinVal) ? isolatinEncoder : encoder)
                  .encode(CharBuffer.wrap(val));


          int headsize = 1 + //encoding byte
                        ((lang== null) ? 0 : lang.length) + //lang bytes
                        dbytes.limit() + // descr
                        (isIsoLatin ? 1 : 2); // null terminator
          byte[] result = new byte[headsize + vbytes.limit()];

          //this takes care of the null terminators
          Arrays.fill(result, 0, headsize, (byte) 0x00);

          //set the encoding byte (0x00 for isolatin, 0x01 for UTF-16)
          if(!isIsoLatin){
              result[0] = 0x01;
          }

          //copy the language bytes
          int pos = 1;
          if(lang != null){
              System.arraycopy(lang, 0, result, pos, lang.length);
              pos += lang.length;
          }

          //copy the descr bytes
          dbytes.get(result, pos, dbytes.limit());

          //copy the val bytes
          vbytes.get(result, headsize, vbytes.limit());
          return result;
      }
  }

  private byte[] encode(String text) throws CharacterCodingException{
      synchronized(isolatinEncoder){
          isolatinEncoder.reset();
          // if we can encode in iso-latin we use iso-latin
          // otherwise we use utf-8
          boolean isIsoLatin = isolatinEncoder.canEncode(text);
          CharsetEncoder encoder = (isIsoLatin)? isolatinEncoder : utf16Encoder;
          encoder.reset();
          ByteBuffer bytes = encoder.encode(CharBuffer.wrap(text));
          int len = bytes.limit();
          byte[] result = new byte[len + 1];
          result[0] = (byte) (isIsoLatin ? 0x00 : 0x01);
          bytes.get(result, 1, len);
          return result;
      }
  }

  /**
   * Set the data contained in a text frame. This includes all frames with an id
   * that starts with 'T' but excludes "TXXX". If an improper id is passed, then
   * nothing will happen.
   *
   *@param id    the id of the frame to set the data for
   *@param data  the data for the frame
   */
  public void setTextFrame(String id, String data) {
    if ((id.charAt(0) == 'T')
        && !id.equals(ID3v2Frames.USER_DEFINED_TEXT_INFO)) {

      try {
        updateFrameData(id, encode(data));
      } catch (CharacterCodingException e) {
          logger.warning("Error setting text frame " + id + " to " + data);
          logger.warning(e.toString());
      }
    }

    //start (mr)
    //Quick and dirty implementation of USLT frame
    //byte  0      = text encoding = 0 for iso-latin or 1 for utf-16
    //bytes 1 to 3 = language = "XXX" (unknown)
    //byte  4      = string terminator (no content description)
    //byte  5      = string terminator if encoding is UTF-16
    if(id.equals(ID3v2Frames.UNSYNCHRONISED_LYRIC_TRANSCRIPTION)){
      try{
          synchronized(isolatinEncoder){
              updateFrameData(id, encodePair("", data, UNKNOWN_LANGUAGE, false));
          }
      } catch (CharacterCodingException e) {
          logger.warning("Error setting frame " + id + " to " + data);
          logger.warning(e.toString());
      }
    }
    //end (mr)
  }


  /**
   * Set the data contained in a URL frame. This includes all frames with an id
   * that starts with 'W' but excludes "WXXX". If an improper id is passed, then
   * nothing will happen.
   *
   *@param id    the id of the frame to set the data for
   *@param data  the data for the frame
   */
  public void setURLFrame(String id, String data) {
    if ((id.charAt(0) == 'W')
        && !id.equals(ID3v2Frames.USER_DEFINED_URL)) {

      updateFrameData(id, data.getBytes());
    }
  }


  /**
   * Sets the data contained in the user defined text frame (TXXX).
   *
   *@param description  a description of the data
   *@param value        the data for the frame
   */
  public void setUserDefinedTextFrame(String description, String value) {
    try {
      updateFrameData(ID3v2Frames.USER_DEFINED_TEXT_INFO, encodePair(description, value, null, false));
    } catch (CharacterCodingException e) {
          logger.warning("Error setting user defined text frame to " + value);
          logger.warning(e.toString());
    }
  }


  /**
   * Sets the data contained in the user defined url frame (WXXX).
   *
   *@param description  a description of the url
   *@param value        the url for the frame
   */
  public void setUserDefinedURLFrame(String description, String value) {
    try {
      updateFrameData(ID3v2Frames.USER_DEFINED_URL, encodePair(description, value, null, true));
    } catch (CharacterCodingException e) {
          logger.warning("Error setting user defined url frame to " + value);
          logger.warning(e.toString());
    }
  }


  /**
   * Set the data contained in the comments frame (COMM).
   *
   *@param description  a description of the comment
   *@param comment      the comment
   */
  public void setCommentFrame(String description, String comment) {
    try {
        updateFrameData(ID3v2Frames.COMMENTS,
            encodePair(description, comment, UNKNOWN_LANGUAGE, false));
    } catch (CharacterCodingException e) {
          logger.warning("Error setting comment frame to " + comment);
          logger.warning(e.toString());
    }
  }


  /**
   * Remove the frame with the specified id from the file. If there is no frame
   * with that id nothing will happen.
   *
   *@param id  the id of the frame to remove
   */
  public void removeFrame(String id) {
    frames.remove(id);
  }


  /**
   * Updates the data for the frame specified by id. If no frame exists for the
   * id specified, a new frame with that id is created.
   *
   *@param id    the id of the frame to update
   *@param data  the data for the frame
   */
  public void updateFrameData(String id, byte[] data) {
    if (frames.containsKey(id)) {
      ((ID3v2Frame) frames.get(id)).setFrameData(data);
    } else {
      ID3v2Frame frame = new ID3v2Frame(id, data);
      frames.put(id, frame);
    }
  }


  /**
   * Returns the textual information contained in the frame specified by the id.
   * Not every type of frame has textual information. If an id is specified that
   * will not work, the empty string is returned.
   *
   *@param id                        the id of the frame to get text from
   *@return                          the text information contained in the frame
   *@exception ID3v2FormatException  if an error is encountered parsing data
   */
  public String getFrameDataString(String id) throws ID3v2FormatException {
    String str = new String();

    if (frames.containsKey(id)) {
      str = ((ID3v2Frame) frames.get(id)).getDataString();
    }

    return str;
  }

  public APICImage getAttachedPicture()  throws ID3v2FormatException{
      final String id = ID3v2Frames.ATTACHED_PICTURE;
      return (frames.containsKey(id))
      ? new APICImage((ID3v2Frame) frames.get(id))
      : null;
  }

  public void setAttachedPicture(APICImage pic){
      updateFrameData(ID3v2Frames.ATTACHED_PICTURE, pic.toFrameData());
  }


  /**
   * Returns the data found in the frame specified by the id. If the frame
   * doesn't exist, then a zero length array is returned.
   *
   *@param id  the id of the frame to get the data from
   *@return    the data found in the frame
   */
  public byte[] getFrameData(String id) {
    byte[] b = new byte[0];

    if (frames.containsKey(id)) {
      b = ((ID3v2Frame) frames.get(id)).getFrameData();
    }

    return b;
  }

}

