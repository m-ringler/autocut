/* Copyright (C) 2009-2010 Moritz Ringler
 * $Id: MP3SearchException.java 78 2010-12-13 20:36:22Z ringler $
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

public class MP3SearchException extends Exception{
	private static final long serialVersionUID = -8806131570080219362L;

	public MP3SearchException(Throwable cause){
        super(cause);
    }

    public MP3SearchException(String s){
        super(s);
    }
}
