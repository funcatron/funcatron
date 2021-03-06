package funcatron.scala_sample

import java.io.{InputStream, OutputStream}
import java.util.function.Function
import java.util.logging.Level

import funcatron.intf.{Context, Func, MetaResponse}
import java.util.{Date, Random, Map => JMap}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.reflect.ClassTag

/**
  * Returns a simple Map
  */
class SimpleGet extends Func[AnyRef] with DecoderOMatic[AnyRef] {
  def apply(o: AnyRef, c: Context): AnyRef = {
    c.getLogger.log(Level.INFO, "In Scala 'SimpleGet... yay!")

    // get the optional num param
    val num = c.getMergedParams.get("num")

    // if we've got one, put it in the 'num-param' field
    val base = if (null != num) Map(("num-param", num)) else Map()

    base ++ List(("query-params", c.getRequestInfo.get("query-params")),
      ("time", (new Date).toString),
      ("bools", true),
      ("numero", (new Random).nextDouble))
    
  }

  protected def ct: Class[AnyRef] = classOf[AnyRef]
}

case class Data(name: String,age: Int)

object DecoderOMatic {
  val jackson: ObjectMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper
  }
}

/**
  * Encode/decode the Scala types correctly
  */
trait DecoderOMatic[T] {

  protected def ct: Class[T]

  def jsonDecoder(): Function[InputStream, T] = {
    new Function[InputStream, T] {
      def apply(t: InputStream): T = DecoderOMatic.jackson.readValue(t, ct)
    }
  }

  def jsonEncoder(): Function[Object, Array[Byte]] =
    new Function[Object, Array[Byte]] {
      def apply(o: Object) = DecoderOMatic.jackson.writer().writeValueAsBytes(o)
    }

}

/**
  * A class that handles POST or DELETE
  */
class PostOrDelete extends Func[Data] with DecoderOMatic[Data] {
  def apply(data: Data, context: Context) = {
    // we're guaranteed the 'cnt' path variable by the Swagger definition
    val cnt = context.getPathParams.get("cnt").asInstanceOf[Number]

    context.getMethod match {
      case "delete" =>
        new Data("Deleted " + cnt.longValue, cnt.intValue)

      case "post" =>
        (1 to cnt.intValue()).
          map(i => new Data(data.name + i, data.age + i)).
          toList

      case _ =>
        new MetaResponse() {
          def getResponseCode = 400

          override def getContentType = "text/plain"

          def getBody = ("Expecting a POST or DELETE, but got " + context.getMethod).getBytes("UTF-8")
        }
    }
  }

  protected def ct: Class[Data] = classOf[Data]
}
