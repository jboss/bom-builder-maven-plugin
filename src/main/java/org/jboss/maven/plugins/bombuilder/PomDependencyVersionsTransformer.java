package org.jboss.maven.plugins.bombuilder;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;

class PomDependencyVersionsTransformer {


    public Model transformPomModel(Model pomModel) {
        DependencyManagement depMgmt = pomModel.getDependencyManagement();
        Map<String, String> groupIdArtifactIdVersions = new TreeMap<>();
        Map<String, String> groupIdVersions = new TreeMap<>();
        for (Dependency dependency : depMgmt.getDependencies()) {
            groupIdArtifactIdVersions.put(dependency.getGroupId() + ":" + dependency.getArtifactId(), dependency.getVersion());
            groupIdVersions.put(dependency.getGroupId(), dependency.getVersion());
        }
        ;
        Properties properties = pomModel.getProperties();
        for(Map.Entry<String, String> groupVersion  : groupIdVersions.entrySet()) {
            properties.setProperty("version." + groupVersion.getKey(), groupVersion.getValue());
        }
        for (Dependency dependency : depMgmt.getDependencies()) {
            dependency.setVersion("${version." + dependency.getGroupId() + "}");
        }
        return pomModel;
    }

}
