package maker

import org.scalatest.FunSuite

class PropsTests extends FunSuite{
  test("http properties include only overrides"){
    for (overrides <- List(
      Map[String, String]("HttpProxyPort" → "1234"),
      Map[String, String]("HttpProxyHost" → "fred-the-proxy"),
      Map[String, String]("HttpProxyHost" → "fred-the-proxy", "HttpProxyPort" → "1234"),
      Map[String, String]("IvyJar" → "bar"),
      Map[String, String](),
      Map[String, String]("HttpNonProxyHosts" → "fred-not-the-proxy,mike-not-the-proxy-either")
    )){
      val props = Props(overrides)
      assert(props.httpProperties.toMap === overrides.filterKeys(_.startsWith("Http")))
    }

  }
}
