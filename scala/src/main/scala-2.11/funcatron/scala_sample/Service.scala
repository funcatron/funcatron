package funcatron.scala_sample

import java.io.OutputStream
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

  def jsonDecoder(): Function[JMap[String, AnyRef], T] = {
    new Function[JMap[String, AnyRef], T] {
      def apply(t: JMap[String, AnyRef]): T = DecoderOMatic.jackson.convertValue(t, ct)
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
    val cnt = context.getRequestParams.get("path").get("cnt").asInstanceOf[Number]

    context.getLogger.log(Level.INFO, "Our function and data: ",
      Array(context.getMethod, data).asInstanceOf[Array[Object]])

    if ("delete" == context.getMethod) new Data("Deleted " + cnt.longValue, cnt.intValue)
    else if ("post" == context.getMethod) {

      (1 to cnt.intValue()).
        map(i => new Data(data.name + i, data.age + i)).
        toList

    }
    else new MetaResponse() {
      def getResponseCode = 400

      override def getContentType = "text/plain"

      def getBody = ("Expecting a POST or DELETE, but got " + context.getMethod).getBytes("UTF-8")
    }
  }

  protected def ct: Class[Data] = classOf[Data]
}
