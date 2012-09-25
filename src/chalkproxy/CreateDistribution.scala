package chalkproxy
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.OutputStream
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.util.jar.JarOutputStream
import java.util.jar.JarInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.jar.Attributes
import java.util.jar.Manifest
import scala.io.Source
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.util.zip.GZIPOutputStream
import java.io.BufferedOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry

object CreateDistribution {

  def main(args:Array[String]) {
    val version = if (args.length == 0) "undefined" else args(0)
    createOneJar(new File("chalkproxy.jar"))
    createTgz(version, new File("chalkproxy-"+version+".tgz"))
  }
  
  private def createOneJar(file:File) {
    val jar = initJar(file)
    addOneJarBoot(jar)
    addChalkProxyJar(jar)
    addLibs(jar)
    new File("assets").listFiles().foreach(copyToJar(_, "", jar))
    jar.close()
  }
  
  private def createTgz(version:String, file:File) {
    val tgz = new TarArchiveOutputStream(
          new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
    addFile(tgz, version, new File("chalkproxy.jar"), false)
    addFile(tgz, version, new File("start"), true)
    addFile(tgz, version, new File("stop"), true)
    addFile(tgz, version, new File("distribution-jars/readme"), false)
    addFile(tgz, version, new File("distribution-jars/run"), true)
    tgz.close()
  }
  
  private def addFile(tgz:TarArchiveOutputStream, version:String, file:File, executable:Boolean) {
    val entry = new TarArchiveEntry(file, "chalkproxy-" + version + "/"+file.getName())
    if (executable) entry.setMode(0070505)
    tgz.putArchiveEntry(entry)
    val fis = new FileInputStream(file)
    Utils.copy(fis, tgz)
    fis.close()
    tgz.closeArchiveEntry()
  }
  
  private def initJar(file:File) = {
    val manifest = new Manifest()
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0")
    manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "com.simontuffs.onejar.Boot")
    manifest.getMainAttributes().put(new Attributes.Name("One-Jar-Main-Class"), "chalkproxy.Run")
    val out = new FileOutputStream(file)
    new JarOutputStream(out, manifest)
  }
  
  private def addFile(jar:JarOutputStream, file:File) {
    jar.putNextEntry(new ZipEntry("lib/" + file.getName))
    Utils.copy(new FileInputStream(file), jar)
    jar.closeEntry()
  }
  
  private def addLibs(jar:JarOutputStream) {
    addDir(jar, "lib/")
    for (file <- new File("lib").listFiles()) {
      addFile(jar, file)
    }
    addFile(jar, new File("distribution-jars/scala-library.jar"))
  }
  
  private def addOneJarBoot(jar:JarOutputStream) {
    val oneJar = new JarInputStream(new FileInputStream("distribution-jars/one-jar-boot-0.97.jar"))
    var e = oneJar.getNextJarEntry()
    while (e != null) {
      if (!e.getName.startsWith("src") && e.getName != "boot-manifest.mf") {
        jar.putNextEntry(new ZipEntry(e.getName))
        Utils.copy(oneJar, jar)
        jar.closeEntry()
      }
      e = oneJar.getNextJarEntry()
    }
  }
  
  private def addChalkProxyJar(jar:JarOutputStream) {
    addDir(jar, "main/")
    jar.putNextEntry(new ZipEntry("main/" + "main.jar"))
    val buffer = new ByteArrayOutputStream() //need to buffer so the internal jar can be closed
    val mainJar = new JarOutputStream(buffer)
    new File("out").listFiles().foreach{ f => copyToJar(f, "", mainJar) }
    mainJar.close()
    Utils.copy(new ByteArrayInputStream(buffer.toByteArray()), jar)
    jar.closeEntry()
  }
  
  private def addDir(jar:JarOutputStream, name:String) {
    jar.putNextEntry(new ZipEntry(name))
    jar.closeEntry()
  }
  
  private def copyToJar(file:File, prefix:String, jar:JarOutputStream) {
    if (file.isDirectory()) {
      val dirName = prefix + file.getName + "/"
      addDir(jar, dirName)
      file.listFiles().foreach(f => copyToJar(f, dirName, jar))
    } else {
      jar.putNextEntry(new ZipEntry(prefix + file.getName))
      Utils.copy(new FileInputStream(file), jar)
      jar.closeEntry()
    }
  }
}