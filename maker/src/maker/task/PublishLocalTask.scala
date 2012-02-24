package maker.task

import maker.project.Project
import maker.utils.FileUtils
import java.io.File
import org.apache.commons.io.{FileUtils => ApacheFileUtils}
import maker.os.Command
import maker.utils.maven._
import maker.project._
import maker.utils.ivy.IvyReader

case object PublishLocalTask extends Task{
  def exec(project : Project, acc : List[AnyRef]) = {

    val moduleDef = project.moduleDef
    val file = new File("~/.ivy2/cache/local")
    PomWriter.writePom(file, moduleDef)

    Right(Unit)
  }
}
