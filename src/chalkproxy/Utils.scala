package chalkproxy

import java.io.{InputStream,OutputStream}
object Utils {
  def copy(from:InputStream, to:OutputStream) {
    val buffer = new Array[Byte](1024)
    var r = from.read(buffer)
    while (r != -1) {
      to.write(buffer, 0, r)
      r = from.read(buffer)
    }
  }

}