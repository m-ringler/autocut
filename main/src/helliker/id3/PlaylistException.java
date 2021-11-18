package helliker.id3;

/*
   Copyright (C) 2001 Jonathan Hilliker
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
 * Description: This exception is thrown by the Playlist class when an error is
 * encountered while parsing an existing playlist.
 */


public class PlaylistException extends ID3Exception {

  /**
	 * 
	 */
	private static final long serialVersionUID = 7713625183958656906L;


/**
   * Create a PlaylistException with a default message
   */
  public PlaylistException() {
    super("The playlist file is corrupt.");
  }


  /**
   * Create an PlaylistException with a specified message
   *
   *@param msg  the message for this exception
   */
  public PlaylistException(String msg) {
    super(msg);
  }

}

