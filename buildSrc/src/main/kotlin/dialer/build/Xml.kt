package dialer.build

import java.io.File
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource

internal const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

internal object Xml {
    private fun builder() =
        DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = true
                isCoalescing = false
                isExpandEntityReferences = false
            }
            .newDocumentBuilder()

    fun parse(file: File): Document = builder().parse(file)

    fun parse(text: String): Document = builder().parse(InputSource(StringReader(text)))

    fun write(doc: Document, file: File) {
        file.parentFile.mkdirs()
        file.writeText(toString(doc))
    }

    fun toString(doc: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8")
        transformer.setOutputProperty(OutputKeys.INDENT, "no")
        val out = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toString()
    }

    fun Element.childElements(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
}
