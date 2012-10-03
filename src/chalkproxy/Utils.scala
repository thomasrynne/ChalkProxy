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