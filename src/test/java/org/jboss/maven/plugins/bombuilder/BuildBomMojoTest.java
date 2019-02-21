package org.jboss.maven.plugins.bombuilder;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.util.Collections;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BuildBomMojoTest {


    @Mock
    private PomDependencyVersionsTransformer versionTransformer;
    @Mock
    private BuildBomMojo.ModelWriter modelWriter;
    private BuildBomMojo mojo;

    @Before
    public void before() {
        mojo = new BuildBomMojo(modelWriter, versionTransformer);
        mojo.mavenProject = new MavenProject();
        mojo.mavenProject.getBuild().setOutputDirectory("target");
        mojo.outputFilename = "pom.xml";
    }
    
    @Test
    public void testDependencyVersionIsNotStoredInPropertiesByDefault() throws Exception {
        mojo.execute();

        verify(versionTransformer, never()).transformPomModel(any(Model.class));
    }

    @Test
    public void testDependencyVersionIsStoredInProperties() throws Exception {
        mojo.usePropertiesForVersion = true;

        mojo.execute();

        verify(versionTransformer).transformPomModel(any(Model.class));
    }

    @Test
    public void testDependencyManagementRetrieved() throws Exception {
        // given
        mojo.useDependencyManagementDependencies = true;
        mojo.useAllResolvedDependencies = false;

        mojo.mavenProject.setDependencies(Collections.singletonList(createDependency("groupId", "shouldNotBeUsed")));
        mojo.mavenProject.getModel().setDependencyManagement(new DependencyManagement());
        mojo.mavenProject.getDependencyManagement().addDependency(createDependency("groupId", "artifactId1"));
        mojo.mavenProject.getDependencyManagement().addDependency(createDependency("groupId", "artifactId2"));

        // when
        mojo.execute();

        // then
        final ArgumentCaptor<Model> modelCaptor = ArgumentCaptor.forClass(Model.class);
        verify(modelWriter).writeModel(modelCaptor.capture(), any(File.class));
        final Model model = modelCaptor.getValue();
        assertThat(model.getDependencyManagement().getDependencies().size(), equalTo(2));
        assertThat(model.getDependencyManagement().getDependencies().get(0).getArtifactId(), equalTo("artifactId1"));
        assertThat(model.getDependencyManagement().getDependencies().get(1).getArtifactId(), equalTo("artifactId2"));
    }

    @Test
    public void testDependencyRetrieved() throws Exception {
        // given
        mojo.useDependencies = true;
        mojo.useAllResolvedDependencies = false;

        mojo.mavenProject.setDependencies(Collections.singletonList(createDependency("groupId", "artifactId")));
        mojo.mavenProject.getModel().setDependencyManagement(new DependencyManagement());
        mojo.mavenProject.getDependencyManagement().addDependency(createDependency("groupId", "shouldNotBeUsed"));

        // when
        mojo.execute();

        // then
        final ArgumentCaptor<Model> modelCaptor = ArgumentCaptor.forClass(Model.class);
        verify(modelWriter).writeModel(modelCaptor.capture(), any(File.class));
        final Model model = modelCaptor.getValue();
        assertThat(model.getDependencyManagement().getDependencies().size(), equalTo(1));
        assertThat(model.getDependencyManagement().getDependencies().get(0).getArtifactId(), equalTo("artifactId"));
    }

    @Test
    public void testMatchesExcludedDependency() {
        assertArtifactMatchesExcludedDependency(true, "groupId", "artifactId", "groupId", "artifactId");
        assertArtifactMatchesExcludedDependency(true, "groupId", "artifactId", "*", "artifactId");
        assertArtifactMatchesExcludedDependency(true, "groupId", "artifactId", "groupId", "*");
        assertArtifactMatchesExcludedDependency(true, "groupId", "artifactId", "*", "*");
        assertArtifactMatchesExcludedDependency(true, "groupId", "artifactId", " * ", " * ");
        assertArtifactMatchesExcludedDependency(false, "groupId", "otherArtifactId", "groupId", null);
        assertArtifactMatchesExcludedDependency(false, "groupId", "otherArtifactId", null, "artifactId");
        assertArtifactMatchesExcludedDependency(false, "groupId", "otherArtifactId", "groupId", "artifactId");
        assertArtifactMatchesExcludedDependency(false, "otherGroupId", "artifactId", "groupId", "artifactId");
        assertArtifactMatchesExcludedDependency(false, "otherGroupId", "otherArtifactId", "groupId", "artifactId");
    }

    private void assertArtifactMatchesExcludedDependency(boolean expected, String artifactGroupId, String artifactArtifactId, String dependencyGroupId, String dependencyArtifactId) {
        Dependency artifact = createDependency(artifactGroupId, artifactArtifactId);
        DependencyExclusion exclusion = createDependencyExclusion(dependencyGroupId, dependencyArtifactId);
        BuildBomMojo mojo = new BuildBomMojo();
        assertEquals(expected, mojo.matchesExcludedDependency(artifact, exclusion));
    }

    private DependencyExclusion createDependencyExclusion(String groupId, String artifactId) {
        return new DependencyExclusion(groupId, artifactId);
    }

    private Dependency createDependency(String groupId, String artifactId) {
        final Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion("version");
        dependency.setScope("scope");
        dependency.setType("type");
        dependency.setClassifier("classifier");
        return dependency;
    }
}