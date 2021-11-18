package helliker.id3;
// CHANGED BY MORITZ RINGLER (mr) for mp3dings
// $Id: ID3v2Tag.java,v 1.16 2006/08/12 12:38:37 ringler Exp $
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.logging.Logger;

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

public final class APICImage{
    private static final Logger logger = Logger.getLogger(APICImage.class.getPackage().getName());
    private String description = "";
    private String mimeType;
    private URL url;
    private byte[] imageData;
    private PictureType type = PictureType.Other;

    public static final String MIME_TYPE_LINK = "-->";
    public static final String MIME_TYPE_PNG = "image/png";
    public static final String MIME_TYPE_JPEG = "image/jpeg";
    public static final String MIME_TYPE_GENERIC = "image/";

    /** @throws NullPointerException if any of the arguments is null
        @throws IllegalArgumentException if imageData.length == 0
      */
    public APICImage(String mimeType, byte[] imageData){
        if(imageData.length == 0){
            throw new IllegalArgumentException("image data must have length >= 1");
        }
        this.mimeType = mimeType.toString(); //throws NPE if necessary
        this.imageData = imageData;
        this.url = null;
    }

    /** @throws NullPointerException if the argument is null */
    public APICImage(URL url){
        url.toString(); //throws NPE if necessary
        this.url = url;
        this.imageData = null;
        mimeType = MIME_TYPE_LINK;
    }

    /** This package private constructor parses an APIC Id3v2 frame. */
    APICImage(ID3v2Frame apicframe) throws ID3v2FormatException{
        final byte[] data = apicframe.getFrameData();

        //read encoding byte
        final int iencoding = data[0];
        if(iencoding < 0 || iencoding >= ID3v2Frame.ENC_TYPES.length){
            throw new ID3v2FormatException("Unknown encoding byte in APIC frame 0x" + Integer.toHexString(iencoding));
        }
        final String encoding = ID3v2Frame.ENC_TYPES[iencoding];
        //System.out.println("Encoding " + encoding);

        //read mime type
        int start = 1;
        int end = 1;
        for(end = start; end < data.length; end++){
            if(data[end] == 0x00){
                break;
            }
        }
        if(end +3 >= data.length){
            throw new ID3v2FormatException("Premature end of APIC frame.");
        }
        int len = end - start;
        /* Spec says: In the event that the MIME media type name is omitted,
            "image/" will be implied. */
        try{
        mimeType = (len == 0)? MIME_TYPE_GENERIC :
            new String(data, start, len, "ISO-8859-1");
        } catch (UnsupportedEncodingException ex){
            throw new Error(ex);
        }
        //this error seems to be common, we fix it directly on reading
        if("image/jpg".equals(mimeType)){
            mimeType = "image/jpeg";
        }
        //System.out.println("Mime Type " + mimeType);

        //read picture type byte
        int ipicType = data[end + 1];
        if(ipicType < 0 || ipicType >= PictureType.length()){
            throw new ID3v2FormatException("Unknown picture type byte in APIC frame 0x" + Integer.toHexString(ipicType));
        }
        type = PictureType.valueOf(ipicType);
        //System.out.println("Type " + type);

        //read description
        start = end + 2;
        int dataOffset = ID3v2Frame.getValOffset(data, 0, start);
        if(dataOffset == data.length){
            throw new ID3v2FormatException("Premature end of APIC frame.");
        }
        end = dataOffset;
        while(data[end - 1] == 0x00){
            end--;
        }
        len = end - start;
        try{
            description = (len <= 0)
            ? ""
            : new String(data, start, len, encoding);
        } catch (UnsupportedEncodingException ex){
            throw new Error(ex);
        }
        //System.out.println("Description " + description);

        //read url or image data
        start = dataOffset;
        end = data.length;
        len = end - start;
        if("-->".equals(mimeType)){
            try{
                url = new URL(new String(data, start, len, "ISO-8859-1"));
            } catch (UnsupportedEncodingException ex){
                throw new Error(ex);
            } catch (MalformedURLException ex){
                throw new ID3v2FormatException(ex);
            }
            //System.out.println("URL " + url);
        } else {
            imageData = new byte[len];
            System.arraycopy(data, start, imageData, 0, len);
            //System.out.println("imageData: " + len + " bytes");
        }

    }

    /**
     * Returns the value of type.
     */
    public PictureType getType()
    {
        return type;
    }

    /**
     * Sets the value of type.
     * @param type The value to assign type.
     * @throws NullPointerException if type is null
     */
    public void setType(PictureType type)
    {
        type.toString(); //called for the side effect of possibly throwing a NPE
        this.type = type;
    }


     /** Returns the value of description.
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Sets the value of description.
     * @param description The value to assign description.
     * @throws NullPointerException if description is null
     * @throws IllegalArgumentException if description.length() > 64
     */
    public void setDescription(String description)
    {
        if(description.length() > 64){
            throw new IllegalArgumentException("Description must not have more than 64 characters.");
        }
        this.description = description;
    }

    /**
     * Returns the value of mimeType.
     */
    public String getMimeType()
    {
        return mimeType;
    }


    /**
     * Returns the value of url.
     */
    public URL getURL()
    {
        return url;
    }

    /**
     * Sets the value of url.
     * @param url The value to assign url.
     */
    public void setURL(URL url)
    {
        if(url == null){
            throw new IllegalArgumentException("url must be non-null.");
        }
        this.url = url;
        this.mimeType = MIME_TYPE_LINK;
        this.imageData = null;
    }

    /**
     * Returns the value of imageData.
     */
    public byte[] getImageData()
    {
        return imageData;
    }

    /**
     * Sets the value of imageData.
     * @param imageData The value to assign imageData.
     */
    public void setImageData(byte[] imageData, String mimeType)
    {
        if(imageData.length == 0){
            throw new IllegalArgumentException("image data must have length >= 1");
        }
        this.imageData = imageData;
        this.mimeType = mimeType.toString(); //will throw NPE if necessary
        this.url = null;
    }

    @Override
    public boolean equals(Object o){
        if(this == o){
            return true;
        }
        boolean result = false;
        if(o instanceof APICImage){
            APICImage other = (APICImage) o;
            result = (type == other.type);
            result = result && (description == null)
                    ? (other.description == null)
                    : description.equals(other.description);
            result = result && ((url == null)
                    ? (other.url == null)
                    : url.equals(other.url));
            result = result && ((imageData == null)
                    ? (other.imageData == null)
                    : imageData.equals(other.imageData));
            result = result && ((mimeType == null)
                    ? (other.mimeType == null)
                    : mimeType.equals(other.mimeType));
        }
        return result;
    }

    @Override
    public int hashCode(){
        int result = 19;
        result = 43 * result + type.ordinal();
        result = 43 * result +
                    ((description == null)? 0 : description.hashCode());
        result = 43 * result +
                    ((mimeType == null)? 0 : mimeType.hashCode());
        result = 43 * result +
                    ((imageData == null)? 0 : imageData.hashCode());
        result = 43 * result +
                    ((url == null)? 0 : url.hashCode());
        return result;
    }

    @Override
    public String toString(){
        StringBuffer sb = new StringBuffer();
        if(description != null){
            sb.append(description).append(' ');
        }
        sb.append('(').append(type.name()).append(", ");
        if(url == null){
            sb.append(mimeType).append(", ");
            sb.append(Math.ceil(imageData.length/1000)).append(" kB");
        } else {
            sb.append(url);
        }
        return sb.append(')').toString();
    }

    byte[] toFrameData(){
        final CharsetEncoder isolatinEncoder =
                Charset.forName("ISO-8859-1").newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        // encode description
        ByteBuffer bdesc = null;
        boolean isoLatin = true;
        if( description == null || description.length() == 0){
            bdesc = ByteBuffer.allocate(0);
        } else {
            isolatinEncoder.reset();
            isoLatin = isolatinEncoder.canEncode(description);
            final CharsetEncoder encoder = (isoLatin)
                ? isolatinEncoder
                : Charset.forName("UTF-16").newEncoder();
            encoder.reset();
            // Spec says: The description has a maximum length of 64 characters
            try{
            bdesc = encoder.encode(
                CharBuffer.wrap(description, 0,
                    Math.min(description.length(), 64)));
            } catch (CharacterCodingException ex){
                logger.warning(ex.toString());
                logger.warning("APIC: Omitting description");
                bdesc = ByteBuffer.allocate(0);
            }
        }

        final int size =
            1 + //encoding byte
            mimeType.length() + 1 + //null-terminated mime type
            1 + // picture type
            bdesc.limit() + // description length
            ((isoLatin)? 1 : 2) + //null terminator(s)
            ((url == null)? imageData.length : url.toString().length());

        final byte[] result = new byte[size];
        int pos = 0;

        //write encoding
        result[pos++] = (byte) (isoLatin ? 0x00 : 0x01);

        //write null-terminated mime type
        isolatinEncoder.reset();
        ByteBuffer bmime = null;
        try{
            bmime = isolatinEncoder.encode(CharBuffer.wrap(mimeType));
        } catch (CharacterCodingException ex){
            logger.warning(ex.toString());
            logger.warning("APIC: Omitting mime type.");
            bmime = ByteBuffer.allocate(0);
        }
        int len = bmime.limit();
        bmime.get(result, pos, len);
        pos += len;
        result[pos++] = 0x00;

        //write picture type
        result[pos++] = type.byteValue();

        //write null-terminated description
        len = bdesc.limit();
        bdesc.get(result, pos, len);
        pos += len;
        result[pos++] = 0x00;
        if(!isoLatin){
            result[pos++] = 0x00;
        }

        //copy image data or write URL
        if(url == null){
            len = imageData.length;
            System.arraycopy(imageData, 0, result, pos, imageData.length);
            pos += len;
        } else {
            isolatinEncoder.reset();
            ByteBuffer burl = null;
            try{
                burl = isolatinEncoder
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .encode(CharBuffer.wrap(url.toString()));
            } catch (CharacterCodingException ex){
                throw new Error(ex);
            }
            len = burl.limit();
            burl.get(result, pos, len);
            pos += len;
        }
        assert(pos == result.length);
        return result;
    }

	/** typesafe enum for picture type */
    public static final class PictureType{
        private final int byteval;
        private final String name;
        private static final PictureType[] values = new PictureType[21];
        private static int ordinal = 0;

        private PictureType(String name){
            byteval = ordinal++;
            this.name = name;
            values[byteval] = this;
        }

        @Override
        public String toString(){
            return name;
        }

        public String name(){
            return name;
        }

        public int ordinal(){
            return byteval;
        }

        public byte byteValue(){
            return (byte) byteval;
        }

        public static final PictureType Other = new PictureType("Other");                                 //$00  Other
        public static final PictureType FileIcon32 = new PictureType("FileIcon32x32");                    //$01  32x32 pixels 'file icon' (PNG only)
        public static final PictureType FileIconOther = new PictureType("FileIconOther");                 //$02  Other file icon
        public static final PictureType CoverFront = new PictureType("CoverFront");                       //$03  Cover (front)
        public static final PictureType CoverBack = new PictureType("CoverBack");                         //$04  Cover (back)
        public static final PictureType LeafletPage = new PictureType("LeafletPage");                     //$05  Leaflet page
        public static final PictureType Media = new PictureType("Media");                                 //$06  Media (e.g. lable side of CD)
        public static final PictureType LeadArtist = new PictureType("LeadArtist");                       //$07  Lead artist/lead performer/soloist
        public static final PictureType Artist = new PictureType("Artist");                               //$08  Artist/performer
        public static final PictureType Conductor = new PictureType("Conductor");                         //$09  Conductor
        public static final PictureType BandOrOrchestra = new PictureType("BandOrOrchestra");             //$0A  Band/Orchestra
        public static final PictureType Composer = new PictureType("Composer");                           //$0B  Composer
        public static final PictureType Lyricist = new PictureType("Lyricist");                           //$0C  Lyricist/text writer
        public static final PictureType RecordingLocation = new PictureType("RecordingLocation");         //$0D  Recording Location
        public static final PictureType DuringRecording = new PictureType("DuringRecording");             //$0E  During recording
        public static final PictureType DuringPerformance = new PictureType("DuringPerformance");         //$0F  During performance
        public static final PictureType MovieCapture = new PictureType("MovieCapture");                   //$10  Movie/video screen capture
        public static final PictureType BrightColouredFish = new PictureType("BrightColouredFish");       //$11  A bright coloured fish
        public static final PictureType Illustration = new PictureType("Illustration");                   //$12  Illustration
        public static final PictureType BandOrArtistLogo = new PictureType("BandOrArtistLogo");           //$13  Band/artist logotype
        public static final PictureType PublisherOrStudioLogo = new PictureType("PublisherOrStudioLogo"); //$14  Publisher/Studio logotype

        /** @throws ArrayIndexOutOfBoundsException if bbyte is out of range */
        public static PictureType valueOf(int bbyte){
            return values[bbyte];
        }

        /** @throws IllegalArgumentException if name is not one of the known picture types
        *   @throws NullPointerException if name is null
        */
        public static PictureType valueOf(String name){
            for(int i=0; i<ordinal; i++){
                if(name.equals(values[i].name())){
                    return values[i];
                }
            }
            throw new IllegalArgumentException("Unknown PictureType " + name);
        }

        public static int length(){
            return ordinal;
        }

        public static PictureType[] values(){
            return values.clone();
        }
    }

}
