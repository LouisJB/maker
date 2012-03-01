package maker.task

import maker.utils.FileUtils._
import java.io.File
import org.apache.commons.io.{FileUtils => ApacheFileUtils}
import maker.project._

case object PublishTask extends Task {
  def exec(project: Project, acc: List[AnyRef]) = {

    val homeDir = project.props.HomeDir()
    val moduleDef = project.moduleDef
    val baseLocalRepo = file(homeDir, ".ivy2/maker-local/")
    val moduleLocal = file(baseLocalRepo, project.name)

    // paceholder, todo, implement some automation of the form:
    // $ java -Dhttp.proxyHost=host -Dhttp.proxyPort=port -Dhttp.nonProxyHosts=
    //   -jar ~/repos/maker/libs/ivy-2.2.0.jar -debug -ivy utils/maker-ivy.xml -settings maker-ivysettings.xml -publish resolvername -revision 1.2.3 -publishpattern /home/louis/.ivy2/maker-local/utils/jars/utils.jar

    Right("Not implemented yet")
  }
}
