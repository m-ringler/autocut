package helliker.id3;

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


abstract class AbstractVBRHeader implements VBRHeader {
    static final Logger logger = Logger.getLogger(MPEGAudioFrameHeader.class.getPackage().getName());

    private int playingTime = -1;
    private int avgBitRate = -1;

    /**
    * samples per frame for the different mpeg layers
    */
    private static final float[] SAMPLES_PER_FRAME = {-1, 1152, 1152, 384};


    /**
    * Calculates the playing time and the average bitrate.
    *
    *@param layer        the layer value read by the MPEGAudioFrameHeader
    *@param mpegVersion  the version value read by the MPEGAudioFrameHeader
    *@param sampleRate   the sample rate read by the MPEGAudioFrameHeader
    */
    protected void calc(int layer, int mpegVersion, int sampleRate) {
        float tpf = SAMPLES_PER_FRAME[layer] / sampleRate;

        if (mpegVersion != MPEGAudioFrameHeader.MPEG_V_1){
            tpf /= 2;
        }

        playingTime = Math.round(tpf * getNumFrames());
        avgBitRate = Math.round(getNumBytes() * 8/(tpf * getNumFrames() * 1000));
    }


    /**
    * Returns the average bit rate of the mpeg file if a Xing header exists and
    * -1 otherwise.
    *
    *@return   the average bit rate (in kbps)
    */
    @Override
    public int getAvgBitrate() {
        return avgBitRate;
    }

    @Override
    public int getPlayingTime(){
        return playingTime;
    }

    /**
    * Returns a string representation of this object.
    *
    *@return   a string representation of this object
    */
    @Override
    public String toString() {
        return getClass().getName() + ":"
        + "\n#audio frames:   "  + getNumFrames()
        + "\n#audio bytes:    " + getNumBytes()
        + "\nPlaying time:    " + getPlayingTime() + " s"
        + "\nAverage bitrate: " + getAvgBitrate() + " kbps"
        + "\nHeader Length:   " + getLength() + " bytes";
    }

}

