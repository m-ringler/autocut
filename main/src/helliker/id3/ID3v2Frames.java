package helliker.id3;

import java.util.HashMap;
import java.util.Iterator;
/*
 *  Copyright (C) 2001 Jonathan Hilliker
 *  2004, Moritz Ringler. (Major changes in javadoc)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */




/**
 * This class is a collection that is used to hold the ID3v2Frames.
 */

/** Frame definitions according to
 *  <a href="http://www.id3.org/id3v2.4.0-frames.txt">Martin Nilsson,
 *  ID3 tag version 2.4.0 - Native Frames,  2000-11-01</a>
 *  from <a href="http://www.id3.org">the ID3v2 web site</a>.
 **/
public class ID3v2Frames extends HashMap {

  /**
	 * 
	 */
	private static final long serialVersionUID = -5579177646813011589L;
//
  /** Album/Movie/Show title.
   *  The 'Album/Movie/Show title' frame is intended for the title of the
   *  recording (or source of sound) from which the audio in the file is
   *  taken.
   **/
  public static String ALBUM = "TALB";
  /** BPM (beats per minute).
   *  The 'BPM' frame contains the number of beats per minute in the
   *  main part of the audio. The BPM is an integer and represented as a
   *  numerical string.
   **/
  public static String BPM = "TBPM";
  /** Composer.
   *  The 'Composer' frame is intended for the name of the composer.
   **/
  public static String COMPOSER = "TCOM";
  /** Content type (Genre).
   * The 'Content type', which ID3v1 was stored as a one byte numeric
   * value only, is now a string. You may use one or several of the ID3v1
   * types as numerical strings, or, since the category list would be
   * impossible to maintain with accurate and up to date categories,
   * define your own. Example: "21" $00 "Eurodisco" $00
   *
   * You may also use any of the following keywords:
   *
   * RX  Remix
   * CR  Cover
   **/
  public static String CONTENT_TYPE = "TCON";
  /** Copyright message.
   *  The 'Copyright message' frame, in which the string must begin with a
   *  year and a space character (making five characters), is intended for
   *  the copyright holder of the original sound, not the audio file
   *  itself. The absence of this frame means only that the copyright
   *  information is unavailable or has been removed, and must not be
   *  interpreted to mean that the audio is public domain. Every time this
   *  field is displayed the field must be preceded with "Copyright " (C) "
   * ", where (C) is one character showing a C in a circle.
   **/
  public static String COPYRIGHT_MESSAGE = "TCOP";
  /** Encoding time **/
  public static String ENCODING_TIME = "TDEN";
  /** Playlist delay **/
  public static String PLAYLIST_DELAY = "TDLY";
  /** Original release time **/
  public static String ORIGINAL_RELEASE_TIME = "TDOR";
  /** Recording time **/
  public static String RECORDING_TIME = "TDRC";
  /** Release time **/
  public static String RELEASE_TIME = "TDRL";
  /** Tagging time **/
  public static String TAGGING_TIME = "TDTG";
  public static String ENCODED_BY = "TENC";
  public static String LYRICIST = "TEXT";
  public static String FILE_TYPE = "TFLT";
  public static String INVOLVED_PEOPLE = "TIPL";
  public static String CONTENT_GROUP = "TIT1";
  public static String TITLE = "TIT2";
  public static String SUBTITLE = "TIT3";
  public static String INITIAL_KEY = "TKEY";
  public static String LANGUAGE = "TLAN";
  public static String LENGTH = "TLEN";
  public static String MUSICIAN_CREDITS = "TMCL";
  public static String MEDIA_TYPE = "TMED";
  public static String MOOD = "TMOO";
  public static String ORIGINAL_ALBUM = "TOAL";
  public static String ORIGINAL_FILENAME = "TOFN";
  public static String ORIGINAL_LYRICIST = "TOLY";
  public static String ORIGINAL_ARTIST = "TOPE";
  public static String FILE_OWNER = "TOWN";
  public static String LEAD_PERFORMERS = "TPE1";
  public static String ACCOMPANIMENT = "TPE2";
  public static String CONDUCTOR = "TPE3";
  public static String REMIXED_BY = "TPE4";
  public static String PART_OF_SET = "TPOS";
  public static String PRODUCED_NOTICE = "TPRO";
  public static String PUBLISHER = "TPUB";
  public static String TRACK_NUMBER = "TRCK";
  public static String INTERNET_RADIO_STATION_NAME = "TRSN";
  public static String INTERNET_RADIO_STATION_OWNER = "TRSO";
  public static String ALBUM_SORT_ORDER = "TSOA";
  public static String PERFORMER_SORT_ORDER = "TSOP";
  public static String TITLE_SORT_ORDER = "TSOT";
  public static String ISRC = "TSRC";
  public static String SOFTWARE_HARDWARE_SETTINGS = "TSSE";
  public static String SET_SUBTITLE = "TSST";
  public static String USER_DEFINED_TEXT_INFO = "TXXX";
  public static String YEAR = "TYER";
  public static String COMMERCIAL_INFO_URL = "WCOM";
  public static String COPYRIGHT_INFO_URL = "WCOP";
  public static String OFFICIAL_FILE_WEBPAGE_URL = "WOAF";
  public static String OFFICIAL_ARTIST_WEBPAGE_URL = "WOAR";
  public static String OFFICIAL_SOURCE_WEBPAGE_URL = "WOAS";
  public static String OFFICIAL_INTERNET_RADIO_WEBPAGE_URL = "WOAS";
  public static String PAYMENT_URL = "WPAY";
  public static String OFFICIAL_PUBLISHER_WEBPAGE_URL = "WPUB";
  public static String USER_DEFINED_URL = "WXXX";
  public static String AUDIO_ENCRYPTION = "AENC";
  public static String ATTACHED_PICTURE = "APIC";
  public static String AUDIO_SEEK_POINT_INDEX = "ASPI";
  public static String COMMENTS = "COMM";
  public static String COMMERCIAL_FRAME = "COMR";
  public static String ENCRYPTION_METHOD_REGISTRATION = "ENCR";
  public static String EQUALISATION = "EQU2";
  public static String EVENT_TIMING_CODES = "ETCO";
  public static String GENERAL_ENCAPSULATED_OBJECT = "GEOB";
  public static String GROUP_IDENTIFICATION_REGISTRATION = "GRID";
  public static String LINKED_INFORMATION = "LINK";
  public static String MUSIC_CD_IDENTIFIER = "MCDI";
  public static String MPEG_LOCATION_LOOKUP_TABLE = "MLLT";
  public static String OWNERSHIP_FRAME = "OWNE";
  public static String PRIVATE_FRAME = "PRIV";
  public static String PLAY_COUNTER = "PCNT";
  public static String POPULARIMETER = "POPM";
  public static String POSITION_SYNCHRONISATION_FRAME = "POSS";
  public static String RECOMMENDED_BUFFER_SIZE = "RBUF";
  public static String RELATIVE_VOLUME_ADJUSTMENT = "RVA2";
  public static String REVERB = "RVRB";
  public static String SEEK_FRAME = "SEEK";
  public static String SIGNATURE_FRAME = "SIGN";
  public static String SYNCHRONISED_LYRIC = "SYLT";
  public static String SYNCHRONISED_TEMPO_CODES = "SYTC";
  public static String UNIQUE_FILE_IDENTIFIER = "UFID";
  public static String TERMS_OF_USE = "USER";
  public static String UNSYNCHRONISED_LYRIC_TRANSCRIPTION = "USLT";


  /** Returns the length in bytes of all the frames contained in this object.
   * Empty frames are dropped from this calculation.
   *
   * @return the length of all the frames contained in this object.
   */
  public int getLength() {
    int length = 0;

    Iterator it = this.values().iterator();
    while (it.hasNext()) {
      ID3v2Frame frame = (ID3v2Frame) it.next();

      if (!frame.isEmpty()) {
        length += frame.getFrameLength();
      }
    }

    return length;
  }


  /** Return an array bytes containing all frames contained in this object. This
   * can be used to easily write the frames to a file. Empty frames are dropped
   * to save space.
   *
   * @return an array of bytes contain all frames contained in this object
   */
  public byte[] getBytes() {
    byte b[] = new byte[getLength()];
    int bytesCopied = 0;

    Iterator it = this.values().iterator();
    while (it.hasNext()) {
      ID3v2Frame frame = (ID3v2Frame) it.next();

      if (!frame.isEmpty()) {
        System.arraycopy(frame.getFrameBytes(), 0, b, bytesCopied,
          frame.getFrameLength());
        bytesCopied += frame.getFrameLength();
      }
    }

    return b;
  }


  /** Returns a string representation of this object. Returns the toStrings of
   * all the frames contained within seperated by line breaks.
   *
   * @return a string representation of this object
   */
  @Override
public String toString() {
    String str = new String();

    Iterator it = this.values().iterator();
    while (it.hasNext()) {
      str += it.next().toString() + "\n";
    }

    return str;
  }

}

