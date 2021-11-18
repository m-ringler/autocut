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

public class SearchStep{
    private final int ti;
    private final int tf;
    private final float successCorr;

    /** negative times count from the end of the file */
    public SearchStep(float successCorr, int tstart_ms, int tend_ms){
        this.ti = tstart_ms;
        this.tf = tend_ms;
        this.successCorr = successCorr;
    }

    public int getStartTimeMillis()
    {
        return ti;
    }

    public int getEndTimeMillis()
    {
        return tf;
    }

    public float getSuccessCorrelation()
    {
        return successCorr;
    }
}
