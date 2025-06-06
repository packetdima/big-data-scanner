package ru.packetdima.datascanner.scan.common.files

import com.github.junrar.Archive
import info.downdetector.bigdatascanner.common.Cleaner
import info.downdetector.bigdatascanner.common.IDetectFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTable
import org.apache.poi.hslf.usermodel.HSLFTextBox
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.HWPFOldDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextBox
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mozilla.universalchardet.UniversalDetector
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument
import org.odftoolkit.odfdom.doc.OdfTextDocument
import org.odftoolkit.odfdom.dom.element.table.TableTableCellElement
import org.odftoolkit.odfdom.dom.element.table.TableTableElement
import org.odftoolkit.odfdom.dom.element.table.TableTableRowElement
import org.odftoolkit.odfdom.incubator.doc.text.OdfTextParagraph
import org.odftoolkit.simple.PresentationDocument
import ru.packetdima.datascanner.common.ScanSettings
import ru.packetdima.datascanner.scan.common.Document
import ru.packetdima.datascanner.scan.functions.CertFileType
import ru.packetdima.datascanner.scan.functions.CodeFileType
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.coroutines.CoroutineContext

enum class FileType(val extensions: List<String>) : KoinComponent {
    XLSX(listOf("xlsx")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val str = StringBuilder()
            val res = Document(file.length(), file.absolutePath)
            var sample = 0
            try {
                withContext(Dispatchers.IO) {
                    FileInputStream(file).use { inputStream ->
                        ReadableWorkbook(inputStream).use { workbook ->
                            workbook.sheets.use { sheets ->
                                sheets.forEach sheet@{ sheet ->
                                    sheet?.openStream().use { rowStream ->
                                        rowStream?.forEach rowStream@{ row ->
                                            if (isSampleOverload(sample, fastScan) || !isActive) return@rowStream
                                            row?.forEach { cell ->
                                                if (cell != null) {
                                                    str.append(cell.text).append("\n")
                                                    if (str.length >= scanSettings.sampleLength || !isActive) {
                                                        res + scan(str.toString(), detectFunctions)
                                                        str.clear()
                                                        sample++
                                                        if (isSampleOverload(
                                                                sample,
                                                                fastScan
                                                            ) || !isActive
                                                        ) return@rowStream
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                res.skip()
                return res
            }
            if (str.isNotEmpty()) {
                res + withContext(context) { scan(str.toString(), detectFunctions) }
            }
            return res
        }
    },
    DOCX(listOf("docx")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val str = StringBuilder()
            val res = Document(file.length(), file.absolutePath)
            var sample = 0
            try {
                withContext(Dispatchers.IO) {
                    FileInputStream(file).use { fileInputStream ->
                        XWPFDocument(fileInputStream).use { document ->
                            document.bodyElements
                            for (elem in document.bodyElements) {
                                when (elem) {
                                    is XWPFParagraph -> elem.text
                                    is XWPFTable -> elem.text
                                    else -> ""
                                }.forEach { c ->
                                    str.append(c)
                                    if (str.length >= scanSettings.sampleLength || !isActive) {
                                        res + withContext(context) { scan(str.toString(), detectFunctions) }
                                        str.clear()
                                        sample++
                                        if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                    }
                                }
                                str.append("\n")
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                try {
                    withContext(Dispatchers.IO) {
                        FileInputStream(file).use { fileInputStream ->
                            HWPFDocument(fileInputStream).use { document ->
                                WordExtractor(document).use { extractor ->
                                    extractor.text.forEach { c ->
                                        str.append(c).append("\n")
                                        if (str.length >= scanSettings.sampleLength || !isActive) {
                                            res + withContext(context) { scan(str.toString(), detectFunctions) }
                                            str.clear()
                                            sample++
                                            if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    res.skip()
                    return res
                }
            }
            if (str.isNotEmpty() && !isSampleOverload(sample, fastScan)) {
                res + withContext(context) { scan(str.toString(), detectFunctions) }
            }
            return res
        }
    },
    PPTX(listOf("pptx", "potx", "ppsx", "pptm")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val str = StringBuilder()
            val res = Document(file.length(), file.absolutePath)
            var sample = 0
            try {
                withContext(Dispatchers.IO) {
                    FileInputStream(file).use { fileInputStream ->
                        XMLSlideShow(fileInputStream).use stream@{ presentation ->
                            presentation.slides.forEach { slide ->
                                str.append(slide.slideName).append("\n")
                                str.append(slide.title).append("\n")

                                slide.shapes.forEach { shape ->
                                    when (shape) {
                                        is XSLFTextBox -> {
                                            str.append(shape.text).append("\n")
                                            if (str.length >= scanSettings.sampleLength || !isActive) {
                                                res + withContext(context) { scan(str.toString(), detectFunctions) }
                                                str.clear()
                                                sample++
                                                if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                            }
                                        }

                                        is XSLFTable -> {
                                            shape.rows.forEach { row ->
                                                row.cells.forEach { cell ->
                                                    str.append(cell.text).append("\n")
                                                    if (str.length >= scanSettings.sampleLength || !isActive) {
                                                        res + withContext(context) {
                                                            scan(
                                                                str.toString(),
                                                                detectFunctions
                                                            )
                                                        }
                                                        str.clear()
                                                        sample++
                                                        if (isSampleOverload(
                                                                sample,
                                                                fastScan
                                                            ) || !isActive
                                                        ) return@withContext
                                                    }
                                                }
                                            }
                                        }

                                        else -> {}
                                    }
                                }
                                slide.comments.forEach { comment ->
                                    str.append(comment.text).append("\n")
                                    if (str.length >= scanSettings.sampleLength || !isActive) {
                                        res + withContext(context) { scan(str.toString(), detectFunctions) }
                                        str.clear()
                                        sample++
                                        if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                res.skip()
                return res
            }

            if (str.isNotEmpty() && !isSampleOverload(sample, fastScan)) {
                res + withContext(context) { scan(str.toString(), detectFunctions) }
            }
            return res
        }
    },
    PPT(listOf("ppt", "pps", "pot")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val str = StringBuilder()
            val res = Document(file.length(), file.absolutePath)
            var sample = 0
            try {
                withContext(Dispatchers.IO) {
                    FileInputStream(file).use { fileInputStream ->
                        HSLFSlideShow(fileInputStream).use { presentation ->
                            presentation.slides.forEach { slide ->
                                str.append(slide.slideName).append("\n")
                                str.append(slide.title).append("\n")

                                slide.shapes.forEach { shape ->
                                    when (shape) {
                                        is HSLFTextBox -> {
                                            str.append(shape.text).append("\n")
                                            if (str.length >= scanSettings.sampleLength || !isActive) {
                                                res + withContext(context) { scan(str.toString(), detectFunctions) }
                                                str.clear()
                                                sample++
                                                if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                            }
                                        }

                                        is HSLFTable -> {
                                            for (row in 0..shape.numberOfRows - 1) {
                                                for (col in 0..shape.numberOfColumns - 1) {
                                                    str.append(shape.getCell(row, col).text).append("\n")
                                                    if (str.length >= scanSettings.sampleLength || !isActive) {
                                                        res + withContext(context) {
                                                            scan(
                                                                str.toString(),
                                                                detectFunctions
                                                            )
                                                        }
                                                        str.clear()
                                                        sample++
                                                        if (isSampleOverload(
                                                                sample,
                                                                fastScan
                                                            ) || !isActive
                                                        ) return@withContext
                                                    }
                                                }
                                            }
                                        }
                                    }

                                }
                                slide.comments.forEach { comment ->
                                    str.append(comment.text).append("\n")
                                    if (str.length >= scanSettings.sampleLength || !isActive) {
                                        res + withContext(context) { scan(str.toString(), detectFunctions) }
                                        str.clear()
                                        sample++
                                        if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                res.skip()
                return res
            }

            if (str.isNotEmpty() && !isSampleOverload(sample, fastScan)) {
                res + withContext(context) { scan(str.toString(), detectFunctions) }
            }
            return res
        }
    },
    Text((1..999).map { it.toString().padStart(3, '0') } + listOf("txt", "csv", "xml", "json", "log")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val str = StringBuilder()
            val res = Document(file.length(), file.absolutePath)
            val buf = CharArray(1000)
            var sample = 0
            try {
                val encoding = UniversalDetector.detectCharset(file)
                withContext(Dispatchers.IO) {
                    FileInputStream(file).use { fileInputStream ->
                        fileInputStream.bufferedReader(charset = Charset.forName(encoding)).use { reader ->
                            var actualRead: Int
                            while (true) {
                                actualRead = reader.read(buf)
                                if (actualRead <= 0) {
                                    break
                                }

                                str.append(buf)

                                if (str.length >= scanSettings.sampleLength || !isActive) {
                                    res + withContext(context) { scan(str.toString(), detectFunctions) }
                                    str.clear()
                                    sample++
                                    if (isSampleOverload(sample, fastScan) || !isActive)
                                        return@withContext
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                res.skip()
                return res
            }
            if (str.isNotEmpty() && !isSampleOverload(sample, fastScan)) {
                res + withContext(context) { scan(str.toString(), detectFunctions) }
            }
            return res
        }
    },
    DOC(listOf("doc")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val str = StringBuilder()
            val res = Document(file.length(), file.absolutePath)
            var sample = 0
            try {
                withContext(Dispatchers.IO) {
                    FileInputStream(file).use { inputStream ->
                        WordExtractor(inputStream).use { wordExtractor ->
                            wordExtractor.text.forEach { c ->
                                str.append(c)
                                if (str.length >= scanSettings.sampleLength || !isActive) {
                                    res + withContext(context) { scan(str.toString(), detectFunctions) }
                                    str.clear()
                                    sample++
                                    if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                try {
                    withContext(Dispatchers.IO) {
                        POIFSFileSystem(file).use { inputStream ->
                            HWPFOldDocument(inputStream).use { hwpfOldDocument ->
                                hwpfOldDocument.documentText.forEach { c ->
                                    str.append(c)
                                    if (str.length >= scanSettings.sampleLength || !isActive) {
                                        res + withContext(context) { scan(str.toString(), detectFunctions) }
                                        str.clear()
                                        sample++
                                        if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    res.skip()
                    return res
                }
            }
            if (str.isNotEmpty() && !isSampleOverload(sample, fastScan)) {
                res + withContext(context) { scan(str.toString(), detectFunctions) }
            }
            return res
        }
    },
    XLS(listOf("xls")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val str = StringBuilder()
            val res = Document(file.length(), file.absolutePath)
            var sample = 0
            try {
                //Create Workbook instance holding reference to .xlsx file
                withContext(Dispatchers.IO) {
                    FileInputStream(file).use { fileInputStream ->
                        HSSFWorkbook(fileInputStream).use { workbook ->
                            val dataFormatter = DataFormatter()
                            dataFormatter.isEmulateCSV = true
                            workbook.forEach workbook@{ sheet ->
                                sheet?.forEach { row ->
                                    row?.forEach { cell ->
                                        if (cell != null) {
                                            when (cell.cellType) {
                                                CellType.NUMERIC -> str.append(dataFormatter.formatCellValue(cell))
                                                    .append("\n")

                                                CellType.STRING -> str.append(dataFormatter.formatCellValue(cell))
                                                    .append("\n")

                                                else -> {}
                                            }
                                            if (str.length >= scanSettings.sampleLength || !isActive) {
                                                res + withContext(context) { scan(str.toString(), detectFunctions) }
                                                str.clear()
                                                sample++
                                                if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                res.skip()
                return res
            }
            if (str.isNotEmpty() && !isSampleOverload(sample, fastScan)) {
                res + withContext(context) { scan(str.toString(), detectFunctions) }
            }
            return res
        }
    },
    PDF(listOf("pdf")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val str = StringBuilder()
            val res = Document(file.length(), file.absolutePath)
            var sample = 0
            try {
                withContext(Dispatchers.IO) {
                    PDDocument.load(file).use { document ->
                        PDFTextStripper().getText(document).forEach { c ->
                            str.append(c)
                            if (str.length >= scanSettings.sampleLength || !isActive) {
                                res + withContext(context) { scan(str.toString(), detectFunctions) }
                                str.clear()
                                sample++
                                if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                res.skip()
                return res
            }
            if (str.isNotEmpty() && !isSampleOverload(sample, fastScan)) {
                res + withContext(context) { scan(str.toString(), detectFunctions) }
            }
            return res
        }
    },
    ODT(listOf("odt")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val str = StringBuilder()
            val res = Document(file.length(), file.absolutePath)
            var sample = 0


            try {
                withContext(Dispatchers.IO) {
                    OdfTextDocument.loadDocument(file).contentRoot.also { content ->
                        for (contIt in 0 until content.length) {
                            when (val item = content.item(contIt)) {
                                is OdfTextParagraph -> {
                                    if (item.textContent.isNotEmpty()) {
                                        str.append(item.textContent).append("\n")
                                        if (str.length >= scanSettings.sampleLength || !isActive) {
                                            res + withContext(context) { scan(str.toString(), detectFunctions) }
                                            str.clear()
                                            sample++
                                            if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                        }
                                    }
                                }

                                is TableTableElement -> {
                                    for (rowIt in 0 until item.length) {
                                        item.item(rowIt).also { row ->
                                            if (row is TableTableRowElement) {
                                                for (celIt in 0 until row.length) {
                                                    val celElement = row.item(celIt)
                                                    if (celElement is TableTableCellElement) {
                                                        for (celContIt in 0 until celElement.length) {
                                                            celElement.item(celContIt).textContent.also { text ->
                                                                if (text.isNotEmpty()) {
                                                                    str.append(text).append("\n")
                                                                    if (str.length >= scanSettings.sampleLength || !isActive) {
                                                                        res + withContext(context) {
                                                                            scan(
                                                                                str.toString(),
                                                                                detectFunctions
                                                                            )
                                                                        }
                                                                        str.clear()
                                                                        sample++
                                                                        if (isSampleOverload(
                                                                                sample,
                                                                                fastScan
                                                                            ) || !isActive
                                                                        ) return@withContext
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                res.skip()
                return res
            }
            if (str.isNotEmpty() && !isSampleOverload(sample, fastScan)) {
                res + withContext(context) { scan(str.toString(), detectFunctions) }
            }

            return res
        }
    },
    ODP(listOf("odp", "otp")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val str = StringBuilder()
            val res = Document(file.length(), file.absolutePath)
            var sample = 0

            try {
                withContext(Dispatchers.IO) {
                    PresentationDocument.loadDocument(file).use { document ->
                        val slideIterator = document.slides
                        while (slideIterator.hasNext()) {
                            val slide = slideIterator.next()
                            str.append(slide.slideName).append("\n")

                            slide.tableList.forEach { table ->
                                table.rowList.forEach { r ->
                                    for (i in 0..r.cellCount - 1) {
                                        str.append(r.getCellByIndex(i).displayText).append("\n")
                                        if (str.length >= scanSettings.sampleLength || !isActive) {
                                            res + withContext(context) { scan(str.toString(), detectFunctions) }
                                            str.clear()
                                            sample++
                                            if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                        }
                                    }
                                }
                            }

                            val listIterator = slide.listIterator
                            while (listIterator.hasNext()) {
                                val list = listIterator.next()
                                str.append(list.header)
                                list.items.forEach {
                                    str.append(it.textContent).append("\n")
                                    if (str.length >= scanSettings.sampleLength || !isActive) {
                                        res + withContext(context) { scan(str.toString(), detectFunctions) }
                                        str.clear()
                                        sample++
                                        if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                    }
                                }
                            }
                            val textboxIterator = slide.textboxIterator
                            while (textboxIterator.hasNext()) {
                                val textbox = textboxIterator.next()
                                str.append(textbox.textContent).append("\n")
                                if (str.length >= scanSettings.sampleLength || !isActive) {
                                    res + withContext(context) { scan(str.toString(), detectFunctions) }
                                    str.clear()
                                    sample++
                                    if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                }
                            }
                            val noteListIterator = slide.notesPage?.listIterator
                            if (noteListIterator != null) {
                                while (noteListIterator.hasNext()) {
                                    val noteList = noteListIterator.next()
                                    noteList.items.forEach {
                                        str.append(it.textContent).append("\n")
                                        if (str.length >= scanSettings.sampleLength || !isActive) {
                                            res + withContext(context) { scan(str.toString(), detectFunctions) }
                                            str.clear()
                                            sample++
                                            if (isSampleOverload(sample, fastScan) || !isActive) return@withContext
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                res.skip()
                return res
            }
            if (str.isNotEmpty() && !isSampleOverload(sample, fastScan)) {
                res + withContext(context) { scan(str.toString(), detectFunctions) }
            }

            return res
        }
    },
    ODS(listOf("ods")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val str = StringBuilder()
            val res = Document(file.length(), file.absolutePath)
            var sample = 0
            try {
                withContext(Dispatchers.IO) {
                    OdfSpreadsheetDocument.loadDocument(file).use { document ->
                        document.spreadsheetTables.forEach { table ->
                            table.rowElementList.forEach { row ->
                                if (row is TableTableRowElement) {
                                    for (celIt in 0 until row.length) {
                                        val celElement = row.item(celIt)
                                        if (celElement is TableTableCellElement) {
                                            for (celContIt in 0 until celElement.length) {
                                                celElement.item(celContIt).textContent.also { text ->
                                                    if (text.isNotEmpty()) {
                                                        str.append(text).append("\n")
                                                        if (str.length >= scanSettings.sampleLength || !isActive) {
                                                            res + withContext(context) {
                                                                scan(
                                                                    str.toString(),
                                                                    detectFunctions
                                                                )
                                                            }
                                                            str.clear()
                                                            sample++
                                                            if (isSampleOverload(
                                                                    sample,
                                                                    fastScan
                                                                ) || !isActive
                                                            ) return@withContext
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                res.skip()
                return res
            }
            if (str.isNotEmpty() && !isSampleOverload(sample, fastScan)) {
                res + withContext(context) { scan(str.toString(), detectFunctions) }
            }
            return res
        }
    },
    ZIP(listOf("zip")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val res = Document(file.length(), file.absolutePath)
            var skipped = 0
            var all = 0
            var reading = true
            try {
                withContext(Dispatchers.IO) {
                    FileInputStream(file).use { fileInputStream ->
                        BufferedInputStream(fileInputStream).use { bufferedInputStream ->
                            ZipInputStream(
                                bufferedInputStream,
                                Charset.forName("windows-1251")
                            ).use { zipInputStream ->
                                var zipEntry: ZipEntry?
                                val buffer = ByteArray(2048)
                                while (reading) {
                                    zipEntry = try {
                                        zipInputStream.nextEntry.also {
                                            if (it == null)
                                                reading = false
                                        }
                                    } catch (_: NullPointerException) {
                                        null
                                    } catch (_: IllegalArgumentException) {
                                        null
                                    }
                                    if (zipEntry == null) continue

                                    // не распаковывать если расширение не из выбранных
                                    if (!selectedExtension(zipEntry.name))
                                        continue

                                    val tmpFile = File.createTempFile(
                                        "ADS_",
                                        "." + zipEntry.name.substringAfterLast(".")
                                    )
                                    try {
                                        tmpFile.outputStream().use { fileOutputStream ->
                                            fileOutputStream.buffered(buffer.size).use { bufferedOutputStream ->
                                                while (true) {
                                                    val length = zipInputStream.read(buffer)
                                                    if (length <= 0) break
                                                    bufferedOutputStream.write(buffer, 0, length)
                                                }
                                                bufferedOutputStream.flush()
                                            }
                                        }
                                        getFileType(tmpFile)?.scanFile(tmpFile, context, detectFunctions, fastScan)
                                            ?.also { doc ->
                                                if (!doc.skipped()) {
                                                    res + doc.getDocumentFields()
                                                } else {
                                                    skipped++
                                                }
                                            }
                                        all++
                                    } catch (_: IOException) {
                                        continue
                                    } finally {
                                        tmpFile.delete()
                                    }
                                    zipInputStream.closeEntry()
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                if (res.isEmpty()) {
                    res.skip()
                    return res
                }
            }
            if (skipped == all)
                res.skip()
            return res
        }
    },
    RAR(listOf("rar")) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            val res = Document(file.length(), file.absolutePath)
            var skipped = 0
            var all = 0
            try {
                withContext(Dispatchers.IO) {
                    val archive = Archive(file)
                    while (true) {
                        val fileHeader = archive.nextFileHeader() ?: break

                        if (!selectedExtension(fileHeader.fileName))
                            continue

                        val tmpFile = File.createTempFile(
                            "ADS_",
                            "." + fileHeader.fileName.substringAfterLast(".")
                        )

                        try {
                            archive.extractFile(fileHeader, tmpFile.outputStream())
                            getFileType(tmpFile)?.scanFile(tmpFile, context, detectFunctions, fastScan)?.also { doc ->
                                if (!doc.skipped()) {
                                    res + doc.getDocumentFields()
                                } else {
                                    skipped++
                                }
                            }
                            all++
                        } catch (_: IOException) {
                            continue
                        } finally {
                            tmpFile.delete()
                        }
                    }
                }
            } catch (_: Exception) {
                if (res.isEmpty()) {
                    res.skip()
                    return res
                }
            }
            if (skipped == all)
                res.skip()
            return res
        }
    },
    CERT(CertFileType.entries.flatMap { it.extensions }) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            return CertFileType
                .entries.find { it.extensions.contains(file.extension) }
                ?.scanFile(
                    file,
                    context,
                    detectFunctions,
                    fastScan
                ) ?: Document(file.length(), file.absolutePath)
                .also {
                    it.skip()
                }
        }
    },
    CODE(CodeFileType.entries.flatMap { it.extensions }) {
        override suspend fun scanFile(
            file: File,
            context: CoroutineContext,
            detectFunctions: List<IDetectFunction>,
            fastScan: Boolean
        ): Document {
            return CodeFileType.scanFile(
                file
            )
        }
    };

    abstract suspend fun scanFile(
        file: File,
        context: CoroutineContext,
        detectFunctions: List<IDetectFunction>,
        fastScan: Boolean
    ): Document

    protected fun scan(text: String, detectFunctions: List<IDetectFunction>): Map<IDetectFunction, Int> {
        val cleanText = Cleaner.Companion.cleanText(text)
        return detectFunctions.map { f ->
            f to f.scan(cleanText).takeIf { it > 0 }
        }.mapNotNull { p ->
            p.second?.let { p.first to it }
        }.toMap()
    }

    companion object : KoinComponent {
        fun getFileType(file: File): FileType? {
            return entries.find { fileType -> fileType.extensions.contains(file.extension) }
        }

        private fun selectedExtension(fileName: String): Boolean =
            entries.filter {
                scanSettings.extensions.contains(it) // Заменить на загруженные из задачи, а не из текущих настроек
            }.flatMap {
                it.extensions
            }.any { fileName.endsWith(it) }

        private fun isSampleOverload(sample: Int, fastScan: Boolean): Boolean {
            return (fastScan && sample >= scanSettings.sampleCount)
        }

        val scanSettings: ScanSettings by inject()
    }
}