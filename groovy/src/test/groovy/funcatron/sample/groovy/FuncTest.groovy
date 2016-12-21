package funcatron.sample.groovy

import com.fasterxml.jackson.databind.ObjectMapper
import funcatron.intf.MetaResponse
import funcatron.intf.impl.ContextImpl
import spock.lang.Specification

import java.util.logging.Logger

class FuncTest extends Specification{
    static {
        ContextImpl.initContext([:], FuncTest.class.getClassLoader(), Logger.anonymousLogger)
    }

    ObjectMapper jackson = new ObjectMapper()


    def "Data JSON serializable"() {
        setup:
        Data data = new Data(name: "David", age: 33)
        when:
        def result = jackson.writeValueAsString(data)
        then:
        result instanceof String
    }

    def "Simple GET works"() {
        setup:
        def sg = new SimpleGet()
        when:
        def result = sg.apply([:], new ContextImpl([:], Logger.getAnonymousLogger()))
        def resJson = jackson.writeValueAsString(result)
        then:
        resJson instanceof String &&
                resJson.indexOf("bools") >= 0
    }

    def "Not POST/DELETE on PostOrDelete"() {
        setup:
        def pod = new PostOrDelete()
        when:
        def result = pod.apply(null, new ContextImpl([parameters: [path: [cnt: 42]],
                                                      "request-method": "get"], Logger.getAnonymousLogger()))
        then:
        result instanceof MetaResponse &&
                result.getResponseCode() == 400
    }

    def "DELETE on PostOrDelete"() {
        setup:
        def pod = new PostOrDelete()
        when:
        def result = pod.apply(null, new ContextImpl([parameters: [path: [cnt: 45]],
                                                      "request-method": "delete"], Logger.getAnonymousLogger()))
        def resJson = jackson.writeValueAsString(result)
        then:
        result instanceof Data &&
                result.age == 45 &&
                resJson instanceof String
    }

    def "POST on PostOrDelete"() {
        setup:
        def pod = new PostOrDelete()
        def data = new Data(name: "David", age: 33)

        when:
        def result = pod.apply(data, new ContextImpl([parameters: [path: [cnt: 3]],
                                                      "request-method": "post"], Logger.getAnonymousLogger()))
        def resJson = jackson.writeValueAsString(result)
        then:
        result instanceof List &&
                result.get(0) instanceof Data &&
                result.get(0).age == 34 &&
                resJson instanceof String
    }
}
