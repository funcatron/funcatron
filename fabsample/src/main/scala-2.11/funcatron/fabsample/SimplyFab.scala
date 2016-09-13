package funcatron.fabsample

import funcatron.intf.{Context, Func}

/**
  * Created by dpp on 9/13/16.
  */
class SimplyFab extends Func[Map[String, Object], Object] {
  override def apply(request: Map[String, Object], context: Context) = {
    List(Wombat("David", 42),
      Wombat("Archer", 33),
      Wombat(context.getRequestParams().get("firstname").get(0), 43))
  }
}

case class Wombat(name: String, value: Int)