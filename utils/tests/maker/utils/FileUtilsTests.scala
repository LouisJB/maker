package maker.utils
import org.scalatest.FunSuite
import java.io.File

class FileUtilsTests extends FunSuite{
  test("With temp dir"){
    var savedDir : File = null
    FileUtils.withTempDir{
      dir ⇒
        savedDir = dir
        assert(dir.exists)
    }
    assert(! savedDir.exists)
  }
}
