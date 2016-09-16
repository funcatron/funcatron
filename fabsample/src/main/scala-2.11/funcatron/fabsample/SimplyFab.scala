package funcatron.fabsample

import funcatron.intf.{Context, Func}

import scala.beans.BeanProperty
import scala.collection.JavaConversions._

/**
  * Created by dpp on 9/13/16.
  */
class SimplyFab extends Func[java.util.Map[String, Object], Object] {
  override def apply(request: java.util.Map[String, Object], context: Context) = {
    val logger = context.getLogger();

    logger.error("Yes... I can log!!")

    List(Wombat("David", 4232),
      Wombat("Archer", 332) /*,
      Wombat(context.getRequestParams().get("firstname").get(0), 43) */
    ) : java.util.List[Object]
  }
}

case class Wombat(@BeanProperty name: String,@BeanProperty value: Int)