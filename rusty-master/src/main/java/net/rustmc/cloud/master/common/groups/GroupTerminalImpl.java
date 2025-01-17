package net.rustmc.cloud.master.common.groups;

import net.rustmc.cloud.base.common.Rust;
import net.rustmc.cloud.base.console.ICloudConsole;
import net.rustmc.cloud.base.objects.SimpleCloudGroup;
import net.rustmc.cloud.base.packets.PacketPauseCodec;
import net.rustmc.cloud.base.packets.output.transfer.PacketOutGroupTransfer;
import net.rustmc.cloud.base.util.FileHelper;
import net.rustmc.cloud.base.util.ZipHelper;
import net.rustmc.cloud.master.RustCloud;
import net.rustmc.cloud.master.configurations.CloudGroupConfiguration;
import net.rustmc.cloud.master.groups.ICloudGroup;
import net.rustmc.cloud.master.groups.IGroupTerminal;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * This class belongs to the rusty-cloud project
 *
 * @author Alexander Jilge
 * @since 21.11.2022
 */
public class GroupTerminalImpl implements IGroupTerminal {

    private final List<ICloudGroup> groups = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public void register(File file) {
        final var object = Rust.getInstance()
                .getConfigurationHandler()
                .open(file.getName().replace("json", ""), file.toURI(), CloudGroupConfiguration.class);
        this.groups.add(new SimpleCloudGroupImpl(object.cloudGroup()));
    }

    @Override
    public Collection<ICloudGroup> getUnallocatedGroups() {
        return groups.stream().filter(iCloudGroup -> iCloudGroup.getObject().getAllocatedNode().equals("null")).toList();
    }

    @Override
    public ICloudGroup getCloudGroupByName(String name) {
        for (ICloudGroup group : this.groups) {
            if (group.getObject().getName().equals(name)) return group;
        }
        return null;
    }

    @Override
    public ICloudGroup produce(String name, boolean proxy, int maxPlayersPer, int percent, int maxServers, int memory, String allocatedNode) {
        String node = allocatedNode;
        if (node.equals("null")) {
            if (RustCloud.getCloud().getOnlineNodeTerminal().size() == 0) {
                if (RustCloud.getCloud().getOfflineNodeTerminal().getOfflineNodes().size() == 0) {
                    RustCloud.getCloud().getCloudConsole().send("no available node could be found!", ICloudConsole.Output.ERROR);
                    return null;
                } else {
                    node = RustCloud.getCloud()
                            .getOfflineNodeTerminal()
                            .getOfflineNodes()
                            .get(this.random.nextInt(RustCloud.getCloud()
                                    .getOfflineNodeTerminal()
                                    .size()))
                            .configuration()
                            .getName();
                }
            } else {
                node = RustCloud.getCloud()
                        .getOnlineNodeTerminal()
                        .getOnlineNodes()
                        .get(this.random.nextInt(RustCloud.getCloud()
                                .getOnlineNodeTerminal()
                                .size()))
                        .configuration()
                        .getName();
            }
        } else {
            if (RustCloud.getCloud()
                    .getOfflineNodeTerminal()
                    .getOfflineNodes()
                    .stream()
                    .filter(offlineNode -> offlineNode
                            .configuration()
                            .getName()
                            .equals(allocatedNode))
                    .toList()
                    .isEmpty()) {
                RustCloud.getCloud()
                        .getCloudConsole()
                        .send("The specified node could not be found!", ICloudConsole.Output.ERROR);
                return null;
            }
        }
        final var object = new SimpleCloudGroup(name, proxy, 19, maxPlayersPer, maxServers, memory, node);
        final var file = new File("groups//" + name + ".json");
        final var content = new File(object.isTemplate() ? "templates//" + object.getName() : "statics//" + object.getName());
        FileHelper.create(content);
        Rust.getInstance().getConfigurationHandler().open(name, file.toURI(), new CloudGroupConfiguration(object));
        final var out = new SimpleCloudGroupImpl(object);
        this.groups.add(out);
        this.requestTransfer(out);
        return out;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void requestTransfer(ICloudGroup group) {
        final File transfer = new File("temp//" + group.getObject().getName() + ".zip").toPath().toFile();
        ZipHelper.zipFoldersAndFiles(
                new File( (group.getObject().isTemplate() ? "templates" : "statics") + "//" + group.getObject().getName()).toPath(),
                transfer.toPath()
        );
        Rust.getInstance().getAsynchronousExecutor().schedule(() -> {
            final var node = RustCloud.getCloud().getOnlineNodeTerminal().getByName(group.getObject().getAllocatedNode());
            if (node !=  null) {
                node.dispatch(new PacketPauseCodec());
                node.dispatch(transfer);
                node.dispatch(new PacketOutGroupTransfer(group.getObject().getName(), group.getObject().isTemplate()));
                RustCloud.getCloud().getCloudConsole().send("the data from the §a" + group.getObject().getName() + " §rgroup is transmitted to §a" + group.getObject().getAllocatedNode() + "§r.");
            }
            transfer.delete();
        }, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public List<ICloudGroup> getCloudGroups() {
        return this.groups;
    }

}
