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
 * This exception is thrown when an data in an id3v2 tag violates the id3v2
 * standards.
 */


public class ID3v2FormatException extends ID3Exception {

  /**
	 * 
	 */
	private static final long serialVersionUID = -6666731558595192530L;

/**
   * Create an ID3v2FormatException with a default message
   */
  public ID3v2FormatException() {
    super("ID3v2 tag is not formatted correctly.");
  }


  /**
   * Create an ID3v2FormatException with a specified message
   *
   *@param msg  the message for this exception
   */
  public ID3v2FormatException(String msg) {
    super(msg);
  }

  public ID3v2FormatException(Throwable cause){
      super(cause.getMessage());
      initCause(cause);
  }

}

