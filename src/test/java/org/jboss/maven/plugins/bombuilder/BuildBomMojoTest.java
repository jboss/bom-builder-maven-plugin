package org.jboss.maven.plugins.bombuilder;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BuildBomMojoTest {

    @Test
    public void testMatchesExcludedDependency() throws Exception {
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
        Artifact artifact = createArtifact(artifactGroupId, artifactArtifactId);
        DependencyExclusion exclusion = createDependencyExclusion(dependencyGroupId, dependencyArtifactId);
        BuildBomMojo mojo = new BuildBomMojo();
        assertEquals(expected, mojo.matchesExcludedDependency(artifact, exclusion));
    }

    private DependencyExclusion createDependencyExclusion(String groupId, String artifactId) {
        return new DependencyExclusion(groupId, artifactId);
    }

    private Artifact createArtifact(String groupId, String artifactId) {
        return new DefaultArtifact(groupId, artifactId, "version", "scope", "type", "classifier", (ArtifactHandler)null);
    }
}