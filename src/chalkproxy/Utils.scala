/* 
    ChalkProxy - a directory for a team's test web servers
    Copyright (C) 2012 Thomas Rynne

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    See http://www.gnu.org/copyleft/gpl.html

*/
package chalkproxy

import java.io.{InputStream,OutputStream}
import java.security.MessageDigest
import java.io.BufferedInputStream
object Utils {
  def copy(from:InputStream, to:OutputStream) {
    val buffer = new Array[Byte](1024)
    var r = from.read(buffer)
    while (r != -1) {
      to.write(buffer, 0, r)
      r = from.read(buffer)
    }
  }
  
  def bytes2Hex( bytes: Array[Byte] ): String = {
    def cvtByte( b: Byte ): String = {
      (if (( b & 0xff ) < 0x10 ) "0" else "" ) + java.lang.Long.toString( b & 0xff, 16 )
    }
    bytes.map( cvtByte( _ )).mkString.toUpperCase
  }

  def calculateHash(inputStream:InputStream) = {
    val md = MessageDigest.getInstance("MD5")
    val buffer = new Array[Byte](1024)
    val input = new BufferedInputStream(inputStream)
    Stream.continually(input.read(buffer))
      .takeWhile(_ != -1)
      .foreach(md.update(buffer, 0, _))
    input.close()
    bytes2Hex(md.digest)
  }

}