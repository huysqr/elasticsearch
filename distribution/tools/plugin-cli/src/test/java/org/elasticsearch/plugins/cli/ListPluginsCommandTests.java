/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.plugins.cli;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.elasticsearch.Version;
import org.elasticsearch.cli.Command;
import org.elasticsearch.cli.CommandTestCase;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.TestEnvironment;
import org.elasticsearch.plugins.PluginInfo;
import org.elasticsearch.plugins.PluginTestUtil;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@LuceneTestCase.SuppressFileSystems("*")
public class ListPluginsCommandTests extends CommandTestCase {

    private Path home;
    private Environment env;

    @Before
    public void initEnv() throws Exception {
        home = createTempDir();
        Files.createDirectories(home.resolve("plugins"));
        Settings settings = Settings.builder().put("path.home", home).build();
        env = TestEnvironment.newEnvironment(settings);
    }

    private static String buildMultiline(String... args) {
        return Arrays.stream(args).collect(Collectors.joining("\n", "", "\n"));
    }

    private static void buildFakePlugin(final Environment env, final String description, final String name, final String classname)
        throws IOException {
        buildFakePlugin(env, description, name, classname, false);
    }

    private static void buildFakePlugin(
        final Environment env,
        final String description,
        final String name,
        final String classname,
        final boolean hasNativeController
    ) throws IOException {
        PluginTestUtil.writePluginProperties(
            env.pluginsFile().resolve(name),
            "description",
            description,
            "name",
            name,
            "version",
            "1.0",
            "elasticsearch.version",
            Version.CURRENT.toString(),
            "java.version",
            "1.8",
            "classname",
            classname,
            "has.native.controller",
            Boolean.toString(hasNativeController)
        );
    }

    public void testPluginsDirMissing() throws Exception {
        Files.delete(env.pluginsFile());
        IOException e = expectThrows(IOException.class, () -> execute());
        assertEquals("Plugins directory missing: " + env.pluginsFile(), e.getMessage());
    }

    public void testNoPlugins() throws Exception {
        execute();
        assertTrue(terminal.getOutput(), terminal.getOutput().isEmpty());
    }

    public void testOnePlugin() throws Exception {
        buildFakePlugin(env, "fake desc", "fake", "org.fake");
        execute();
        assertEquals(buildMultiline("fake"), terminal.getOutput());
    }

    public void testTwoPlugins() throws Exception {
        buildFakePlugin(env, "fake desc", "fake1", "org.fake");
        buildFakePlugin(env, "fake desc 2", "fake2", "org.fake");
        execute();
        assertEquals(buildMultiline("fake1", "fake2"), terminal.getOutput());
    }

    public void testPluginWithVerbose() throws Exception {
        buildFakePlugin(env, "fake desc", "fake_plugin", "org.fake");
        execute("-v");
        assertEquals(
            buildMultiline(
                "Plugins directory: " + env.pluginsFile(),
                "fake_plugin",
                "- Plugin information:",
                "Name: fake_plugin",
                "Description: fake desc",
                "Version: 1.0",
                "Elasticsearch Version: " + Version.CURRENT.toString(),
                "Java Version: 1.8",
                "Native Controller: false",
                "Licensed: false",
                "Type: isolated",
                "Extended Plugins: []",
                " * Classname: org.fake"
            ),
            terminal.getOutput()
        );
    }

    public void testPluginWithNativeController() throws Exception {
        buildFakePlugin(env, "fake desc 1", "fake_plugin1", "org.fake", true);
        execute("-v");
        assertEquals(
            buildMultiline(
                "Plugins directory: " + env.pluginsFile(),
                "fake_plugin1",
                "- Plugin information:",
                "Name: fake_plugin1",
                "Description: fake desc 1",
                "Version: 1.0",
                "Elasticsearch Version: " + Version.CURRENT.toString(),
                "Java Version: 1.8",
                "Native Controller: true",
                "Licensed: false",
                "Type: isolated",
                "Extended Plugins: []",
                " * Classname: org.fake"
            ),
            terminal.getOutput()
        );
    }

    public void testPluginWithVerboseMultiplePlugins() throws Exception {
        buildFakePlugin(env, "fake desc 1", "fake_plugin1", "org.fake");
        buildFakePlugin(env, "fake desc 2", "fake_plugin2", "org.fake2");
        execute("-v");
        assertEquals(
            buildMultiline(
                "Plugins directory: " + env.pluginsFile(),
                "fake_plugin1",
                "- Plugin information:",
                "Name: fake_plugin1",
                "Description: fake desc 1",
                "Version: 1.0",
                "Elasticsearch Version: " + Version.CURRENT.toString(),
                "Java Version: 1.8",
                "Native Controller: false",
                "Licensed: false",
                "Type: isolated",
                "Extended Plugins: []",
                " * Classname: org.fake",
                "fake_plugin2",
                "- Plugin information:",
                "Name: fake_plugin2",
                "Description: fake desc 2",
                "Version: 1.0",
                "Elasticsearch Version: " + Version.CURRENT.toString(),
                "Java Version: 1.8",
                "Native Controller: false",
                "Licensed: false",
                "Type: isolated",
                "Extended Plugins: []",
                " * Classname: org.fake2"
            ),
            terminal.getOutput()
        );
    }

    public void testPluginWithoutVerboseMultiplePlugins() throws Exception {
        buildFakePlugin(env, "fake desc 1", "fake_plugin1", "org.fake");
        buildFakePlugin(env, "fake desc 2", "fake_plugin2", "org.fake2");
        execute();
        assertEquals(buildMultiline("fake_plugin1", "fake_plugin2"), terminal.getOutput());
    }

    public void testPluginWithoutDescriptorFile() throws Exception {
        final Path pluginDir = env.pluginsFile().resolve("fake1");
        Files.createDirectories(pluginDir);
        NoSuchFileException e = expectThrows(NoSuchFileException.class, () -> execute());
        assertEquals(pluginDir.resolve(PluginInfo.ES_PLUGIN_PROPERTIES).toString(), e.getFile());
    }

    public void testPluginWithWrongDescriptorFile() throws Exception {
        final Path pluginDir = env.pluginsFile().resolve("fake1");
        PluginTestUtil.writePluginProperties(pluginDir, "description", "fake desc");
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> execute());
        final Path descriptorPath = pluginDir.resolve(PluginInfo.ES_PLUGIN_PROPERTIES);
        assertEquals("property [name] is missing in [" + descriptorPath.toString() + "]", e.getMessage());
    }

    public void testExistingIncompatiblePlugin() throws Exception {
        PluginTestUtil.writePluginProperties(
            env.pluginsFile().resolve("fake_plugin1"),
            "description",
            "fake desc 1",
            "name",
            "fake_plugin1",
            "version",
            "1.0",
            "elasticsearch.version",
            Version.fromString("1.0.0").toString(),
            "java.version",
            System.getProperty("java.specification.version"),
            "classname",
            "org.fake1"
        );
        buildFakePlugin(env, "fake desc 2", "fake_plugin2", "org.fake2");

        execute();
        String message = "plugin [fake_plugin1] was built for Elasticsearch version 1.0.0 but version " + Version.CURRENT + " is required";
        assertEquals("""
            fake_plugin1
            fake_plugin2
            """, terminal.getOutput());
        assertEquals("WARNING: " + message + "\n", terminal.getErrorOutput());

        terminal.reset();
        execute("-s");
        assertEquals("""
            fake_plugin1
            fake_plugin2
            """, terminal.getOutput());
    }

    @Override
    protected Command newCommand() {
        return new ListPluginsCommand() {
            @Override
            protected Environment createEnv(Map<String, String> settings) {
                return env;
            }

            @Override
            protected boolean addShutdownHook() {
                return false;
            }
        };
    }
}
