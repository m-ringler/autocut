/* Copyright (C) 2010 Moritz Ringler
* $Id: MP3Search.java 13 2009-12-27 21:53:29Z ringler $
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
 
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class MP3Position
{
    private final int time_ms;
    private final int frames;
    private final long bytes;

    public MP3Position(int time_ms, int frames, long bytes)
    {
        this.time_ms = time_ms;
        this.frames = frames;
        this.bytes = bytes;
    }

    public static MP3Position getPositionForFrameCount(int framecount, File mp3)
    throws IOException, MP3SearchException
    {
        return MP3Position.getPositionsForFrameCounts(new int[]{framecount}, mp3)[0];
    }

    public static MP3Position[] getPositionsForFrameCounts(int[] framecounts, File mp3)
    throws IOException, MP3SearchException
    {
        final FileInputStream smp3 = new FileInputStream(mp3);

        MP3Position[] result = new MP3Position[framecounts.length];

        try
        {
            MP3Reader reader = new MP3Reader(smp3);
            for (int i=0; reader.hasMoreFrames(); reader.readFrame(false))
            {
                if (reader.getFrameCount() >= framecounts[i])
                {
                    result[i++] = new MP3Position(
                        reader.getMillis(),
                        reader.getFrameCount(),
                        reader.getPosition());

                    if(i == framecounts.length)
                    {
                        break;
                    }
                }
            }
        }
        finally
        {
            /* close the input stream */
            smp3.close();
        }

        return result;
    }


    public static MP3Position getPositionForTime(int time_ms, File mp3)
    throws IOException, MP3SearchException
    {
        final FileInputStream smp3 = new FileInputStream(mp3);
        MP3Position result = null;

        try
        {
            MP3Reader reader = new MP3Reader(smp3);
            while(reader.hasMoreFrames() && reader.getMillis() < time_ms)
            {
                reader.readFrame(false);
            }

            if(reader.getMillis() >= time_ms)
            {
                result = new MP3Position(
                    reader.getMillis(),
                    reader.getFrameCount(),
                    reader.getPosition());
            }
        }
        finally
        {
            smp3.close();
        }

        return result;
    }

    public int getTimeMillis()
    {
        return this.time_ms;
    }

    public int getFrameCount()
    {
        return this.frames;
    }

    public long getByteOffset()
    {
        return this.bytes;
    }
}


