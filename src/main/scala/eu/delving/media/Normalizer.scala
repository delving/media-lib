package eu.delving.media

import org.slf4j.LoggerFactory
import org.im4java.core.{IdentifyCmd, IMOperation, ConvertCmd}
import java.io.{InputStreamReader, BufferedReader, InputStream, File}
import org.im4java.process.OutputConsumer
import org.apache.commons.io.FileUtils


/**
 * Normalizes a TIF prior to tiling.
 * Here we add all sorts of tricks we need to do in order to produce tiles that are compatible with the IIP Image Server for PTIF tiling.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object Normalizer {

  private val log = LoggerFactory.getLogger("eu.delving.media")

  /**
   * Normalizes a file to be usable for tiling and presentation for DeepZoom
   * @param sourceImage the source image to be normalized
   * @param targetDirectory the target directory to which the normalized file should be written to
   * @return an optional normalized file, if normalization took place
   */
  def normalize(sourceImage: File, targetDirectory: File): Option[File] = {

    var source: File = sourceImage
    val destination = new File(targetDirectory, sourceImage.getName)

    val hasBeenNormalized = identifyLargestLayer(source) != None || !isRGB(source)

    identifyLargestLayer(source).map { index =>
      log.info("Image %s has multiple layers, normalizing to just one...".format(source.getName))
      val convertCmd = new ConvertCmd
      val convertOp = new IMOperation
      convertOp.addRawArgs(source.getAbsolutePath + "[%s]".format(index))
      convertOp.addRawArgs(destination.getAbsolutePath)
      convertCmd.run(convertOp)
      source = destination
    }

    if (!isRGB(source)) {
      log.info("Image %s isn't RGB encoded, converting...".format(source.getName))

      if (isGrayscale(source)) {
        log.info("Image %s is Greyscale, converting to CMYK first to get the right colorspace when converting back...".format(source.getName))
        // GraphicsMagick considers Grayscale to be a subset of RGB, so it won't change the type when converting directly to RGB
        // so we first go over to CMYK and then back to RGB
        convertColorspace(targetDirectory, source, destination, "CMYK")
        source = destination
      }

      convertColorspace(targetDirectory, source, destination, "RGB")
    }

    if (hasBeenNormalized) {
      Some(destination)
    } else {
      None
    }
  }

  private def convertColorspace(targetDirectory: File, source: File, destination: File, colorspace: String) {
    val converted = new File(targetDirectory, colorspace + "_" + source.getName)
    val convertCmd = new ConvertCmd
    val convertOp = new IMOperation
    convertOp.colorspace(colorspace)
    convertOp.addImage(source.getAbsolutePath)
    convertOp.addImage(converted.getAbsolutePath)
    convertCmd.run(convertOp)
    if (converted.exists()) {
      if (converted.getParentFile.getAbsolutePath == targetDirectory.getAbsoluteFile) {
        FileUtils.deleteQuietly(source)
      }
      FileUtils.moveFile(converted, destination)
    }
  }

  private def identifyLargestLayer(sourceImage: File): Option[Int] = {
    val identified = identify(sourceImage, { op => })
    if (identified.length > 1) {
      // gm identify gives us lines like this:
      // 2006-011.tif TIFF 1000x800+0+0 DirectClass 8-bit 3.6M 0.000u 0:01
      // we want to fetch the 1000x800 part and know which line is da biggest
      val largestLayer = identified.map { line =>
        val Array(width: Int, height: Int) = line.split(" ")(2).split("\\+")(0).split("x").map(Integer.parseInt(_))
        (width, height)
      }.zipWithIndex.foldLeft((0, 0), 0) { (r: ((Int, Int), Int), c: ((Int, Int), Int)) =>
        if (c._1._1 * c._1._2 > r._1._1 * r._1._2) c else r
      }
      val largestIndex = largestLayer._2
      Some(largestIndex)
    } else {
      None
    }
  }

  private def isRGB(sourceImage: File): Boolean = isColorspace(sourceImage, "RGB")

  private def isGrayscale(sourceImage: File): Boolean = isColorspace(sourceImage, "Grayscale")

  private def isColorspace(sourceImage: File, colorspace: String) = {
    val identified = identify(sourceImage, { _.format("%[colorspace]") })
    log.info(s"Identified colorspace of image ${sourceImage.getAbsolutePath} as ${colorspace.mkString(", ")}")
    identified.headOption.map { c: String => c.contains(colorspace) }.getOrElse(false)
  }

  private def identify(sourceImage: File, addParameters: IMOperation => Unit): Seq[String] = {
    val identifyCmd = new IdentifyCmd(false)
    val identifyOp = new IMOperation
    var identified: List[String] = List()
    identifyCmd.setOutputConsumer(new OutputConsumer() {
      def consumeOutput(is: InputStream) {
        val br = new BufferedReader(new InputStreamReader(is))
        identified = Stream.continually(br.readLine()).takeWhile(_ != null).toList
      }
    })

    addParameters(identifyOp)
    identifyOp.addImage(sourceImage.getAbsolutePath)

    identifyCmd.run(identifyOp)
    identified.toSeq
  }

}
