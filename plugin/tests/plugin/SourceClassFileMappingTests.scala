package plugin

import org.scalatest.FunSuite
import maker.utils.FileUtils
import java.io.File
import utils.SourceClassFileMapping

class SourceClassFileMappingTests extends FunSuite{
  test("persisting can round trip"){
    FileUtils.withTempFile{
      f =>
        val s = SourceClassFileMapping(f)
        s +=(new File("foo.scala"), new File("foo.class"))
        s ++=(new File("bar.scala"), Set(new File("bar.class"), new File("bar$.class")))
        s.persist()
        val t = SourceClassFileMapping(f)
        assert(s === t)
    }
  }
}
