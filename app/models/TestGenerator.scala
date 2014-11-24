package models

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import jp.co.flect.io.FileUtils
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.UUID
import testgen.SourceGenerator

case class TestGenerator(dir: File, sm: StorageManager, srcGen: SourceGenerator) {

  def generate(name: String, desc: String, ids: Seq[String], external: Option[String] = None, id: String = UUID.randomUUID.toString): String = {
    val testDir = new File(dir, id)
    testDir.mkdirs
    FileUtils.writeFile(new File(testDir, "id.txt"), id, "utf-8")

    val requests = ids.map(id => MessageWrapper(sm.getRequestMessage(id), sm.getResponseMessage(id)))
    if (sm.dir != testDir) {
      requests.zipWithIndex.foreach { case(msg, idx) =>
        val filename = (idx + 1).toString
        msg.request.copyTo(testDir, filename)
        msg.response.copyTo(testDir, filename)
      }
    }
    srcGen.generateTest(testDir, name, desc, requests, external)
    val zipFile = zip(testDir)
    zipFile.renameTo(new File(testDir, name + ".zip"))
    id
  }

  def zip(zipDir: File): File = {
    val outputFile = new File(dir, zipDir.getName() + ".zip")
    val zos = new ZipOutputStream(new FileOutputStream(outputFile))
    val buf = new Array[Byte](8192)
    try {
      zipDir.listFiles.foreach{ f =>
        val entry = new ZipEntry(f.getName)
        zos.putNextEntry(entry)
        val is = new FileInputStream(f)
        try {
          Iterator.continually(is.read(buf)).takeWhile(_ != -1).foreach (zos.write(buf, 0, _))
        } finally {
          is.close
        }
      }
    } finally {
      zos.close
    }
    outputFile
  }
}

