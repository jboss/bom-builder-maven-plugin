bom-builder-maven-plugin
========================

A Maven plugin to generate a dependency management POM, sometimes called a 
BOM or bill of materials POM.  The plugin reads the set of dependencies in 
the current project, and writes a new POM to "target/bom-pom.xml" which
contains a dependency management section listing the dependencies of
the current project.


Usage
-----
The plugin is configure in the "plugins" section of the pom.

    <plugins>
      <plugin>
        <groupId>org.jboss.maven.plugins</groupId>
        <artifactId>bom-builder-maven-plugin</artifactId>
        <version>1.0.0.Beta3</version>
        <executions>
          <execution>
            <id>build-bom</id>
            <goals>
              <goal>build-bom</goal>
            </goals>
            <configuration>
              <bomGroupId>org.jboss.bom</bomGroupId>
              <bomArtifactId>my-artifacts-bom</bomArtifactId>
              <bomVersion>1.0.0</bomVersion>
              <bomName>My Artifacts BOM</bomName>
              <bomDescription>My Artifacts BOM</bomDescription>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>


