package net.rustmc.cloud.master;

import lombok.Getter;
import net.rustmc.cloud.api.commands.CommandManager;
import net.rustmc.cloud.api.commands.listeners.ConsoleInputListener;
import net.rustmc.cloud.api.commands.listeners.ConsoleTabListener;
import net.rustmc.cloud.base.common.Rust;
import net.rustmc.cloud.base.communicate.IChannelBootstrap;
import net.rustmc.cloud.base.communicate.ICommunicateBaseChannel;
import net.rustmc.cloud.base.console.ICloudConsole;
import net.rustmc.cloud.base.util.FileHelper;
import net.rustmc.cloud.master.commands.CloseCommand;
import net.rustmc.cloud.master.commands.ProduceCommand;
import net.rustmc.cloud.master.configurations.RustyGroupsConfiguration;
import net.rustmc.cloud.master.configurations.RustyMasterConfiguration;
import net.rustmc.cloud.master.configurations.RustyNodeConfiguration;
import net.rustmc.cloud.master.managers.SimpleGroupManager;
import net.rustmc.cloud.master.managers.SimpleNodeManager;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * This class belongs to the rusty-cloud project
 *
 * @author Alexander Jilge
 * @since 23.10.2022
 */
@SuppressWarnings("SpellCheckingInspection")
@Getter
public final class RustCloud {

    @Getter
    private static RustCloud cloud;

    private final ICloudConsole cloudConsole;
    private final IChannelBootstrap bootstrap = Rust.getInstance().getChannelFactory().bootstrap();
    private final RustyMasterConfiguration configuration = Rust.getInstance().getConfigurationHandler().open(new File("master.json").toURI(), RustyMasterConfiguration.class);
    private final RustyGroupsConfiguration groupsConfiguration = Rust.getInstance().getConfigurationHandler().open(new File("groups.json").toURI(), RustyGroupsConfiguration.class);
    private final ICommunicateBaseChannel communicateBaseChannel;
    private final CommandManager commandManager = new CommandManager();
    private final File nodeFile = new File("nodes");
    private final ArrayList<RustyNodeConfiguration> nodeConfigurations = new ArrayList<>();
    private final SimpleGroupManager groupManager = new SimpleGroupManager();
    private final SimpleNodeManager nodeManager = new SimpleNodeManager();

    public RustCloud() throws MalformedURLException, URISyntaxException {

        this.cloudConsole = Rust.getInstance().getConsoleFactory().newConsole();

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            RustCloud.this
                    .getCloudConsole()
                    .send(
                            "caught an unexpected error (§c" + e.getClass().getSimpleName() + "§r).",
                            ICloudConsole.Output.ERROR
                    );

            for (StackTraceElement element : e.getStackTrace()) {
                if (element.getModuleName() != null) {
                    this.getCloudConsole().send(
                            "at " +
                                    element.getModuleName() +
                                    "/" + element.getClass().getPackageName() +
                                    "." + element.getClassName() + "." +
                                    element.getMethodName() + "(" +
                                    element.getClass().getSimpleName() + ":" +
                                    element.getLineNumber() +
                                    ")"
                            , ICloudConsole.Output.ERROR);
                } else {
                    this.getCloudConsole().send(
                            "at " +
                                    element.getClass().getPackageName() +
                                    "." + element.getClassName() + "." +
                                    element.getMethodName() + "(" +
                                    element.getClass().getSimpleName() + ":" +
                                    element.getLineNumber() +
                                    ")"
                            , ICloudConsole.Output.ERROR);
                }
            }

        });

        this.cloudConsole.run();

        new ConsoleInputListener(this.getCommandManager());
        new ConsoleTabListener(this.getCommandManager());

        this.getCommandManager().register(new CloseCommand());
        this.getCommandManager().register(new ProduceCommand());
        this.getCloudConsole().send("loaded commands: " + this.getCommandManager().getCommands().size() + "§r.");

        FileHelper.create(nodeFile);
        for (final File tempNodeFile : Arrays
                .stream(Objects.requireNonNull(this.nodeFile.listFiles()))
                .filter(file -> file.getName().endsWith(".json"))
                .toList()) {
            final var nodeConfiguraion = Rust.getInstance().getConfigurationHandler().open(
                            tempNodeFile
                                    .getName()
                                    .replace(".json", ""),
                            tempNodeFile.toURI(),
                            RustyNodeConfiguration.class
                    );
            this.nodeConfigurations.add(nodeConfiguraion);
            this.cloudConsole.send("Waiting for node: §6" + nodeConfiguraion.getNode().getName() + " §ron: §6" + nodeConfiguraion.getNode().getHost() + "§r.");
        }

        communicateBaseChannel = this.bootstrap.port(this.configuration.getPort()).open();

    }

    public void onBoot() {

        this.getCloudConsole().send("The cloud started on port §a" + this.configuration.getPort() + "§r.");
        if (this.nodeConfigurations.isEmpty()) this.cloudConsole.send("the master could not locate a registered node!", ICloudConsole.Output.WARN);

    }

    public void onShutdown() {
        this.getCloudConsole().close();
        Rust.getInstance().getChannelFactory().close();
        Rust.getInstance().getConfigurationHandler().close();
        this.getCloudConsole().send("Successfully shutdown the cloudsystem.");
    }

    public static void boot() {
        try {
            cloud = new RustCloud();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
