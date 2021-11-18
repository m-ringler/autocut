/* Copyright (C) 2009-2010 Moritz Ringler
 * $Id: MP3Reader.java 78 2010-12-13 20:36:22Z ringler $
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

import java.io.InputStream;
import java.nio.ShortBuffer;
import javazoom.jl.decoder.*;

/** Reads samples from an MP3 file
 */
public class MP3Reader
{
    private boolean moreFrames = true;
    private final Bitstream stream;
    private Decoder decoder;
    private Header header;
    private int frameCount = 0;
    private double milliseconds = 0;
    long position = 0;
    static public final int BUFFER_SIZE = 6144;
    private final ShortBuffer output = ShortBuffer.allocate(BUFFER_SIZE);

    /**
     * Creates a new converter instance.
     */
    public MP3Reader(InputStream in)
    {
        this.stream = new Bitstream(in);/*new Bitstream(
            (in instanceof BufferedInputStream)
            ? (BufferedInputStream) in
            : new BufferedInputStream(in)
        );*/
        final Decoder.Params decoderParams = Decoder.getDefaultParams();
        decoderParams.setOutputChannels(OutputChannels.DOWNMIX);
        decoder = new Decoder(decoderParams);
        decoder.setOutputBuffer(new ShortOBuffer(output));
    }

    public void close() throws MP3SearchException{
        moreFrames = false;
        try{
            stream.close();
        } catch (BitstreamException bex){
            throw new MP3SearchException(bex);
        }
    }

    public boolean hasMoreFrames(){
        return moreFrames;
    }

    public Header getLastHeader(){
        return header;
    }

    public int getFrameCount(){
        return frameCount;
    }

    public int getMillis()
    {
        return (int)this.milliseconds;
    }

    public ShortBuffer getOutput(){
        return output;
    }

    public float secs_per_frame(){
        float result = 0;
        if( getLastHeader() != null){
            result = getLastHeader().ms_per_frame()/1000.0f;
        }
        return result;
    }

    public void readFrame() throws MP3SearchException{
        readFrame(true);
    }

    public long getPosition()
    {
        // return this.position;
        return stream.getPosition();
    }

    public void readFrame(boolean decode) throws MP3SearchException{
        try{
            Header xheader = stream.readFrame();
            if (xheader == null){
                this.moreFrames = false;
            } else {
                header = xheader;
                if (position == 0l)
                {
                    position = stream.header_pos();
                }
                position += header.framesize + 4; // 4 bytes header size
                frameCount++;
                milliseconds += header.ms_per_frame();
                if(decode){
                    decoder.decodeFrame(header, stream);
                }
                stream.closeFrame();
            }
        } catch (BitstreamException bex){
            throw new MP3SearchException(bex);
        } catch (DecoderException dex){
            throw new MP3SearchException(dex);
        }
    }

    public static class ShortOBuffer extends Obuffer{
        private ShortBuffer buff;
        public ShortOBuffer(ShortBuffer sbuff){
            buff = sbuff;
        }

        @Override
        public void append(int channel, short value){
            //System.err.println("(" + channel + ") " + value);
            //if(channel != 0){
            //    throw new IllegalArgumentException("Attempting to add to channel "+ channel + ". Only one channel allowed.");
            //}
            if (channel == 0){
                buff.put(value);
            }
        }

        @Override
        public void close(){
            // does nothing.
        }

        /**
        * Write the samples to the file (Random Acces).
        */
        @Override
        public void write_buffer(int val)
        {
            // does nothing.
        }

        @Override
        public void clear_buffer()
        {
            buff.clear();
        }

        @Override
        public void set_stop_flag()
        {
            // does nothing.
        }

    }
}