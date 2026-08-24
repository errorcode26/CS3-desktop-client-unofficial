package com.lagradost.cloudstream3.desktop.ui.screens.profile

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream

object GifCropper {
    fun isGif(fileOrUrl: String): Boolean {
        if (fileOrUrl.endsWith(".gif", ignoreCase = true) || fileOrUrl.contains(".gif?", ignoreCase = true)) {
            return true
        }
        val file = File(fileOrUrl)
        if (file.exists() && file.isFile) {
            return isGif(file)
        }
        return false
    }

    fun isGif(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 4) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                val read = fis.read(header)
                read == 4 && isGif(header)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isGif(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 0x47.toByte() && // 'G'
            bytes[1] == 0x49.toByte() && // 'I'
            bytes[2] == 0x46.toByte() && // 'F'
            bytes[3] == 0x38.toByte()    // '8'
    }

    fun cropAndSaveGif(
        inputFile: File,
        outputFile: File,
        targetSize: Int,
        zoomScale: Float,
        panOffsetX: Float,
        panOffsetY: Float,
        previewBoxSizeDp: Double = 260.0,
    ): Boolean {
        val readers = ImageIO.getImageReadersByFormatName("gif")
        if (!readers.hasNext()) return false
        val reader = readers.next()

        val iis = ImageIO.createImageInputStream(inputFile) ?: return false
        reader.setInput(iis, false)

        val numImages = try { reader.getNumImages(true) } catch (_: Exception) { 0 }
        if (numImages <= 0) {
            try { iis.close() } catch (_: Exception) {}
            try { reader.dispose() } catch (_: Exception) {}
            return false
        }

        val writers = ImageIO.getImageWritersByFormatName("gif")
        if (!writers.hasNext()) {
            try { iis.close() } catch (_: Exception) {}
            try { reader.dispose() } catch (_: Exception) {}
            return false
        }
        val writer = writers.next()

        val outputStream = FileImageOutputStream(outputFile)
        writer.output = outputStream

        try {
            val streamMeta = writer.getDefaultStreamMetadata(null)
            writer.prepareWriteSequence(streamMeta)

            for (i in 0 until numImages) {
                val frame = reader.read(i)
                val imgW = frame.width.toDouble()
                val imgH = frame.height.toDouble()

                val baseScale = maxOf(targetSize / imgW, targetSize / imgH)
                val totalScale = baseScale * zoomScale

                val drawW = imgW * totalScale
                val drawH = imgH * totalScale

                val panScaleFactor = targetSize / previewBoxSizeDp
                val drawX = (targetSize - drawW) / 2.0 + (panOffsetX * panScaleFactor)
                val drawY = (targetSize - drawH) / 2.0 + (panOffsetY * panScaleFactor)

                val cropped = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB)
                val g2d = cropped.createGraphics()
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.drawImage(frame, drawX.toInt(), drawY.toInt(), drawW.toInt(), drawH.toInt(), null)
                g2d.dispose()

                // Extract delay
                val metadata = reader.getImageMetadata(i)
                val delayTime = extractDelay(metadata)

                // Write frame to sequence
                val imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB)
                val writeMeta = writer.getDefaultImageMetadata(imageTypeSpecifier, null)
                configureMetadata(writeMeta, delayTime, i == 0)

                val iioImage = IIOImage(cropped, null, writeMeta)
                writer.writeToSequence(iioImage, null)
            }

            writer.endWriteSequence()
            return true
        } catch (_: Exception) {
            return false
        } finally {
            try { outputStream.close() } catch (_: Exception) {}
            try { iis.close() } catch (_: Exception) {}
            try { reader.dispose() } catch (_: Exception) {}
            try { writer.dispose() } catch (_: Exception) {}
        }
    }

    private fun extractDelay(metadata: IIOMetadata): Int {
        try {
            val root = metadata.getAsTree("javax_imageio_gif_image_1.0") as? IIOMetadataNode ?: return 10
            for (i in 0 until root.length) {
                val node = root.item(i)
                if (node.nodeName.equals("GraphicControlExtension", ignoreCase = true)) {
                    val delay = node.attributes.getNamedItem("delayTime")?.nodeValue?.toIntOrNull()
                    if (delay != null && delay > 0) return delay
                }
            }
        } catch (_: Exception) {}
        return 10 // Default 100ms in centiseconds
    }

    private fun configureMetadata(metadata: IIOMetadata, delayTimeCentiseconds: Int, isFirstFrame: Boolean) {
        val metaFormatName = metadata.nativeMetadataFormatName ?: return
        val root = metadata.getAsTree(metaFormatName) as? IIOMetadataNode ?: return

        var graphicsControlExtensionNode: IIOMetadataNode? = null
        var appExtensionsNode: IIOMetadataNode? = null

        for (i in 0 until root.length) {
            val node = root.item(i)
            if (node.nodeName.equals("GraphicControlExtension", ignoreCase = true)) {
                graphicsControlExtensionNode = node as? IIOMetadataNode
            }
            if (node.nodeName.equals("ApplicationExtensions", ignoreCase = true)) {
                appExtensionsNode = node as? IIOMetadataNode
            }
        }

        if (graphicsControlExtensionNode == null) {
            graphicsControlExtensionNode = IIOMetadataNode("GraphicControlExtension")
            root.appendChild(graphicsControlExtensionNode)
        }
        graphicsControlExtensionNode.setAttribute("disposalMethod", "restoreToBackgroundColor")
        graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE")
        graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE")
        graphicsControlExtensionNode.setAttribute("delayTime", delayTimeCentiseconds.toString())
        graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0")

        if (isFirstFrame) {
            if (appExtensionsNode == null) {
                appExtensionsNode = IIOMetadataNode("ApplicationExtensions")
                root.appendChild(appExtensionsNode)
            }
            val appNode = IIOMetadataNode("ApplicationExtension")
            appNode.setAttribute("applicationID", "NETSCAPE")
            appNode.setAttribute("authenticationCode", "2.0")
            // 01 00 00 = loop continuously
            appNode.userObject = byteArrayOf(0x1, 0x0, 0x0)
            appExtensionsNode.appendChild(appNode)
        }

        try {
            metadata.setFromTree(metaFormatName, root)
        } catch (_: Exception) {}
    }
}
