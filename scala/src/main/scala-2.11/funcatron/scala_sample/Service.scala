package funcatron.scala_sample

import java.io.OutputStream

import funcatron.intf.{Context, Func, MetaResponse}
import java.util.{Date, Random, HashMap => JHash, List => JList, Map => JMap}

import scala.collection.JavaConversions._
import scala.beans.BeanProperty
/**
  * Returns a simple Map
  */
class SimpleGet extends Func[Any, JMap[String, Any]] {
  def apply(o: Any, c: Context): JMap[String, Any] = {
    // create the return value
    val ret = new JHash[String, Any]
    // get the optional num param
    val num = c.getRequestParams.get("params").get("num")
    // if we've got one, put it in the 'num-param' field
    if (null != num) ret.put("num-param", num)
    // populate a bunch of other values
    ret.put("query-params", c.getRequestParams.get("query"))
    ret.put("time", (new Date).toString)
    ret.put("bools", true)
    ret.put("numero", (new Random).nextDouble)
    // return the map which will be turned into a JSON blob
    ret
  }
}

case class Data(@BeanProperty name: String,@BeanProperty age: Int)


/**
  * A class that handles POST or DELETE
  */
class PostOrDelete extends Func[Data, Any] {
  def apply(data: Data, context: Context): Any = {
    val cnt = context.getRequestParams.get("path").get("cnt").asInstanceOf[Number]


    if ("delete" == context.getMethod) new Data("Deleted " + cnt.longValue, cnt.intValue)
    else if ("post" == context.getMethod) {

      (1 to cnt.intValue()).
        map(i => new Data(data.getName + i, data.getAge + i)).
        toList: JList[Data]

    }
    else new MetaResponse() {
      def getResponseCode = 400

      def getHeaders = new JHash()

      def isLargeBody = false

      def getBody = ("Expecting a POST or DELETE, but got " + context.getMethod).getBytes("UTF-8")

      def writeBody(outputStream: OutputStream) {
      }
    }
  }
}
