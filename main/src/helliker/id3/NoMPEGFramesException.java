package helliker.id3;
/*
   Copyright (C) 2001 Jonathan Hilliker
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

import java.io.File;

/**
 * An exception to be thrown if the parser is unable to find an mpeg header.
 */
public class NoMPEGFramesException extends ID3Exception {
   /**
	 * 
	 */
	private static final long serialVersionUID = 7375737586293365726L;
private final File file;

  /**
   * Create a NoMPEGFramesException with a default message.
   */
  public NoMPEGFramesException(File file) {
    super(file + " is not a valid MPEG audio file.");
    this.file = file;
  }


  /**
   * Create a NoMPEGFramesException with a specified message.
   *
   *@param msg  the message for this exception
   */
  public NoMPEGFramesException(File file, String msg) {
    super(msg);
    this.file = file;
  }

  public File getFile(){
      return file;
  }

}

