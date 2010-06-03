package unfiltered.request

import javax.servlet.ServletRequest
import javax.servlet.http.HttpServletRequest

object HTTPS {
  def unapply(req: HttpServletRequest) = 
    if (req.getProtocol.equalsIgnoreCase("HTTPS")) Some(req)
    else None
}

class Method(method: String) {
  def unapply(req: HttpServletRequest) = 
    if (req.getMethod.equalsIgnoreCase(method)) Some(req)
    else None
}

object GET extends Method("GET")
object POST extends Method("POST")
object PUT extends Method("PUT")
object DELETE extends Method("DELETE")
object HEAD extends Method("HEAD")

object Path {
  def unapply(req: HttpServletRequest) = Some((req.getRequestURI, req))
}
object Seg {
  def unapply(path: String): Option[List[String]] = path.split("/").toList match {
    case "" :: rest => Some(rest) // skip a leading slash
    case all => Some(all)
  }
}

class RequestHeader(name: String) {
  def unapplySeq(req: HttpServletRequest): Option[Seq[String]] = { 
    def headers(e: java.util.Enumeration[_]): List[String] =
      if (e.hasMoreElements) e.nextElement match {
        case v: String => v :: headers(e)
        case _ => headers(e)
      } else Nil
    Some(headers(req.getHeaders(name)))
  }
}
object IfNoneMatch extends RequestHeader("If-None-Match")


object InStream {
  def unapply(req: HttpServletRequest) = Some(req.getInputStream, req)
}

object Read {
  def unapply(req: HttpServletRequest) = Some(req.getReader, req)
}

object Bytes {
  def unapply(req: HttpServletRequest) = {
    val InStream(in, _) = req
    val bos = new java.io.ByteArrayOutputStream
    val ba = new Array[Byte](4096)
    /* @scala.annotation.tailrec */ def read {
      val len = in.read(ba)
      if (len > 0) bos.write(ba, 0, len)
      if (len >= 0) read
    }
    read
    in.close
    bos.toByteArray match {
      case Array() => None
      case ba => Some(ba, req)
    }
  }
}

object Params {
  /** Dress a Java Enumeration in Scala Iterator clothing */
  case class JEnumerationIterator[T](e: java.util.Enumeration[T]) extends Iterator[T] {
    def hasNext: Boolean =  e.hasMoreElements()
    def next: T = e.nextElement()
  }
  /**
    Given a req, extract the request params into a (Map[String, Seq[String]], requset).
    The Map is assigned a default value of Nil, so param("p") would return Nil if there
    is no such parameter, or (as normal for servlets) a single empty string if the
    parameter was supplied without a value. */
  def unapply(req: HttpServletRequest) = {
    val names = JEnumerationIterator[String](req.getParameterNames.asInstanceOf[java.util.Enumeration[String]])
    Some(((Map.empty[String, Seq[String]] /: names) ((m, n) => 
        m + (n -> req.getParameterValues(n))
      )).withDefaultValue(Nil), req)
  }
}

abstract class NamedParameter[T](name: String) {
  def unapply(params: Map[String, Seq[String]]) = accept(params(name)) map {
    (_, params)
  }
  def accept(values: Seq[String]): Option[T]
}

class StringParameter(name: String, f: Option[String] => Option[String]) extends NamedParameter[String](name) {
  def this(name: String) = this(name, identity[Option[String]])
  def accept(values: Seq[String]) = values.headOption
}
object T2 extends Function1[Option[String], Option[String]] {
  def apply(s: Option[String]) = s.map { _.trim }
}
object NE2 extends Function1[Option[String], Option[String]] {
  def apply(s: Option[String]) = s.filter { ! _.isEmpty }
}
object Name2 extends StringParameter("name", T2 andThen { os => os.filter { ! _.isEmpty } })
trait Trimmed extends StringParameter {
  override def accept(values: Seq[String]) = super.accept(values) map { _.trim }
}

trait NotEmpty extends StringParameter {
  override def accept(values: Seq[String]) = super.accept(values) filter { ! _.isEmpty }
}

object Name extends StringParameter("name") with Trimmed with NotEmpty
