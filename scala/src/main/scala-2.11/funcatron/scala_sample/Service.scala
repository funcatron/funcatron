package funcatron.scala_sample

import java.io.OutputStream
import java.util.function.Function

import funcatron.intf.{Context, Func, MetaResponse}
import java.util.{Date, Random, HashMap => JHash, List => JList, Map => JMap}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.collection.JavaConversions._
import scala.beans.BeanProperty
/**
  * Returns a simple Map
  */
class SimpleGet extends Func[Any] {
  def apply(o: Any, c: Context) = {
    // create the return value
    val ret = new JHash[String, Any]

    // get the optional num param
    val num = c.getMergedParams.get("num")

    // if we've got one, put it in the 'num-param' field
    if (null != num) ret.put("num-param", num)

    // populate a bunch of other values
    ret.put("query-params", c.getRequestInfo.get("query-params"))
    ret.put("time", (new Date).toString)
    ret.put("bools", true)
    ret.put("numero", (new Random).nextDouble)
    // return the map which will be turned into a JSON blob
    ret
  }
}

case class Data(name: String,age: Int)

object Jackson {
  val jackson: ObjectMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper
  }
}

/**
  * A class that handles POST or DELETE
  */
class PostOrDelete extends Func[Data] {
  def apply(data: Data, context: Context) = {
    val cnt = context.getRequestParams.get("path").get("cnt").asInstanceOf[Number]


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

  def jsonDecoder(): Function[JMap[String, AnyRef], Data] = {
    new Function[JMap[String, AnyRef], Data] {
      def apply(t: JMap[String, AnyRef]): Data = Jackson.jackson.convertValue(t, classOf[Data])
    }
  }

  def jsonEncoder(): Function[Object, Array[Byte]] =
    new Function[Object, Array[Byte]] {
      def apply(o: Object) = Jackson.jackson.writer().writeValueAsBytes(o)
    }

}
