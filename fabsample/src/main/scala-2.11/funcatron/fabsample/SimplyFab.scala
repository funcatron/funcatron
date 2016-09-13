package funcatron.fabsample

import funcatron.intf.{Context, Func}

import scala.beans.BeanProperty
import scala.collection.JavaConversions._

/**
  * Created by dpp on 9/13/16.
  */
class SimplyFab extends Func[Map[String, Object], Object] {
  override def apply(request: Map[String, Object], context: Context) = {
    List(Wombat("David", 422),
      Wombat("Archer", 332) /*,
      Wombat(context.getRequestParams().get("firstname").get(0), 43) */
    ) : java.util.List[Object]
  }
}

case class Wombat(@BeanProperty val name: String,@BeanProperty val value: Int)