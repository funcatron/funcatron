package funcatron.sample.kotlin

import java.util.*
import java.util.logging.Level
import funcatron.intf.Func
import funcatron.intf.Context
import funcatron.intf.MetaResponse

/**
 * The data class
 */

class Data(val name: String, val age: Int) {
    override fun toString(): String = "{"+name+", "+age+"}"
}

/**
 * Returns a simple Map
 */
class SimpleGet : Func<Any> {


    override fun apply(o: Any, c: Context): Any {

        c.logger.info("In Simple Get...")

        // get the optional num param
        val num = c.mergedParams["num"]

        val m1 = if (null != num) mapOf("num-param" to num) else mapOf()

        // populate a bunch of other values
        val ret =
                mapOf("query-params" to c.requestInfo["query-params"],
                        "time" to Date().toString(),
                        "bools" to true,
                        "numero" to Random().nextDouble()) + m1

        c.logger.log(Level.INFO, "Returning", ret)

        // return the map which will be turned into a JSON blob
        return ret
    }

}


/**
 * A class that handles POST or DELETE
 */
class PostOrDelete : Func<Data> {

    override fun apply(data: Data, context: Context): Any {
        val cnt = context.requestParams["path"]?.get("cnt") as Number

        return when (context.method) {
            "delete" -> Data("Deleted " + cnt.toLong(), cnt.toInt())
            "post" -> (1..cnt.toInt()).map {
                i ->
                Data(data.name + i, data.age + i)
            }
            else -> object : MetaResponse {

                override fun getResponseCode(): Int = 400


                override fun getContentType(): String = "text/plain"

                override fun getBody(): ByteArray =
                        ("Expecting a POST or DELETE, but got " +
                                context.method).toByteArray()
            }
        }


    }

}
