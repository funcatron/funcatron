package funcatron.sample.kotlin

import com.fasterxml.jackson.databind.ObjectMapper
import funcatron.intf.MetaResponse
import funcatron.intf.impl.ContextImpl
import io.kotlintest.matchers.be
import io.kotlintest.matchers.have
import io.kotlintest.specs.WordSpec
import java.util.logging.Logger

class FuncTests : WordSpec() {
    val jackson = ObjectMapper()

    // set up Context object, ignore the return type
    val isVoid = ContextImpl.initContext(mapOf(), FuncTests::class.java.classLoader,
            Logger.getAnonymousLogger())
    init {
        "Data" should {
            "serialize" {
                jackson.writeValueAsString(Data("David", 53)) should have substring "David"
            }
        }

        "SimpleGet" should {
            val sg = SimpleGet()
            val result = sg.apply(null, ContextImpl(mapOf(), Logger.getAnonymousLogger()))
            val resJson = jackson.writeValueAsString(result)

            "serialize" {
                (resJson is String) shouldBe true
            }

            "include bools" {
                resJson.indexOf("bools") should be gte 0
            }
        }

        "PostOrDelete" should {
            val pod = PostOrDelete()

            "neither post or delete" {
                val result = pod.apply(null, ContextImpl(mapOf("parameters" to mapOf("path" to mapOf("cnt" to 42)),
                        "request-method" to "get"), Logger.getAnonymousLogger()))
                (result is MetaResponse) shouldBe true
                (result as MetaResponse).responseCode shouldBe 400
            }

            "delete" {
                val result = pod.apply(null, ContextImpl(mapOf("parameters" to mapOf("path" to mapOf("cnt" to 45)),
                        "request-method" to "delete"), Logger.getAnonymousLogger()))
                val resJson = jackson.writeValueAsString(result)

                (result is Data) shouldBe true
                (result as Data).age shouldBe 45
                (resJson is String) shouldBe true
            }

            "post" {
                val result = pod.apply(Data("David",33),
                        ContextImpl(mapOf("parameters" to mapOf("path" to mapOf("cnt" to 3)),
                                "request-method" to "post"), Logger.getAnonymousLogger()))
                val resJson = jackson.writeValueAsString(result)

                (result is List<*>) shouldBe true
                if (result is List<*>) {
                    val d = result[0]
                    (d is Data) shouldBe true
                    if (d is Data) {
                        d.age shouldBe 34
                    }
                }
                (resJson is String) shouldBe true
            }
        }
    }
}