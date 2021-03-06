package helliker.id3;

/*
 * Copyright (C) 2002 Jonathan Hilliker
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
/**
 *  A top-level exception for the library.
 */

public class ID3Exception extends Exception {

  /**
	 * 
	 */
	private static final long serialVersionUID = -8095095510238008366L;

/**
   * Creates an ID3Exception with a default message
   *
   */
  public ID3Exception() {
    super("ID3Exception");
  }

  /**
   * Creates an ID3Exception with a specified message
   *
   * @param msg the message for this exception
   */
  public ID3Exception(String msg) {
    super(msg);
  }

}// ID3Exception
