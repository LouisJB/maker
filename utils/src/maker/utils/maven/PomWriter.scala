package maker.utils.maven

import maker.utils._
import java.io.File
import scala.xml._
import maker.utils._

case class MavenRepository(id : String, name : String, url : String, layout : String)
case class ProjectDef(descripton : String, moduleLibDef : DependencyLib, repos : List[MavenRepository])
case class ModuleDef(projectDef : ProjectDef, dependencies : List[DependencyLib], repositories : List[MavenRepository])

object PomWriter {

/*
  def writePom(file : File, moduleDef, ModuleDef) {

    val pomOuter =
        <?xml version='1.0' encoding='UTF-8'?>
        <project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.  org/POM/4.0.0">
            <groupId>todo</groupId>
            <artifactId>todo</artifactId>
            <packaging>jar</packaging>
            <description>todo</description>
            <version>todo</version>
            <name>projectDef.moduleLibDef.name</name>
            <organization>
            <name>todo</name>
            </organization>
            mkDependencies(moduleDef.dependencies)
            mkRepostitories(moduleDef.repositories)
        </repositories>
       </project>
  }

  private def mkDependencies(dependencies : List[Dependencies]) : NodeSeq =
    <dependencies>
      dependencies.map(d =>
        <dependency>
          <groupId>d.groupId</groupId>
          <artifactId>d.artifactId</artifactId>
          <version>d.version</version>
          <scope>comple (todo)</scope>
        </dependency>)
    </dependendencies>
  }

  private def mkRepositories(repositories : List[MavenRepositories]) : NodeSeq = {
    <repositories>
    repositories.map(r =>
      <repository>
        <id>r.id</id>
        <name>r.name</name>
        <url>r.url</url>
        <layout>r.layout</layout>
      </repository>)
    </repositories>
  }
  */
}
