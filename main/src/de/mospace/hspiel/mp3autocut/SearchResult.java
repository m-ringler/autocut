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
import java.io.IOException;

public class SearchResult
{
    private final MP3Position start;
    private final MP3Position end;
    private final float correlation;

    public SearchResult(MP3Position start, MP3Position end, float correlation)
    {
        this.start = start;
        this.end = end;
        this.correlation = correlation;
    }

    public MP3Position getStart()
    {
        return this.start;
    }

    public MP3Position getEnd()
    {
        return this.end;
    }

    public float getCorrelation()
    {
        return this.correlation;
    }

    SearchResult fillInMissingPositionFields(File mp3) throws IOException, MP3SearchException
    {
        MP3Position[] positions = MP3Position.getPositionsForFrameCounts(
                new int[]{ this.getStart().getFrameCount(), this.getEnd().getFrameCount() },
                mp3);
        return new SearchResult(positions[0], positions[1], this.getCorrelation());
    }
}

