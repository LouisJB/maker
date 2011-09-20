fsc -d classes src/plugin/TemplateTraverseComponent.scala
cp scalac-plugin.xml classes/
jar cf plugin.jar -C classes .
