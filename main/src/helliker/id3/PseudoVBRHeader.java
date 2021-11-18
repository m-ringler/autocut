package helliker.id3;
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
/** This class serves to store the result of analyzing a VBR file. */
class PseudoVBRHeader extends AbstractVBRHeader{
    private int numFrames;
    private int numBytes;

    public PseudoVBRHeader(int frames, int bytes, int layer, int mpegVersion, int sampleRate){
        numFrames = frames;
        numBytes = bytes;
        calc(layer, mpegVersion, sampleRate);
    }

    @Override
    public boolean exists(){
        return true;
    }
    @Override
    public int getNumFrames(){
        return numFrames;
    }
    @Override
    public int getNumBytes(){
        return numBytes;
    }
    /** Returns the length of this header in bytes */
    @Override
    public int getLength(){
        return 0;
    }
}
